(ns kixi.heimdall.group
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [taoensso.timbre :as log]))

(def groups-table "groups")
(def created-by-index "groups-by-created-by-and-type")
(def groups-by-name-index "groups-by-name")

(defn add!
  [db {:keys [name user-id group-type] :as group}]
  (let [group-id (str (java.util.UUID/randomUUID))
        group-data {:id group-id
                    :group-name name
                    :group-type (or group-type "group")
                    :created-by user-id
                    :created (str (util/db-now)) ;might need a epoch timestamp field as well if want ordering
                    }]
    (db/put-item db
                 groups-table
                 group-data
                 {:return :none})
    {:group-id group-id}))

(defn find-by-id
  [db id]
  (db/get-item db
               groups-table
               {:id id}
               {:consistent? true}))

(defn find-by-user
  ([db user-id]
   (db/query db
             groups-table
             {:created-by [:eq user-id]}
             {:index created-by-index
              :return :all-attributes}))
  ([db user-id group-type]
   (db/query db
             groups-table
             {:created-by [:eq user-id]
              :group-type [:eq group-type]}
             {:index created-by-index
              :return :all-attributes})))

(defn find-user-group
  [db user-id]
  (first (find-by-user db user-id "user")))

(defn update!
  [db group-id {:keys [name]}]
  (let [new-vals (db/update-item db
                                 groups-table
                                 {:id group-id}
                                 {:update-expr "SET #group_name = :n"
                                  :expr-attr-names {"#group_name" "group-name"}
                                  :expr-attr-vals {":n" name}
                                  :return :updated-new})]
    new-vals))

(defn all
  [db]
  (db/scan db
           groups-table))
