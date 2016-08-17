(ns kixi.heimdall.system
  (:require [kixi.heimdall.handler :refer :all]
            [kixi.heimdall.components.database :refer :all]
            [kixi.heimdall.components.jettyserver :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))


(defn system []
  (let [api-port 3000
        cassandra-host "localhost"
        cassandra-keyspace "heimdall"
        cassandra-replication-factor 1
        profile :development]
    (-> (component/system-map
         :cluster (new-cluster {})
         :cassandra-session (new-session {:host cassandra-host
                                          :keyspace cassandra-keyspace
                                          :replication-factor cassandra-replication-factor} profile)
         :jetty-server (component/using (new-http-server api-port) [:cassandra-session])
         :repl-server  (Object.) ; dummy - replaced when invoked via uberjar.
         )
        (component/system-using
         {:cassandra-session [:cluster]}))))
