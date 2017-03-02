(ns kixi.heimdall.system
  (:require [kixi.heimdall.handler :refer :all]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.components.jettyserver :as web]
            [kixi.heimdall.components.metrics :as metrics]
            [kixi.heimdall.components.persistence :as persistence]
            [kixi.heimdall.config :as config]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [kixi.log :as kixi-log]
            [environ.core :refer [env]]
            [kixi.comms.components.kafka :as kafka]
            [kixi.heimdall.application :as app]))

(defn system [profile]
  (let [config (config/config profile)
        _ (reset! app/profile profile)]
    (log/set-config! {:level (keyword (env :log-level (get-in config [:logging :level])))
                      :timestamp-opts kixi-log/default-timestamp-opts
                      :appenders {:direct-json (kixi-log/timbre-appender-logstash)}})
    (log/info "System with" profile)
    (-> (component/system-map
         :metrics (metrics/map->Metrics (:metrics config))
         :web-server (web/new-http-server (config/webserver-port config) config)
         :repl-server  (Object.) ; dummy - replaced when invoked via uberjar.
         :db (db/new-session (:dynamodb config) profile)
         :communications (kafka/map->Kafka (:kafka (:communications config)))
         :persistence (persistence/->Persistence))
        (component/system-using
         {:web-server [:metrics :communications :db]
          :persistence [:communications :db]}))))
