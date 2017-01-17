(ns seeds.cassandra
  (:require [qbits.alia                 :as alia]
            [qbits.hayt                 :as hayt]
            [joplin.cassandra.database :refer [get-connection]]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.components.database :as db]))

(defn run-dev [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))
        dc (db/->DirectConnection {:session conn})
        ;; Add a test user
        test-user (user/add! dc {:username "test@mastodonc.com"
                                 :name     "Test User"
                                 :password "Secret123"})]
    ;; Add a test group
    (#'service/create-group dc
                            {:group {:group-name "Test Group"}
                             :user {:id (str (:id test-user))}})))

(defn run-staging [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))
        dc (db/->DirectConnection {:session conn})
        ;; Add a test user
        test-user (user/add! dc {:username "test@mastodonc.com"
                                 :name     "Test User"
                                 :password "Secret123"})]
    ;; Add a test group
    (#'service/create-group dc
                            {:group {:group-name "Test Group"}
                             :user {:id (str (:id test-user))}})))

(defn run-prod [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))]))
