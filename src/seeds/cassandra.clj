(ns seeds.cassandra
  (:require [qbits.alia                 :as alia]
            [qbits.hayt                 :as hayt]
            [joplin.cassandra.database :refer [get-connection]]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.member :as member]
            [kixi.heimdall.components.database :as db]))

(defn run-dev [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))
        dc (db/->DirectConnection {:session conn})
        ;; Add a test user
        test-user (user/add! dc {:username "test@mastodonc.com"
                                 :name     "Test User"
                                 :password "Secret123"})
        ;; Add a test group
        test-group (group/create! dc {:name "Test Group"
                                      :user-id (:id test-user)})
        ;; Add test user to test group
        _ (member/add-user-to-group dc (:id test-user) (:group-id test-group))]))

(defn run-prod [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))]))
