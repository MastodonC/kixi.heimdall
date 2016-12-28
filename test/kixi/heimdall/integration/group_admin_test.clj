(ns kixi.heimdall.integration.group-admin-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [kixi.heimdall.integration.base :refer :all]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.user :as u]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.member :as member]
            [taoensso.timbre :as log :refer [debug]]))

(use-fixtures :each cycle-system extract-cassandra-session extract-comms)

(defn create-group!
  [session owner-name group-name]
  (let [_ (u/add! session {:username owner-name :password "Local123"})
        owner-id (:id (u/find-by-username session {:username owner-name}))
        group-id (:group-id (#'service/create-group session {:group {:group-name "fantastic four"} :user {:id (str owner-id)}}))]
    [owner-id group-id]))

(deftest creating-groups
  (testing "the actual creation"
    (let [_ (u/add! @cassandra-session {:username "boo@bar.com" :password "Local123"})
          user (u/find-by-username @cassandra-session {:username "boo@bar.com"})
          group-created (#'service/create-group @cassandra-session {:group {:group-name "fantastic four"} :user (update user :id str)})]
      (is (== (count (member/retrieve-groups-ids @cassandra-session (:id user))) 1))))

  (testing "creation after sending an event"
    (let [_ (u/add! @cassandra-session {:username "boo2@bar.com" :password "Local123"})
          user (u/find-by-username @cassandra-session {:username "boo2@bar.com"})
          creation-params {:group {:group-name "the avengers"} :user {:id (str (:id user))}}
          event-ok? (service/create-group-event @cassandra-session @comms creation-params)]
      (is event-ok?)
      (wait-for #(first (member/retrieve-groups-ids @cassandra-session (:id user))) #(is (= :group-not-created :group-created)))
      (is (== (count (member/retrieve-groups-ids @cassandra-session (:id user))) 1)))))


(deftest adding-member-to-group
  (testing "adding a member directly"
    (let [[_ group-id] (create-group! @cassandra-session "bob@bar.com" "Specter")
          _ (u/add! @cassandra-session {:username "aspiring@bar.com" :password "Local123"})
          member-id (:id (u/find-by-username @cassandra-session {:username "aspiring@bar.com"}))
          _ (#'service/add-member @cassandra-session {:user-id (str member-id)
                                                      :group-id (str group-id)})]
      (is (some #{group-id} (member/retrieve-groups-ids @cassandra-session member-id)))))
  (testing "adding a member via an event"
    (let [[_ group-id] (create-group! @cassandra-session "boss@bar.com" "Hydra")
          _  (u/add! @cassandra-session {:username "new@bar.com" :password "Local123"})
          member-id (:id (u/find-by-username @cassandra-session {:username "new@bar.com"}))
          event-ok? (service/add-member-event @cassandra-session @comms (str member-id) (str group-id))]
      (is event-ok?)
      (wait-for #(some #{group-id} (member/retrieve-groups-ids @cassandra-session member-id))
                #(is (= :user-not-added-to-group :user-added-to-group))))))

(deftest removing-member-from-group
  (testing "removing a member directly"
    (let [[_ group-id] (create-group! @cassandra-session "bob@bar.com" "Specter")
          _  (u/add! @cassandra-session {:username "aspiring@bar.com" :password "Local123"})
          member-id (:id (u/find-by-username @cassandra-session {:username "aspiring@bar.com"}))
          _ (#'service/add-member @cassandra-session {:user-id (str member-id)
                                                      :group-id (str group-id)})
          _ (#'service/remove-member @cassandra-session {:user-id (str member-id)
                                                         :group-id (str group-id)})
          ]
      (is (not-any? #{group-id} (member/retrieve-groups-ids @cassandra-session member-id)))))
  (testing "removing a member via an event"
    (let [[_ group-id] (create-group! @cassandra-session "boss@bar.com" "Hydra")
          _  (u/add! @cassandra-session {:username "new@bar.com" :password "Local123"})
          member-id (:id (u/find-by-username @cassandra-session {:username "new@bar.com"}))
          _ (#'service/add-member @cassandra-session {:user-id (str member-id)
                                                      :group-id (str group-id)})
          event-ok? (service/remove-member-event @cassandra-session @comms (str member-id) (str group-id))
          ]
      (is event-ok?)
      (wait-for #(not-any? #{group-id} (member/retrieve-groups-ids @cassandra-session member-id))
                #(is (= :member-not-removed :member-removed))))))

(deftest modify-group
  (testing "modify directly"
    (let [[_ group-id] (create-group! @cassandra-session "james@bond.net" "MI6")
          _ (#'service/update-group @cassandra-session {:group-id (str group-id) :name "MI5"})]
      (is (= (:name (group/find-by-id @cassandra-session group-id)) "MI5"))))
  (testing "modify via an event"
    (let [[_ group-id] (create-group! @cassandra-session "witch@broom.org" "Broom")
          event-ok? (service/update-group-event @cassandra-session @comms (str group-id) "RoomOnTheBroom")]
      (is event-ok?)
      (wait-for #(= (:name (group/find-by-id @cassandra-session group-id)) "RoomOnTheBroom")
                #(is (= :group-not-updated :group-updated))))))
