(ns kixi.heimdall.member
  (:require [kixi.heimdall.components.database :as db]
            [qbits.alia.uuid :as uuid]))

(defn retrieve-groups-ids
  [session id]
  (map :group-id (db/select session :members_by_user [:group-id] {:user-id id})))

(defn add!
  [session user-id group-id]
  {:pre [user-id group-id]}
  (db/insert! session :members_by_group
              {:id (uuid/random)
               :user-id user-id
               :group-id group-id})
  (db/insert! session :members_by_user
              {:id (uuid/random)
               :user-id user-id
               :group-id group-id}))

(defn remove-member
  [session user-id group-id]
  {:pre [user-id group-id]}
  (db/delete! session :members_by_group
              {:group-id group-id
               :user-id user-id})
  (db/delete! session :members_by_user
              {:user-id user-id
               :group-id group-id})  )
