(ns kixi.heimdall.integration.repl
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async :refer [go-loop >!! chan alts! timeout]]
            [kixi.heimdall.system :as system]
            [environ.core :refer [env]]))

(defonce system (atom nil))

(defn start
  ([]
   (start {}))
  ([overrides]
   (when-not @system
     (try
       (prn "Starting system" (keyword (env :system-profile "test")))
       (->> (system/system (keyword (env :system-profile "test")))
            (#(merge % overrides))
            component/start-system
            (reset! system))
       (catch Exception e
         (reset! system (:system (ex-data e)))
         (throw e))))))

(def wait-emit-msg (Integer/parseInt (env :wait-emit-msg "5000")))
(def total-wait-time (Integer/parseInt (env :total-wait-time "1200000")))

(defn keep-circleci-alive
  [kill-chan]
  (go-loop [cnt 0]
    (let [[_ port] (alts! [kill-chan
                           (timeout wait-emit-msg)])]
      (when (and
             (< cnt (/ total-wait-time wait-emit-msg))
             (not= kill-chan port))
        (prn "Waiting for system shutdown")
        (recur (inc cnt))))))

(defn stop
  []
  (when @system
    (prn "Stopping system")
    (let [kill-chan (chan)
          _ (keep-circleci-alive kill-chan)]
      (component/stop-system @system)
      (>!! kill-chan :die))
    (reset! system nil)))

(defn restart
  []
  (stop)
  (start))
