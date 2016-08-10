(ns kixi.heimdall.components.database
  (:require [com.stuartsierra.component :as component]
            [qbits.alia :as alia]
            [qbits.hayt :as hayt]
            [clojure.java.io :as io]
            [joplin.repl                :as jrepl :refer [migrate]]
            [taoensso.timbre :as log]))

(defprotocol Session
  (execute [this statement]))

(defrecord Cluster [opts]
  component/Lifecycle
  (start [this]
    (assoc this :instance (alia/cluster opts)))
  (stop [this]
    (when-let [instance (:instance this)]
      (alia/shutdown instance)
      this)))

(def ClusterDefaults
  {:contact-points ["127.0.0.1"]
   :port 9042})

(defn new-cluster [opts]
  (->Cluster (merge ClusterDefaults opts)))

(defn exec
  [this x]
  (if-let [conn (get this :connection)]
    (try
      (log/debug "Executing" (hayt/->raw x))
      (alia/execute conn x)
      (catch Exception e (log/error "Failed to execute database command:" (str e))))
    (log/error "Unable to execute Cassandra comment - no connection")))

(defn replacer
  "Calls  replacement function on different types"
  [rfn x]
  (condp = (type x)
    clojure.lang.Keyword (-> x name rfn keyword)
    clojure.lang.MapEntry (update x 0 (partial replacer rfn))
    clojure.lang.PersistentArrayMap (map (partial replacer rfn) x)
    java.lang.String (rfn x)))

(defn underscore->hyphen
  "Converts underscores to hyphens"
  [x]
  (replacer #(clojure.string/replace % #"_" "-") x))

(defn hyphen->underscore
  "Convers hyphens to underscores"
  [x]
  (replacer #(clojure.string/replace % #"-" "_") x))

(defn create-workspace!
  [host keyspace replication-factor]
  (alia/execute
   (alia/connect (alia/cluster {:contact-points [host]}))
   (hayt/create-keyspace keyspace
                         (hayt/if-exists false)
                         (hayt/with {:replication
                                     {:class "SimpleStrategy"
                                      :replication_factor replication-factor}}))))

(defprotocol Database
  (drop-table!
    [this table])
  (create-table!
    [this table columns])
  (insert!
    [this table row]
    [this table row args])
  (select*
    [this table where])
  (select
    [this table what where]))

(defrecord CassandraSession [opts profile]
  Database
  (drop-table! [this table]
    (exec this (hayt/drop-table table (hayt/if-exists))))
  (create-table! [this table columns]
    (exec this (hayt/create-table table (hayt/column-definitions columns))))
  (insert! [this table row {:keys [using]}]
    (cond
      using (exec this (hayt/insert table (hayt/values row) (apply hayt/using using)))
      :else (exec this (hayt/insert table (hayt/values row)))))
  (insert! [this table row]
    (insert! this table (map hyphen->underscore row) {}))
  (select* [this table where]
    (let [result (exec this (hayt/select table (hayt/where where)))
          reformatted (map underscore->hyphen result)]
      (if (coll? result)
        (map (partial into {}) reformatted)
        reformatted)))
  (select [this table what where]
    (let [result (exec this (hayt/select table (apply hayt/columns (map hyphen->underscore what)) (hayt/where where)))
          reformatted (map underscore->hyphen result)]
      (if (coll? result)
        (map (partial into {}) reformatted)
        reformatted)))

  component/Lifecycle
  (start [component]
    (log/info "Bootstrapping Cassandra...")
    (let [{:keys [host keyspace replication-factor]} opts]
      (create-workspace! host keyspace replication-factor))
    (let [joplin-config (jrepl/load-config (io/resource "joplin.edn"))]
      (->> profile
           (migrate joplin-config)
           (with-out-str)
           (clojure.string/split-lines)
           (run! #(log/info "> JOPLIN:" %))))
    (log/info ";; Starting session")
    (assoc component :session
           (alia/connect (get-in component [:cluster :instance])
                         (:keyspace opts))))
  (stop [component]
    (log/info ";; Stopping session")
    (when-let [session (:session component)]
      (alia/shutdown session)
      component)))

(defn new-session [opts profile]
  (->CassandraSession opts profile))
