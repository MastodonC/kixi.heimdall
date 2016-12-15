(ns kixi.heimdall.group
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [qbits.alia.uuid :as uuid]
            [taoensso.timbre :as log]))

(defn create!
  [session {:keys [name user-id] :as group}]
  (log/info "creating group" session group)
  (let [group-id (uuid/random)
        group-data {:id group-id
                    :name name
                    :created-by user-id
                    :created (util/db-now)}]
    (db/insert! session :groups group-data)
    {:group-id group-id}))

(defn find-by-id
  [session id]
  (first (db/select* session :groups {:id id}))  )
