(ns kixi.heimdall.components.database
  (:require [com.stuartsierra.component :as component]
            [qbits.alia :as alia]
            [qbits.hayt :as hayt]
            [qbits.alia.codec.joda-time] ; necessary to get the codec installed.
            [taoensso.timbre :as log]))

;; (def cluster (alia/cluster {:contact-points ["localhost"]}))
;; (def session (alia/connect cluster))
(require 'qbits.alia.codec.joda-time)

(extend-protocol qbits.hayt.cql/CQLEntities
  org.joda.time.ReadableInstant
  (cql-value [x]
    (.getMillis x)))

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

(defrecord CassandraSession [opts]
  ;; Implement the Lifecycle protocol
  component/Lifecycle
  (start [component]
    (log/info ";; Starting session")
    (assoc component :session
           (alia/connect (get-in component [:cluster :instance])
                         (:keyspace opts))))
  (stop [component]
    (log/info ";; Stopping session")
    (when-let [session (:session component)]
      (alia/shutdown session)
      component)))

(defn new-session [opts]
  (->CassandraSession opts))
