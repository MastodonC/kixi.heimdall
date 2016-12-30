(ns kixi.heimdall.integration.base
  (:require [kixi.heimdall.integration.repl :as repl]
            [environ.core :refer [env]]
            [qbits.alia :as alia]
            [qbits.hayt :as hayt]
            [kixi.heimdall.config :as config :refer [config]]
            [taoensso.timbre :as log :refer [debug]]))

(def system (atom nil))
(def wait-tries (Integer/parseInt (env :wait-tries "80")))
(def wait-per-try (Integer/parseInt (env :wait-per-try "100")))

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

(def cassandra-session (atom nil))

(defn extract-cassandra-session
  [all-tests]
  (reset! cassandra-session (:cassandra-session @repl/system))
  (all-tests)
  (reset! cassandra-session nil))

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
