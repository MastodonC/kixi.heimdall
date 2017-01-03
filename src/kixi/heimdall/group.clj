(ns kixi.heimdall.group
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [qbits.alia.uuid :as uuid]
            [taoensso.timbre :as log]))

(defn add!
  [session {:keys [name user-id] :as group}]
  (let [group-id (uuid/random)
        group-data {:id group-id
                    :name name
                    :created-by user-id
                    :created (util/db-now)}]
    (db/insert! session :groups group-data)
    {:group-id group-id}))

(defn update!
  [session group-id {:keys [name]}]
  (db/update! session :groups {:name name} {:id (java.util.UUID/fromString group-id)}))

(defn find-by-id
  [session id]
  (first (db/select* session :groups {:id id}))  )

(defn all
  [session]
  (db/select-all session :groups))
