(ns migrators.dynamodb.20170511141700-fix-groups
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.application :as app]
            [taoensso.faraday :as far]))

;; "name" should be "group-name"

(defn get-db-config
  []
  (let [conf (config/config @app/profile)]
    (:dynamodb conf)))

(defn up
  [db]
  (let [conn (db/new-session (get-db-config) @app/profile)]
    @(db/update-table conn "groups" {:gsindexes {:operation :delete
                                                 :name "groups-by-name"}})
    @(db/update-table conn "groups" {:gsindexes {:operation :create
                                                 :name "groups-by-name"
                                                 :hash-keydef [:group-name :s]
                                                 :throughput {:read 10 :write 10}
                                                 :projection :all}})))

(defn down
  [db]
  (let [conn (db/new-session (get-db-config) @app/profile)]
    @(db/update-table conn "groups" {:gsindexes {:operation :delete
                                                 :name "groups-by-name"}})
    @(db/update-table conn "groups" {:gsindexes {:operation :create
                                                 :name "groups-by-name"
                                                 :hash-keydef [:name :s] ;; the old, wrong one
                                                 :throughput {:read 10 :write 10}
                                                 :projection :all}})))
