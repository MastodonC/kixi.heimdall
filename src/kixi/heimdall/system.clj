(ns kixi.heimdall.system
  (:require [kixi.heimdall.handler :refer :all]
            [kixi.heimdall.components.database :refer :all]
            [kixi.heimdall.components.jettyserver :refer :all]
            [com.stuartsierra.component :as component]))

(defn system []
  (let [api-port 3000
        cassandra-host "localhost"]
    (component/system-map
     :jetty-server (->JettyServer #'kixi.heimdall.handler/app api-port)
     :repl-server  (Object.) ; dummy - replaced when invoked via uberjar.
     :database     (->CassandraDatabase cassandra-host))))
