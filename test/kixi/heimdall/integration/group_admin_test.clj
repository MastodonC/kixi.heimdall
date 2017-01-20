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
            [taoensso.timbre :as log :refer [debug]]
            [qbits.alia :as alia]
            [qbits.hayt :as hayt]))

(use-fixtures :once cycle-system extract-cassandra-session extract-comms)

(defn create-group!
  [session owner-name group-name]
  (let [_ (service/new-user @cassandra-session
                            @comms
                            {:username owner-name :password "Local123" :name "randomName"})
        owner-id (:id (u/find-by-username session {:username owner-name}))
        group-id (:group-id (#'service/create-group session {:group {:group-name group-name}
                                                             :user-id (str owner-id)}))]
    [owner-id group-id]))

(defn rand-username
  []
  (str "lemming" (rand-int 1000) "@bar.com"))

(deftest creating-groups
  (testing "the actual creation"
    (let [username (rand-username)
          _ (u/add! @cassandra-session {:username username :password "Local123" :name "anothername"})
          user (u/find-by-username @cassandra-session {:username username})
          group-created (#'service/create-group @cassandra-session {:group {:group-name "fantastic four"} :user-id (str (:id user))})]
      (is (== (count (member/retrieve-groups-ids @cassandra-session (:id user))) 1))))

  (testing "creation after sending an event"
    (let [username (rand-username)
          _ (u/add! @cassandra-session {:username username :password "Local123" :name "booya"})
          user (u/find-by-username @cassandra-session {:username username})
          creation-params {:group {:group-name "the avengers"} :user-id (str (:id user))}
          event-ok? (service/create-group-event @cassandra-session @comms creation-params)]
      (is event-ok?)
      (wait-for #(first (member/retrieve-groups-ids @cassandra-session (:id user))) #(is (= :group-not-created :group-created)))
      (is (== (count (member/retrieve-groups-ids @cassandra-session (:id user))) 1)))))


(deftest adding-member-to-group
  (testing "adding a member directly"
    (let [username1 (rand-username)
          username2 (rand-username)
          [_ group-id] (create-group! @cassandra-session username1 "Specter")
          _ (u/add! @cassandra-session {:username username2 :password "Local123" :name "Jane"})
          member-id (:id (u/find-by-username @cassandra-session {:username username2}))
          _ (#'service/add-member @cassandra-session {:user-id (str member-id)
                                                      :group-id (str group-id)})]
      (is (some #{group-id} (member/retrieve-groups-ids @cassandra-session member-id)))))
  (testing "adding a member via an event"
    (let [username1 (rand-username)
          username2 (rand-username)
          [_ group-id] (create-group! @cassandra-session "boss@bar.com" "Hydra")
          _  (u/add! @cassandra-session {:username "new@bar.com" :password "Local123" :name "Joe"})
          member-id (:id (u/find-by-username @cassandra-session {:username "new@bar.com"}))
          event-ok? (service/add-member-event @cassandra-session @comms (str member-id) (str group-id))]
      (is event-ok?)
      (wait-for #(some #{group-id} (member/retrieve-groups-ids @cassandra-session member-id))
                #(is (= :user-not-added-to-group :user-added-to-group))))))

(deftest removing-member-from-group
  (testing "removing a member directly"
    (let [username1 (rand-username)
          username2 (rand-username)
          [_ group-id] (create-group! @cassandra-session username1 "Specter")
          _  (u/add! @cassandra-session {:username username2 :password "Local123" :name "blob"})
          member-id (:id (u/find-by-username @cassandra-session {:username username2}))
          _ (#'service/add-member @cassandra-session {:user-id (str member-id)
                                                      :group-id (str group-id)})
          _ (#'service/remove-member @cassandra-session {:user-id (str member-id)
                                                         :group-id (str group-id)})
          ]
      (is (not-any? #{group-id} (member/retrieve-groups-ids @cassandra-session member-id)))))
  (testing "removing a member via an event"
    (let [username1 (rand-username)
          username2 (rand-username)
          [_ group-id] (create-group! @cassandra-session username1 "Hydra")
          _  (u/add! @cassandra-session {:username username2 :password "Local123" :name "mob"})
          member-id (:id (u/find-by-username @cassandra-session {:username username2}))
          _ (#'service/add-member @cassandra-session {:user-id (str member-id)
                                                      :group-id (str group-id)})
          event-ok? (service/remove-member-event @cassandra-session @comms (str member-id) (str group-id))
          ]
      (is event-ok?)
      (wait-for #(not-any? #{group-id} (member/retrieve-groups-ids @cassandra-session member-id))
                #(is (= :member-not-removed :member-removed))))))

(deftest modify-group
  (testing "modify directly"
    (let [username (rand-username)
          [_ group-id] (create-group! @cassandra-session username "MI6")
          _ (#'service/update-group @cassandra-session {:group-id (str group-id) :name "MI5"})]
      (is (= (:name (group/find-by-id @cassandra-session group-id)) "MI5"))))
  (testing "modify via an event"
    (let [username (rand-username)
          [_ group-id] (create-group! @cassandra-session username "Broom")
          event-ok? (service/update-group-event @cassandra-session @comms (str group-id) "RoomOnTheBroom")]
      (is event-ok?)
      (wait-for #(= (:name (group/find-by-id @cassandra-session group-id)) "RoomOnTheBroom")
                #(is (= :group-not-updated :group-updated))))))

(deftest return-groups
  ;; TODO: all groups this user can search -> add groups parameter for permissions
  (testing "return all groups" ;; add 3 people with self-groups and groups
    (let [cnt (count (service/all-groups @cassandra-session))
          _ (doseq [[username group] [[(rand-username) "planets1"]
                                      [(rand-username) "planets2"]
                                      [(rand-username) "planets3"]]]
              (create-group! @cassandra-session username group))
          all-groups (service/all-groups @cassandra-session)
          all-group-names (map :name all-groups)]
      (is (some #{"planets1"} all-group-names))
      (is (some #{"planets2"} all-group-names))
      (is (some #{"planets3"} all-group-names)))))
