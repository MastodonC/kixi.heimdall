(ns kixi.heimdall.components.commands
  (:require [clojure.spec :as spec]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [kixi.heimdall.service :as service]
            [kixi.comms :as c]))

(defrecord Commands
    []
  component/Lifecycle
  (start [{:keys [communications db] :as component}]
    (log/info "Attaching command handlers...")
    (let [command-handlers
          [(c/attach-command-handler! communications
                                      :kixi.heimdall/create-password-reset-request-group
                                      :kixi.heimdall/create-password-reset-request
                                      "1.0.0"
                                      (comp
                                       (partial service/reset-password! db communications)
                                       :kixi.comms.command/payload))]]
      (assoc component :command-handlers command-handlers)))
  (stop [{:keys [communications command-handlers] :as component}]
    (log/info "Detaching command handlers...")
    (doseq [handler command-handlers] (c/detach-handler! communications handler))
    (dissoc component :command-handlers)))
