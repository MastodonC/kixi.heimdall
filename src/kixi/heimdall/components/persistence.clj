(ns kixi.heimdall.components.persistence
  (:require [clojure.spec :as spec]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [kixi.heimdall.service :as service]
            [kixi.comms :as c]))

(defrecord Persistence
    []
  component/Lifecycle
  (start [{:keys [communications db] :as component}]
    (log/info "Attaching event handlers...")
    (let [event-handlers
          [(c/attach-event-handler! communications
                                    :kixi.heimdall/persistence-group-created
                                    :kixi.heimdall/group-created
                                    "2.0.0"
                                    (comp (constantly nil) (partial #'service/create-group db) :kixi.comms.event/payload))
           (c/attach-event-handler! communications
                                    :kixi.heimdall/persistence-user-created
                                    :kixi.heimdall/user-created
                                    "2.0.0"
                                    (comp (constantly nil) (partial #'service/create-user db) :kixi.comms.event/payload))
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
                                    "2.0.0"
                                    (comp (constantly nil) (partial #'service/save-invite db) :kixi.comms.event/payload))
           (c/attach-event-handler! communications
                                    :kixi.heimdall/persistence-password-reset-request-create
                                    :kixi.heimdall/password-reset-request-created
                                    "1.0.0"
                                    (comp (constantly nil)
                                          (partial #'service/save-password-reset-request db communications)
                                          :kixi.comms.event/payload))]]
      (assoc component :event-handlers event-handlers)))
  (stop [{:keys [communications event-handlers] :as component}]
    (log/info "Detaching event handlers...")
    (doseq [handler event-handlers] (c/detach-handler! communications handler))
    (dissoc component :event-handlers)))
