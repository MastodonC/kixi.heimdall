(ns migrators.cassandra.201609021113-init
  (:use [joplin.cassandra.database])
  (:require [qbits.alia :as alia]
            [qbits.hayt :as hayt]))

(defn up
  [db]
  (let [conn (get-connection (:hosts db) (:keyspace db))]
    (alia/execute
     conn
     (hayt/create-table
      "users_by_username"
      (hayt/column-definitions {:password    :text
                                :username    :text
                                :id          :uuid
                                :name        :text
                                :created     :timestamp
                                :primary-key [:username]})))
    (alia/execute
     conn
     (hayt/create-table
      "users"
      (hayt/column-definitions {:password    :text
                                :username    :text
                                :id          :uuid
                                :name        :text
                                :created     :timestamp
                                :primary-key [:id]})))
    (alia/execute
     conn
     (hayt/create-table
      "refresh_tokens"
      (hayt/column-definitions {:id             :uuid
                                :user_id        :uuid
                                :issued         :bigint
                                :refresh_token  :text
                                :valid          :boolean
                                :primary-key    [:id]})))
    (alia/execute
     conn
     (hayt/create-table
      "refresh_tokens_by_user_id_and_issued"
      (hayt/column-definitions {:id             :uuid
                                :user_id        :uuid
                                :issued         :bigint
                                :refresh_token  :text
                                :valid          :boolean
                                :primary-key    [:user_id :issued]})))

    (alia/execute
     conn
     (hayt/create-table
      "groups"
      (hayt/column-definitions {:id           :uuid
                                :name         :text
                                :created      :timestamp
                                :primary-key  [:id]})))

    ;; to get all groups a user belongs to, and their roles in it
    (alia/execute
     conn
     (hayt/create-table
      "members_by_group"
      (hayt/column-definitions {:id :uuid
                                :user_id :uuid
                                :group_id :uuid
                                :role :text
                                :primary-key [:group_id :user_id]})))
    (alia/execute
     conn
     (hayt/create-table
      "members_by_user"
      (hayt/column-definitions {:id :uuid
                                :user_id :uuid
                                :group_id :uuid
                                :role :text
                                :primary-key [:user_id]})))))

(defn down [db]
  (let [conn (get-connection (:hosts db) (:keyspace db))]
    (alia/execute conn (hayt/drop-table "users"))
    (alia/execute conn (hayt/drop-table "users_by_username"))
    (alia/execute conn (hayt/drop-table "refresh_tokens"))
    (alia/execute conn (hayt/drop-table "refresh_tokens_by_user_id_and_issued"))
    (alia/execute conn (hayt/drop-table "groups"))))
