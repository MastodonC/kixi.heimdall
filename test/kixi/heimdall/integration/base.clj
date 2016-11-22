(ns kixi.heimdall.integration.base
  (:require [user :as repl]
            [environ.core :refer [env]]
            [qbits.alia :as alia]
            [qbits.hayt :as hayt]
            [kixi.heimdall.config :as config :refer [config]]
            [taoensso.timbre :as log :refer [debug]]))

(def system (atom nil))
(def wait-tries (Integer/parseInt (env :wait-tries "10")))
(def wait-per-try (Integer/parseInt (env :wait-per-try "100")))

(defn drop-keyspace
  []
  (let [keyspace (get-in (config/config :test) [:cassandra-session :keyspace])]
    (log/debug "droppin keyspace " keyspace)
    (try (alia/execute (:cassandra-session @system) (hayt/drop-keyspace keyspace))
         (catch Exception e (log/debug "DROP KEYSPACE FAILED " e)))))

(defn cycle-system
  [all-tests]
  (reset! system (repl/go :test))
  (all-tests)
  (drop-keyspace)
  (repl/stop)
  (reset! system nil))

(defn wait-for
  ([fn]
   (wait-for fn 1))
  ([fn cnt]
   (when (< cnt wait-tries)
     (let [value (fn)]
       (if value
         value
         (do
           (log/debug "waiting ... " cnt)
           (Thread/sleep wait-per-try)
           (wait-for fn (inc cnt))))))))
