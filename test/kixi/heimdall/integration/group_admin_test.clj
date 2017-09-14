(ns kixi.heimdall.integration.group-admin-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [kixi.heimdall.integration.base :refer :all]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.user :as u]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.member :as member]
            [taoensso.timbre :as log :refer [debug]]))

(use-fixtures :once cycle-system extract-db-session extract-comms)

(defn uid
  []
  (str (java.util.UUID/randomUUID)))

(defn rand-username
  ([]
   (rand-username "lemming"))
  ([prefix]
   (str prefix "-" (uid) "@bar.com")))

(deftest creating-groups
  (testing "the actual creation"
    (let [username (rand-username)
          user-id (uid)
          user (u/add! @db-session {:id user-id :username username :password "Local123" :name "anothername"})
          group-created (#'service/create-group @db-session {:group-id (uid)
                                                             :group-name (str "fantastic four " (java.util.UUID/randomUUID))
                                                             :user-id user-id
                                                             :created (util/db-now)})]
      (is (== (count (member/retrieve-groups-ids @db-session user-id)) 1))))

  (testing "creation after sending an event"
    (let [username (rand-username)
          user-id (uid)
          user (u/add! @db-session {:id user-id :username username :password "Local123" :name "booya"})
          creation-params {:group-id (uid)
                           :group-name (str "the avengers " (java.util.UUID/randomUUID))
                           :user-id user-id
                           :created (util/db-now)}
          event-ok? (service/create-group-event @db-session @comms creation-params)]
      (is (true? event-ok?))
      (wait-for #(first (member/retrieve-groups-ids @db-session user-id))
                #(is (= :group-not-created :group-created)))
      (is (== (count (member/retrieve-groups-ids @db-session user-id)) 1)))))


(deftest adding-member-to-group
  (testing "adding a member directly"
    (let [username1 (rand-username)
          username2 (rand-username)
          [_ group-id] (create-group! @db-session username1 "Specter")
          member-id (uid)
          _ (u/add! @db-session {:id member-id :username username2 :password "Local123" :name "Jane"})
          _ (#'service/add-member @db-session {:user-id (str member-id)
                                               :group-id (str group-id)})]
      (is (some #{group-id} (member/retrieve-groups-ids @db-session member-id)))))
  (testing "adding a member via an event"
    (let [username1 (rand-username)
          username2 (rand-username)
          [_ group-id] (create-group! @db-session (rand-username "boss") "Hydra")
          member-id (uid)
          _ (u/add! @db-session {:id member-id :username "new@bar.com" :password "Local123" :name "Joe"})
          event-ok? (service/add-member-event @db-session @comms (str member-id) (str group-id))]
      (is event-ok?)
      (wait-for #(some #{group-id} (member/retrieve-groups-ids @db-session member-id))
                #(is (= :user-not-added-to-group :user-added-to-group))))))

(deftest removing-member-from-group
  (testing "removing a member directly"
    (let [username1 (rand-username)
          username2 (rand-username)
          [_ group-id] (create-group! @db-session username1 "Specter")
          member-id (uid)
          _ (u/add! @db-session {:id member-id :username username2 :password "Local123" :name "blob"})
          _ (#'service/add-member @db-session {:user-id (str member-id)
                                               :group-id (str group-id)})
          _ (#'service/remove-member @db-session {:user-id (str member-id)
                                                  :group-id (str group-id)})
          ]
      (is (not-any? #{group-id} (member/retrieve-groups-ids @db-session member-id)))))
  (testing "removing a member via an event"
    (let [username1 (rand-username)
          username2 (rand-username)
          [_ group-id] (create-group! @db-session username1 "Hydra")
          member-id (uid)
          _ (u/add! @db-session {:id member-id :username username2 :password "Local123" :name "mob"})
          _ (#'service/add-member @db-session {:user-id (str member-id)
                                               :group-id (str group-id)})
          event-ok? (service/remove-member-event @db-session @comms (str member-id) (str group-id))
          ]
      (is event-ok?)
      (wait-for #(not-any? #{group-id} (member/retrieve-groups-ids @db-session member-id))
                #(is (= :member-not-removed :member-removed))))))

(deftest modify-group
  (testing "modify directly"
    (let [username (rand-username)
          [_ group-id] (create-group! @db-session username "MI6")
          _ (#'service/update-group @db-session {:group-id (str group-id) :name "MI5"})
          result (group/find-by-id @db-session group-id)]
      (is (= (:group-name result) "MI5") (pr-str result))))
  (testing "modify via an event"
    (let [username (rand-username)
          [_ group-id] (create-group! @db-session username "Broom")
          event-ok? (service/update-group-event @db-session @comms (str group-id) "RoomOnTheBroom")]
      (is event-ok?)
      (wait-for #(= (:group-name (group/find-by-id @db-session group-id)) "RoomOnTheBroom")
                #(is (= :group-not-updated :group-updated))))))

(deftest return-groups
  ;; TODO: all groups this user can search -> add groups parameter for permissions
  (testing "return all groups" ;; add 3 people with self-groups and groups
    (let [cnt (count (service/all-groups @db-session))
          _ (doseq [[username group] [[(rand-username) "planets1"]
                                      [(rand-username) "planets2"]
                                      [(rand-username) "planets3"]]]
              (create-group! @db-session username group))
          all-groups (service/all-groups @db-session)
          all-group-names (map :kixi.group/name all-groups)]
      (is (some #{"planets1"} all-group-names))
      (is (some #{"planets2"} all-group-names))
      (is (some #{"planets3"} all-group-names)))))
