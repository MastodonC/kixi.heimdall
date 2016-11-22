(ns user

  ;; DO NOT ADD ANYTHING HERE THAT MIGHT REMOTELY HAVE A COMPILATION ERROR.
  ;; THIS IS TO ENSURE WE CAN ALWAYS GET A REPL STARTED.
  ;;
  ;; see (init) below.

  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [kixi.heimdall.application]
            [ring.adapter.jetty :as jetty]))

(defn init
  "Constructs the current development system."
  [& args]

  ;; We do some gymnastics here to make sure that the REPL can always start
  ;; even in the presence of compilation errors.
  (require '[kixi.heimdall.system])

  (let [new-system (resolve 'kixi.heimdall.system/system)
        profile (first args)]
    (alter-var-root #'kixi.heimdall.application/system
                    (constantly (new-system (or profile :development))))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'kixi.heimdall.application/system component/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'kixi.heimdall.application/system
                  (fn [s] (when s (component/stop s)))))

(defn go [& args]
  (apply init args)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))
