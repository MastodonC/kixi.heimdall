(ns kixi.heimdall.group
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [qbits.alia.uuid :as uuid]))

(defn create!
  [session {:keys [owner_id] :as group}]
  (let [group-id (uuid/random)
        group-data (assoc group
                          :id group-id
                          :created (util/db-now)
                          :admins_ids #{owner_id}
                          :users_ids #{owner_id})]
    (db/insert! session :groups group-data)
    {:group-id group-id}))
