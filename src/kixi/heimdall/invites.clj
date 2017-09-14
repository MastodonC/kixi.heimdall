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

(defn failed-event
  ([username reason]
   (failed-event username reason nil))
  ([username reason explain]
   {:kixi.comms.event/key :kixi.heimdall/invite-failed
    :kixi.comms.event/version "2.0.0"
    :kixi.comms.event/payload (merge {:reason reason
                                      :username username}
                                     (when explain
                                       {:explain explain}))}))

(defn create-invite-event
  [user]
  (let [ic (util/create-code)]
    {:kixi.comms.event/key :kixi.heimdall/invite-created
     :kixi.comms.event/version "2.0.0"
     :kixi.comms.event/payload {:user user
                                :invite-code ic
                                :url (invite-code->url ic (:username user))}}))

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
