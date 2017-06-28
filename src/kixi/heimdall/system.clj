(ns kixi.heimdall.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [kixi.comms :as comms]
            [kixi.comms.components.kinesis :as kinesis]
            [kixi.comms.components.coreasync :as coreasync]
            [kixi.heimdall
             [application :as app]
             [config :as config]]
            [kixi.heimdall.components
             [database :as db]
             [jettyserver :as web]
             [metrics :as metrics]
             [persistence :as persistence]
             [commands :as commands]]
            [kixi.log :as kixi-log]
            [taoensso.timbre :as log]))

(defn system [profile]
  (let [config (config/config profile)
        _ (reset! app/profile profile)]
    (log/set-config! {:level (keyword (env :log-level (get-in config [:logging :level])))
                      :timestamp-opts kixi-log/default-timestamp-opts
                      :appenders (if (#{:prod :staging} profile)
                                   {:direct-json (kixi-log/timbre-appender-logstash)}
                                   {:println (log/println-appender)})})
    (when (get-in config [:logging :kixi-comms-verbose-logging])
      (log/info "Switching on Kixi Comms verbose logging...")
      (comms/set-verbose-logging! true))
    (log/info "System with" profile)
    (-> (component/system-map
         :metrics (metrics/map->Metrics (:metrics config))
         :web-server (web/new-http-server (config/webserver-port config) config)
         :repl-server  (Object.) ; dummy - replaced when invoked via uberjar.
         :db (db/new-session (:dynamodb config) profile)
         :communications (case (first (keys (:communications config)))
                           :kinesis (kinesis/map->Kinesis (:kinesis (:communications config)))
                           :coreasync (coreasync/map->CoreAsync (:coreasync (:communications config))))
         :persistence (persistence/->Persistence)
         :commands (commands/->Commands))
        (component/system-using
         {:web-server [:metrics :communications :db]
          :persistence [:communications :db]
          :commands [:communications :db]}))))
