(ns migrators.dynamodb.20170301145800-init
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
                     "users"
                     [:id :s]
                     {:throughput {:read 1 :write 1}
                      :gsindexes [{:name "users-by-username"
                                   :hash-keydef [:username :s]
                                   :projection :all
                                   :throughput {:read 1 :write 1}}]
                      :block? true})

    (db/create-table conn
                     "groups"
                     [:id :s]
                     {:throughput {:read 1 :write 1}
                      :gsindexes [{:name "groups-by-created-by-and-type"
                                   :hash-keydef [:created-by :s]
                                   :range-keydef [:group-type :s]
                                   :throughput {:read 1 :write 1}
                                   :projection :all}
                                  {:name "groups-by-name"
                                   :hash-keydef [:name :s]
                                   :throughput {:read 1 :write 1}
                                   :projection :all}]
                      :block? true})

    (db/create-table conn
                     "members-groups"
                     [:user-id :s]
                     {:range-keydef [:group-id :s]
                      :throughput {:read 1 :write 1}
                      :gsindexes [{:name "groups-members"
                                   :hash-keydef [:group-id :s]
                                   :range-keydef [:user-id :s]
                                   :throughput {:read 1 :write 1}}]
                      :block? true})

    (db/create-table conn
                     "refresh-tokens"
                     [:id :s]
                     {:throughput {:read 1 :write 1}
                      :gsindexes [{:name "refresh-tokens-by-user-id"
                                   :hash-keydef [:user-id :s]
                                   :range-keydef [:issued :n]
                                   :throughput {:read 1 :write 1}}]
                      :block? true})))

(defn down
  [db]
  (let [conn (db/new-session (get-db-config) @app/profile)]
    (db/delete-table conn "users")
    (db/delete-table conn "groups")
    (db/delete-table conn "members-groups")
    (db/delete-table conn "refresh-tokens")))
