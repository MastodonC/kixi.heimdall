(ns kixi.heimdall.group
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [qbits.alia.uuid :as uuid]
            [taoensso.timbre :as log]))

(defn create!
  [session {:keys [name] :as group}]
  (log/info "creating group" session group)
  (let [group-id (uuid/random)
        group-data (assoc group
                          :id group-id
                          :name name
                          :created (util/db-now))]
    (db/insert! session :groups group-data)
    {:group-id group-id}))
