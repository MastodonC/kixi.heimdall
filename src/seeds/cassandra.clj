(ns seeds.cassandra
  (:require [qbits.alia                 :as alia]
            [qbits.hayt                 :as hayt]
            [joplin.cassandra.database :refer [get-connection]]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.components.database :as db]))

(defn run-dev [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))
        dc (db/->DirectConnection {:session conn})]
    ;; Add a test user
    (user/add! dc {:username "test@mastodonc.com"
                   :name     "Test User"
                   :password "Secret123"})))

(defn run-prod [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))]))
