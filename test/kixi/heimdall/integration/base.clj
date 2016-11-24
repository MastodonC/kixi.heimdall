(ns kixi.heimdall.integration.base
  (:require [user :as repl]))

(def system (atom nil))

(defn cycle-system
  [all-tests]
  (reset! system (repl/go))
  (all-tests)
  (repl/stop)
  (reset! system nil))
