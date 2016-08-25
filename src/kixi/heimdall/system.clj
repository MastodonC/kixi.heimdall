(ns kixi.heimdall.system
  (:require [kixi.heimdall.handler :refer :all]
            [kixi.heimdall.components.database :refer :all]
            [kixi.heimdall.components.jettyserver :refer :all]
            [kixi.heimdall.config :as config]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]))

(defn system [profile]
  (let [config (config/config profile)]
    (println "System with" profile)
    (-> (component/system-map
         :cluster (new-cluster {:contact-points (-> config :cassandra-session :hosts)})
         :cassandra-session (new-session (:cassandra-session config) profile)
         :jetty-server (component/using (new-http-server (config/webserver-port config)) [:cassandra-session])
         :repl-server  (Object.) ; dummy - replaced when invoked via uberjar.
         )
        (component/system-using
         {:cassandra-session [:cluster]}))))
