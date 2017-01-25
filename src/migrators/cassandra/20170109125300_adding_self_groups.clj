(ns migrators.cassandra.20170109125300-adding-self-groups
  (:use [joplin.cassandra.database])
  (:require [qbits.alia :as alia]
            [qbits.hayt :as hayt]))

(defn up
  [db]
  (let [conn (get-connection (:hosts db) (:keyspace db))]
    (alia/execute
     conn
     (hayt/alter-table
      :groups
      (hayt/add-column :group_type :text)))

    (alia/execute
     conn
     (hayt/create-table
      "groups_by_user_and_type"
      (hayt/column-definitions {:id           :uuid
                                :name         :text
                                :group_type   :text
                                :created      :timestamp
                                :created_by   :uuid
                                :primary-key  [:created_by :group_type :id]})))))

(defn down [db]
  (let [conn (get-connection (:hosts db) (:keyspace db))]
    (alia/execute
     conn
     (hayt/alter-table
      "groups"
      (hayt/drop-column :group_type)))
    (alia/execute conn (hayt/drop-table "groups_by_user_and_type"))))
