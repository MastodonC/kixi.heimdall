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
      "users"
      (hayt/column-definitions {:password    :text
                                :username    :text
                                :id          :uuid
                                :name        :text
                                :created     :timestamp
                                :primary-key [:username]})))))

(defn down [db]
  (let [conn (get-connection (:hosts db) (:keyspace db))]
    (alia/execute conn (hayt/drop-table "users"))))
