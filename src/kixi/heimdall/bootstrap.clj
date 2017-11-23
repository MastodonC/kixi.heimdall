(ns kixi.heimdall.bootstrap
  "Start up for application"
  (:require [kixi.heimdall.application]
            [kixi.heimdall.system]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [clojure.tools.cli :refer [cli]]
            [taoensso.timbre :as log]
            [clojure.tools.nrepl.server :as nrepl-server]
            [com.stuartsierra.component :as component]
            [signal.handler :refer [with-handler]]
            [kixi.heimdall.application :as app])
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
  (let [system (kixi.heimdall.system/system (:config-location opts) (:profile opts))]
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
              :default :development :parse-fn keyword]
             ["-c" "--config-location" "location of config"
              :default @app/config-location])]

    (when (:help opts)
      (println banner)
      (System/exit 0))

    (try
      (reset! kixi.heimdall.application/system
              (component/start (build-application opts)))
      (catch Throwable t (log/error "Uncaught exception: " t))) ;; just to really be sure, should be caught elsewhere

    (with-handler :term
      (log/info "SIGTERM was caught: shutting down...")
      (component/stop @kixi.heimdall.application/system))))
