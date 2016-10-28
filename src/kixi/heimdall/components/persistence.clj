(ns kixi.heimdall.components.persistence
  (:require [clojure.spec :as spec]
            [com.stuartsierra.component :as component]
            [kixi.heimdall.service :as service]
            [kixi.comms :as c]))

(defrecord Persistence
    []
  component/Lifecycle
  (start [{:keys [communications cassandra-session] :as component}]
    (c/attach-event-handler! communications
                             :kixi.heimdall/persistence
                             :kixi.heimdall/group-created
                             "1.0.0"
                             (comp (partial service/create-group cassandra-session) :kixi.comms.event/payload))
    component)
  (stop [component]
    component))
