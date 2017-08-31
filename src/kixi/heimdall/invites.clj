(ns kixi.heimdall.invites
  (:require [taoensso.timbre :as log]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.user :as user]
            [kixi.heimdall
             [schema :as schema]
             [util :as util]]))

(def invites-table "invites")

(defn invite-code->url
  [ic un]
  (str "/#/invite?ic=" ic "&un=" un))

(defn create-invite-failed-event
  [reason username]
  {:kixi.comms.event/key :kixi.heimdall/invite-failed
   :kixi.comms.event/version "1.0.0"
   :kixi.comms.event/payload {:reason reason
                              :username username}})

(defn create-invite-event
  [username]
  (let [ic (util/create-code)]
    {:kixi.comms.event/key :kixi.heimdall/invite-created
     :kixi.comms.event/version "1.0.0"
     :kixi.comms.event/payload {:username (clojure.string/lower-case username)
                                :invite-code ic
                                :url (invite-code->url ic username)}}))

(defn save!
  [db invite-code username']
  (let [username (clojure.string/lower-case username')]
    (db/put-item db
                 invites-table
                 {:username username
                  :invite-code invite-code
                  :created-at (util/db-now)}
                 {:return :none})))

(defn get-invite
  [db username]
  (db/get-item db
               invites-table
               {:username username}
               {:consistent? true}))

(defn consume!
  [db invite-code username']
  (let [username (clojure.string/lower-case username')
        item (get-invite db username)]
    (when (= invite-code (:invite-code item))
      (nil? (db/delete-item db
                            invites-table
                            {:username username}
                            {:consistent? true})))))
