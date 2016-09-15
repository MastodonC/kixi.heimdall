(ns kixi.heimdall.user
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [buddy.hashers :as hs]
            [qbits.alia.uuid :as uuid]
            [schema.core :as s]
            [taoensso.timbre :as log]))


;; schema

;; regex from here http://www.lispcast.com/clojure.spec-vs-schema
(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}")
(def password-regex  #"(?=.*\d.*)(?=.*[a-z].*)(?=.*[A-Z].*).{8,}")

(def Login
  {:username email-regex
   :password password-regex})

(defn validate
  [user]
  (let [validation (s/check Login user)]
    (if validation
      [false (clojure.string/join ", " (mapv (fn [[k v]]
                                               (cond
                                                 (= k :email) "username should have an email format"
                                                 (= k :password) "password should have at least 8 letters, one uppercase and one lowercase letter, and one number")) validation))]
      [true nil])))


;; data functions

(defn add!
  [session user]
  (let [user-data (-> user
                      (update-in [:password] #(hs/encrypt %))
                      (assoc :created (util/db-now)
                             :id (uuid/random)))]
    (db/insert! session :users_by_username
                user-data)
    (db/insert! session :users
                user-data)))

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
