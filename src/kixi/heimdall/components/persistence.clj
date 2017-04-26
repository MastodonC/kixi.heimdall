(ns kixi.heimdall.components.persistence
  (:require [clojure.spec :as spec]
            [com.stuartsierra.component :as component]
            [kixi.heimdall.service :as service]
            [kixi.comms :as c]))

(defrecord Persistence
    []
  component/Lifecycle
  (start [{:keys [communications db] :as component}]
    (let [event-handlers
          [(c/attach-event-handler! communications
                                    :kixi.heimdall/persistence-group-created
                                    :kixi.heimdall/group-created
                                    "1.0.0"
                                    (comp (constantly nil) (partial #'service/create-group db) :kixi.comms.event/payload))
           (c/attach-event-handler! communications
                                    :kixi.heimdall/persistence-member-added
                                    :kixi.heimdall/member-added
                                    "1.0.0"
                                    (comp (constantly nil) (partial #'service/add-member db) :kixi.comms.event/payload))
           (c/attach-event-handler! communications
                                    :kixi.heimdall/persistence-member-removed
                                    :kixi.heimdall/member-removed
                                    "1.0.0"
                                    (comp (constantly nil) (partial #'service/remove-member db) :kixi.comms.event/payload))
           (c/attach-event-handler! communications
                                    :kixi.heimdall/persistence-group-updated
                                    :kixi.heimdall/group-updated
                                    "1.0.0"
                                    (comp (constantly nil) (partial #'service/update-group db) :kixi.comms.event/payload))
           (c/attach-event-handler! communications
                                    :kixi.heimdall/persistence-invite-create
                                    :kixi.heimdall/invite-created
                                    "1.0.0"
                                    (comp (constantly nil) (partial #'service/save-invite db) :kixi.comms.event/payload))]]
      (assoc component :event-handlers event-handlers)))
  (stop [{:keys [communications event-handlers] :as component}]
    (doseq [handler event-handlers] (c/detach-handler! communications handler))
    (dissoc component :event-handlers)))
