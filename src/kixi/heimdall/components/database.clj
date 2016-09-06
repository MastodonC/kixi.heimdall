(ns kixi.heimdall.components.database
  (:require [com.stuartsierra.component :as component]
            [qbits.alia :as alia]
            [qbits.hayt :as hayt]
            [clojure.java.io :as io]
            [joplin.repl :as jrepl :refer [migrate]]
            [taoensso.timbre :as log]
            [kixi.heimdall.util :as util]))

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
  (if-let [conn (get this :session)]
    (try
      (log/debug "Executing" (hayt/->raw x))
      (alia/execute conn x)
      (catch Exception e (log/error "Failed to execute database command:" (str e))))
    (log/error "Unable to execute Cassandra comment - no connection")))

(defn create-keyspace!
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
    [this table what where])
  (update!
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
    (insert! this table (into {} (map util/hyphen->underscore row)) {}))
  (select* [this table where]
    (let [result (exec this (hayt/select table (hayt/where where)))
          reformatted (map util/underscore->hyphen result)]
      (if (coll? result)
        (map (partial into {}) reformatted)
        reformatted)))
  (select [this table what where]
    (let [result (exec this (hayt/select table (apply hayt/columns
                                                      (map util/hyphen->underscore what))
                                         (hayt/where (into {} (util/hyphen->underscore where)))))
          reformatted (map util/underscore->hyphen result)]
      (if (coll? result)
        (map (partial into {}) reformatted)
        reformatted)))
  (update! [this table what where]
    (exec this (hayt/update table (hayt/set-columns
                                   (into {} (util/underscore->hyphen what)))
                            (hayt/where where))))

  component/Lifecycle
  (start [component]
    (log/info "Bootstrapping Cassandra...")
    (let [{:keys [host keyspace replication-factor]} opts]
      (create-keyspace! host keyspace replication-factor))
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

(defn session
  []
  (component/using
   (map ->CassandraSession {})
   [:cassandra-session]))
