(ns kixi.heimdall.bootstrap
  "Start up for application"
  (:require [kixi.heimdall.application]
            [kixi.heimdall.system]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [clojure.tools.cli :refer [cli]]
            [taoensso.timbre :as log]
            [clojure.tools.nrepl.server :as nrepl-server]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defrecord ReplServer [config]
  component/Lifecycle
  (start [this]
    (println "Starting REPL server " config)
    (assoc this :repl-server
           (apply nrepl-server/start-server :handler cider-nrepl-handler :bind "0.0.0.0" (flatten (seq config)))))
  (stop [this]
    (println "Stopping REPL server with " config)
    (nrepl-server/stop-server (:repl-server this))
    (dissoc this :repl-server)))

(defn mk-repl-server [config]
  (ReplServer. config))

(defn build-application [opts]
  (let [system (kixi.heimdall.system/system (:profile opts))]
    (cond-> system
      (:repl opts)
      (assoc :repl-server (mk-repl-server {:port (:repl-port opts)})))))


(defn -main [& args]

  (let [[opts args banner]
        (cli args
             ["-R" "--repl" "Start a REPL"
              :flag true :default true]
             ["-r" "--repl-port" "REPL server listen port"
              :default 5001 :parse-fn #(Integer/valueOf %)]
             ["-p" "--profile" "config environment/profile"
              :default :dev :parse-fn keyword])]

    (when (:help opts)
      (println banner)
      (System/exit 0))

    (try
      (component/start (build-application opts))
      (catch Throwable t (log/error t))))) ;; just to really be sure, should be caught elsewhere
