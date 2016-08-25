(ns migrators.cassandra.201608101143-init
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
                                :primary-key [:id]})))
    (alia/execute
     conn
     (hayt/create-table
      "refresh_tokens_by_user_id_and_issued"
      (hayt/column-definitions {:id             :uuid
                                :user_id        :uuid
                                :issued         :bigint
                                :refresh_token  :text
                                :valid          :boolean
                                :primary-key [:user_id :issued]})))))

(defn down [db]
  (let [conn (get-connection (:hosts db) (:keyspace db))]
    (alia/execute conn (hayt/drop-table "users"))
    (alia/execute conn (hayt/drop-table "refresh_tokens"))))
