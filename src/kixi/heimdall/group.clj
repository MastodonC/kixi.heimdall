(ns kixi.heimdall.group
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [qbits.alia.uuid :as uuid]
            [taoensso.timbre :as log]))

(defn add!
  [session {:keys [name user-id group-type] :as group}]
  (let [group-id (uuid/random)
        group-data {:id group-id
                    :name name
                    :group-type (or group-type "group")
                    :created-by user-id
                    :created (util/db-now)}]
    (db/insert! session :groups group-data)
    (db/insert! session :groups_by_user_and_type group-data)
    {:group-id group-id}))

(defn find-by-id
  [session id]
  (first (db/select* session :groups {:id id})))

(defn find-by-user
  ([session user-id]
   (first (db/select* session :groups_by_user_and_type {:created-by user-id})))
  ([session user-id group-type]
   (first (db/select* session :groups_by_user_and_type {:created-by user-id
                                                        :group-type group-type}))))

(defn find-user-group
  [session user-id]
  (find-by-user user-id "user"))

(defn update!
  [session group-id {:keys [name]}]
  (db/update! session :groups {:name name} {:id (java.util.UUID/fromString group-id)})
  (let [group (find-by-id session (java.util.UUID/fromString group-id))]
    (db/update! session :groups_by_user_and_type {:name name} {:created-by (:created-by group) :group-type (:group-type group)})))

(defn all
  [session]
  (db/select-all session :groups))
