(ns kixi.heimdall.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [kixi.comms.components.kinesis :as kinesis]
            [kixi.heimdall
             [application :as app]
             [config :as config]]
            [kixi.heimdall.components
             [database :as db]
             [jettyserver :as web]
             [metrics :as metrics]
             [persistence :as persistence]]
            [kixi.log :as kixi-log]
            [taoensso.timbre :as log]))

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
         :communications (kinesis/map->Kinesis (:kinesis (:communications config)))
         :persistence (persistence/->Persistence))
        (component/system-using
         {:web-server [:metrics :communications :db]
          :persistence [:communications :db]}))))
