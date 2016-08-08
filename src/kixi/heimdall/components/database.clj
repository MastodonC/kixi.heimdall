(ns kixi.heimdall.components.database
  (:require [com.stuartsierra.component :as component]
            [qbits.alia :as alia]
            [taoensso.timbre :as log]))

;; (def cluster (alia/cluster {:contact-points ["localhost"]}))
;; (def session (alia/connect cluster))

(defrecord CassandraDatabase [host]
  ;; Implement the Lifecycle protocol
  component/Lifecycle
  (start [component]
    (log/info ";; Starting database")
    (let [cluster (alia/cluster {:contact-points [host]})
          new-session          (alia/connect cluster)]
      ;; Return an updated version of the component with
      ;; the run-time state assoc'd in.
      (assoc component :session new-session)))

  (stop [component]
    (log/info ";; Stopping database")
    ;; In the 'stop' method, shut down the running
    ;; component and release any external resources it has
    ;; acquired.
    (alia/shutdown (:session component))
    ;; Return the component, optionally modified. Remember that if you
    ;; dissoc one of a record's base fields, you get a plain map.
    (assoc component :session nil)))
