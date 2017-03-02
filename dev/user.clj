(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [kixi.heimdall.application :as app]
            [kixi.heimdall.system :as system]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
            [joplin.repl :as jrepl]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]))

(defn seed [conf env & args]
  (reset! app/profile (keyword env))
  (apply (partial jrepl/seed (jrepl/load-config (io/resource conf)) (keyword env)) args))

(defn start
  ([]
   (start {}))
  ([overrides]
   (when-not @app/system
     (try
       (prn "Starting system")
       (->> (system/system (keyword (env :system-profile "development")))
            (#(merge % overrides))
            component/start-system
            (reset! app/system))
       (catch Exception e
         (reset! app/system (:system (ex-data e)))
         (throw e))))))

(defn stop
  []
  (when @app/system
    (prn "Stopping system")
    (component/stop-system @app/system)
    (reset! app/system nil)))

(defn reset []
  (stop)
  (start))
