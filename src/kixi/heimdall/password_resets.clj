(ns kixi.heimdall.password-resets
  (:require [taoensso.timbre :as log]
            [clojure.spec :as s]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [kixi.heimdall
             [schema :as schema]
             [util :as util]]))

(def resets-table "password-resets")

(defn reset-code->url
  [rc un]
  (str "/#/reset?rc=" rc "&un=" un))

(defn reject-reset-event
  [reason username']
  (let [username (clojure.string/lower-case username')]
    (if (s/valid? ::schema/username username)
      {:kixi.comms.event/key :kixi.heimdall/password-reset-request-rejected
       :kixi.comms.event/version "1.0.0"
       :kixi.comms.event/payload {:reason reason :username username}}
      {:kixi.comms.event/key :kixi.heimdall/password-reset-request-failed
       :kixi.comms.event/version "1.0.0"
       :kixi.comms.event/payload {:error (str "The provided username was not valid: " username)}})))

(defn create-reset-event
  [user _]
  (let [rc (util/create-code)]
    {:kixi.comms.event/key :kixi.heimdall/password-reset-request-created
     :kixi.comms.event/version "1.0.0"
     :kixi.comms.event/payload {:user (-> user
                                          (dissoc :password)
                                          (update :username clojure.string/lower-case))
                                :reset-code rc
                                :url (reset-code->url rc (:username user))}}))

(defn create-reset-completed-event
  [username']
  (let [username (clojure.string/lower-case username')]
    {:kixi.comms.event/key :kixi.heimdall/password-reset-completed
     :kixi.comms.event/version "1.0.0"
     :kixi.comms.event/payload {:username username}}))

(defn save!
  [db reset-code user]
  (let [username (clojure.string/lower-case (:username user))]
    (db/put-item db
                 resets-table
                 {:username username
                  :user-id (:id user)
                  :reset-code reset-code
                  :created-at (util/db-now)}
                 {:return :none})))

(defn consume!
  [db reset-code username']
  (let [username (clojure.string/lower-case username')]
    (let [item (db/get-item db
                            resets-table
                            {:username username}
                            {:consistent? true})]
      (when (= reset-code (:reset-code item))
        (nil? (db/delete-item db
                              resets-table
                              {:username username}
                              {:consistent? true}))))))
