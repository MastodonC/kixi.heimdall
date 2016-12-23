(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [kixi.heimdall.application]
            [kixi.heimdall.system :as system]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]))

(defn start
  ([]
   (start {}))
  ([overrides]
   (when-not @kixi.heimdall.application/system
     (try
       (prn "Starting system")
       (->> (system/system (keyword (env :system-profile "development")))
            (#(merge % overrides))
            component/start-system
            (reset! kixi.heimdall.application/system))
       (catch Exception e
         (reset! kixi.heimdall.application/system (:system (ex-data e)))
         (throw e))))))

(defn stop
  []
  (when @kixi.heimdall.application/system
    (prn "Stopping system")
    (component/stop-system @kixi.heimdall.application/system)
    (reset! kixi.heimdall.application/system nil)))

(defn reset []
  (stop)
  (start))
