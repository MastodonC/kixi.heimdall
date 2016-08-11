(ns kixi.heimdall.user
  (:require [kixi.heimdall.components.database :as db]
            [buddy.hashers :as hs]))

(defn add-user!
  [session user]
  (db/insert! session :users
              (update-in user [:password] #(hs/encrypt %))))
