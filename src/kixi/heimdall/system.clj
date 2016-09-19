(ns kixi.heimdall.system
  (:require [kixi.heimdall.handler :refer :all]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.components.jettyserver :as web]
            [kixi.heimdall.components.logging :as logging]
            [kixi.heimdall.components.metrics :as metrics]
            [kixi.heimdall.config :as config]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]))

(defn system [profile]
  (let [config (config/config profile)]
    (println "System with" profile)
    (-> (component/system-map
         :metrics (metrics/map->Metrics (:metrics config))
         :logging (logging/map->Log (:logging config))
         :cluster (db/new-cluster {:contact-points (-> config :cassandra-session :hosts)})
         :cassandra-session (db/new-session (:cassandra-session config) profile)
         :web-server (web/new-http-server (config/webserver-port config) (config/auth-conf config))
         :repl-server  (Object.) ; dummy - replaced when invoked via uberjar.
         )
        (component/system-using
         {:logging [:metrics]
          :web-server [:metrics :logging :cassandra-session]
          :cassandra-session [:cluster]}))))
