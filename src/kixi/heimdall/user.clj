(ns kixi.heimdall.user
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [buddy.hashers :as hs]
            [qbits.alia.uuid :as uuid]))

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

(defn retrieve-groups-ids
  [session id]
  (db/select session :users :groups_ids {:id id}))

(defn add-group-id
  [session user-id group-id]
  (let [user (find-by-id session user-id)
        user-groups (conj (:groups-ids user) group-id)
        username (:username user)]
    (db/update! session :users
                {:groups_ids user-groups}
                {:id user-id})
    (db/update! session :users_by_username
                {:groups_ids user-groups}
                {:username username})))
