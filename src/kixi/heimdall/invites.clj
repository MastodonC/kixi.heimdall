(ns kixi.heimdall.invites
  (:require [taoensso.timbre :as log]
            [clojure.spec :as s]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [kixi.heimdall
             [schema :as schema]
             [util :as util]]))

(def invites-table "invites")

(defn invite-code->url
  [ic un]
  (str "/#/invite?ic=" ic "&un=" un))

(defn create-invite-event
  [username]
  (if (s/valid? ::schema/username username)
    (let [ic (util/create-code)]
      {:kixi.comms.event/key :kixi.heimdall/invite-created
       :kixi.comms.event/version "1.0.0"
       :kixi.comms.event/payload {:username username
                                  :invite-code ic
                                  :url (invite-code->url ic username)}})
    {:kixi.comms.event/key :kixi.heimdall/invite-failed
     :kixi.comms.event/version "1.0.0"
     :kixi.comms.event/payload {:error (str "The provided username was not valid: " username)}}))

(defn save!
  [db invite-code username]
  (db/put-item db
               invites-table
               {:username username
                :invite-code invite-code
                :created-at (util/db-now)}
               {:return :none}))

(defn consume!
  [db invite-code username]
  (let [item (db/get-item db
                          invites-table
                          {:username username}
                          {:consistent? true})]
    (when (= invite-code (:invite-code item))
      (nil? (db/delete-item db
                            invites-table
                            {:username username}
                            {:consistent? true})))))
