(ns kixi.heimdall.member
  (:require [kixi.heimdall.components.database :as db]))

(def members-table "members-groups")

(defn retrieve-groups-ids
  [db id]
  (map :group-id (db/query db
                           members-table
                           {:user-id [:eq id]}
                           {;;:index members-table
                            :return [:group-id :s]})))

(defn add!
  [db user-id group-id]
  {:pre [user-id group-id]}
  (db/put-item db
               members-table
               {:id (str (java.util.UUID/randomUUID))
                :user-id user-id
                :group-id group-id}
               {:return :all-old})
  )

(defn remove-member
  [db user-id group-id]
  {:pre [user-id group-id]}
  (db/delete-item db
                  members-table
                  {:user-id user-id
                   :group-id group-id}
                  {:return :none}))
