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
  [rc]
  (str "/#/reset/" rc))

(defn reject-reset-event
  [reason username]
  (if (s/valid? ::schema/username username)
    {:kixi.comms.event/key :kixi.heimdall/password-reset-request-rejected
     :kixi.comms.event/version "1.0.0"
     :kixi.comms.event/payload {:reason reason :username username}}
    {:kixi.comms.event/key :kixi.heimdall/password-reset-request-failed
     :kixi.comms.event/version "1.0.0"
     :kixi.comms.event/payload {:error (str "The provided username was not valid: " username)}}))

(defn create-reset-event
  [user _]
  (let [rc (util/create-code)]
    {:kixi.comms.event/key :kixi.heimdall/password-reset-request-created
     :kixi.comms.event/version "1.0.0"
     :kixi.comms.event/payload {:user (dissoc user :password)
                                :reset-code rc
                                :url (reset-code->url rc)}}))

(defn create-reset-completed-event
  [username]
  {:kixi.comms.event/key :kixi.heimdall/password-reset-completed
   :kixi.comms.event/version "1.0.0"
   :kixi.comms.event/payload {:username username}})

(defn save!
  [db reset-code user]
  (db/put-item db
               resets-table
               {:username (:username user)
                :user-id (:id user)
                :reset-code reset-code
                :created-at (util/db-now)}
               {:return :none}))

(defn consume!
  [db reset-code username]
  (let [item (db/get-item db
                          resets-table
                          {:username username}
                          {:consistent? true})]
    (when (= reset-code (:reset-code item))
      (nil? (db/delete-item db
                            resets-table
                            {:username username}
                            {:consistent? true})))))
