(ns kixi.heimdall.member
  (:require [kixi.heimdall.components.database :as db]))

(def members-table "members-groups")

(def group-members-dex "groups-members")

(defn retrieve-groups-ids
  [db id]
  (map :group-id (db/query db
                           members-table
                           {:user-id [:eq id]}
                           { ;;:index members-table
                            :return [:group-id :s]
                            :consistent? true})))

(defn retrieve-member-ids
  [db group-id]
  (map :user-id (db/query db
                           members-table
                           {:group-id [:eq group-id]}
                           {:index group-members-dex
                            :return [:user-id :s]})))

(defn add!
  [db {:keys [user-id group-id]}]
  {:pre [user-id group-id]}
  (db/put-item db
               members-table
               {:user-id user-id
                :group-id group-id}
               {:return :all-old
                :expr-attr-names {"#ui" "user-id"
                                  "#gi" "group-id"}
                :cond-expr "attribute_not_exists(#ui) AND attribute_not_exists(#gi)"}))

(defn remove-member
  [db {:keys [user-id group-id]}]
  {:pre [user-id group-id]}
  (db/delete-item db
                  members-table
                  {:user-id user-id
                   :group-id group-id}
                  {:return :none}))

(defn all
  [db]
  (db/scan db members-table))
