(ns kixi.heimdall.integration.base
  (:require [environ.core :refer [env]]
            [kixi.heimdall.integration.repl :as repl]
            [taoensso.timbre :as log]))

(def system (atom nil))
(def wait-tries (Integer/parseInt (env :wait-tries "300")))
(def wait-per-try (Integer/parseInt (env :wait-per-try "200")))

(defn cycle-system
  [all-tests]
  (repl/start)
  (try
    (all-tests)
    (finally (repl/stop))))

(def comms (atom nil))

(defn extract-comms
  [all-tests]
  (reset! comms (:communications @repl/system))
  (all-tests)
  (reset! comms nil))

(def db-session (atom nil))

(defn extract-db-session
  [all-tests]
  (reset! db-session (:db @repl/system))
  (all-tests)
  (reset! db-session nil))

(defn wait-for
  ([fn fail-fn]
   (wait-for fn fail-fn 1))
  ([fn fail-fn cnt]
   (if (< cnt wait-tries)
     (let [value (fn)]
       (if value
         value
         (do
           (log/info "waiting ... " cnt)
           (Thread/sleep wait-per-try)
           (recur fn fail-fn (inc cnt)))))
     (do
       (log/info "calling fail fn")
       (fail-fn)))))
