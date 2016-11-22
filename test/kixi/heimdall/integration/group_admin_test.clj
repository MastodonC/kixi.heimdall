(ns kixi.heimdall.integration.group-admin-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [kixi.heimdall.integration.base :refer :all]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.user :as u]
            [kixi.heimdall.member :as member]
            [taoensso.timbre :as log :refer [debug]]))

(use-fixtures :each cycle-system)

(deftest creating-groups

  (testing "the actual creation"
    (let [cassandra-session (:cassandra-session @system)
          _ (u/add! cassandra-session {:username "boo@bar.com" :password "Local123"})                       user (u/find-by-username cassandra-session {:username "boo@bar.com"})
          group-created (service/create-group cassandra-session {:group {:group-name "fantastic four"} :user (update user :id str)})]
      (is (not (nil? (get-in group-created [:kixi.comms.event/payload :group-id]))))))

  (testing "creation after sending an event"
    (let [cassandra-session (:cassandra-session @system)
          comms (:communications @system)
          _ (u/add! cassandra-session {:username "boo2@bar.com" :password "Local123"})
          user (u/find-by-username cassandra-session {:username "boo2@bar.com"})
          creation-params {:group {:group-name "the avengers"} :user (update user :id str)}
          event-ok? (service/create-group-event cassandra-session comms creation-params)]
      (is event-ok?)
      (wait-for #(first (member/retrieve-groups-ids cassandra-session (:id user))))
      (is (== (count (member/retrieve-groups-ids cassandra-session (:id user))) 1)))))
