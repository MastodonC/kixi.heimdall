(ns migrators.dynamodb.20170427151300-password-resets
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.application :as app]
            [taoensso.faraday :as far]
            [taoensso.timbre :as log]))

(defn get-db-config
  []
  (let [conf (config/config @app/profile)]
    (:dynamodb conf)))

(defn up
  [db]
  (let [conn (db/new-session (get-db-config) @app/profile)]
    (db/create-table conn
                     "password-resets"
                     [:username :s]
                     {:throughput {:read 1 :write 1}
                      :block? true})))

(defn down
  [db]
  (let [conn (db/new-session (get-db-config) @app/profile)]
    (db/delete-table conn "password-resets")))
