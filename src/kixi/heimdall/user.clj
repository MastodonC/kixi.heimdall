(ns kixi.heimdall.user
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.schema :as schema]
            [buddy.hashers :as hs]
            [qbits.alia.uuid :as uuid]
            [schema.core :as s]
            [taoensso.timbre :as log]
            [clojure.spec :as spec]))

(defn all
  [session]
  (db/select-all session :users))

(defn validate
  [user]
  (let [validation (spec/valid? :kixi.heimdall.schema/login user)]
    (if validation
      [true nil]
      [false (if (spec/valid? :kixi.heimdall.schema/username (:username user))
               "password should have at least 8 letters, one uppercase and one lowercase letter, and one number"
               "username should have an email format")])))


;; data functions

(defn add!
  [session user]
  (let [user-id (uuid/random)
        user-data (-> user
                      (update-in [:password] #(hs/encrypt %))
                      (assoc :created (util/db-now)
                             :id user-id))]
    (db/insert! session :users_by_username
                user-data)
    (db/insert! session :users
                user-data)
    user-data))

(defn find-by-username
  [session {:keys [username]}]
  (first (db/select* session :users_by_username {:username username})))

(defn find-by-id
  [session id]
  (first (db/select* session :users {:id id})))

(defn auth
  [session {:keys [username password]}]
  (let [user (find-by-username session {:username username})
        unauthed [false {:message "Invalid username or password"}]]
    (if user
      (if (hs/check password (:password user))
        [true {:user (dissoc user :password)}]
        unauthed)
      unauthed)))

(defn change-password!
  "Change the user's password"
  [session username new-password]
  (if-let [user (find-by-username session {:username username})]
    (let [pwd (hs/encrypt new-password)]
      (db/update! session :users_by_username
                  {:password pwd}
                  {:username username})
      (db/update! session :users
                  {:password pwd}
                  {:id (:id user)})
      true)
    false))
