(ns kixi.heimdall.integration.event-constraints-test
  (:require [clojure.core.async :as async]
            [clojure.test :refer :all]
            [kixi.comms :as kcomms]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.integration.base :refer :all]
            [kixi.heimdall.invites :as invites]
            [kixi.heimdall.kaylee :as k]
            [kixi.heimdall.member :as member]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.user :as user]))

(use-fixtures :once cycle-system extract-db-session extract-comms)

(defn uid
  []
  (str (java.util.UUID/randomUUID)))

(defn user-groups
  [user]
  (member/retrieve-groups-ids @db-session (:id user)))

(defn group-users
  [group]
  (member/retrieve-member-ids @db-session (:id group)))

(defn wait-for-user-invite
  [username]
  (wait-for #(db/get-item @db-session
                          invites/invites-table
                          {:username username}
                          {:consistent? true})
            #(throw (Exception. "Invite code never arrived."))))

(defn create-users-in-a-shared-group
  [username1 username2 groupname]
  (let [name1 (str username1 "-" (uid))
        username1 (str name1 "@mastodonc.com")
        name2 (str username2 "-" (uid))
        username2 (str name2 "@mastodonc.com")
        groupname (str "groupname" (uid))]
    (with-redefs [k/db    (fn [] @db-session)
                  k/comms (fn [] @comms)]
      (prn "Invite user1: " username1 ". Result: " (k/invite-user! username1 name1 [groupname]))
      (prn "Invite user2: " username2 ". Result: " (k/invite-user! username2 name2 [groupname]))
      (let [user1-ic (wait-for-user-invite username1)
            user2-ic (wait-for-user-invite username2)]
        (is user1-ic)
        (is user2-ic)
        (when (and user1-ic user2-ic)
          (service/signup-user! @db-session @comms {:username username1
                                                    :name name1
                                                    :password "Foobar123"
                                                    :invite-code (:invite-code user1-ic)})
          (service/signup-user! @db-session @comms {:username username2
                                                    :name name2
                                                    :password "Foobar123"
                                                    :invite-code (:invite-code user2-ic)}))
        [username1 username2 groupname]))))

(defn get-entities
  [username1 username2 groupname]
  [(user/find-by-username @db-session {:username username1})
   (user/find-by-username @db-session {:username username2})
   (group/find-by-name @db-session groupname)])

(defn check-group-assignment
  ([user1 user2 group]
   (check-group-assignment
    user1 user2 group 2))
  ([user1 user2 group expected-count]
   (is (= expected-count
          (count (user-groups user1))))
   (is (= expected-count
          (count (user-groups user2))))
   (is (= expected-count
          (count (group-users group))))
   [user1 user2 group]))

(defn attach-event-listener
  [group-id]
  (let [event-chan (async/chan 100)]
    [(kcomms/attach-event-with-key-handler! @comms
                                             group-id
                                             :kixi.comms.event/id
                                             (fn [msg]
                                               (async/>!! event-chan msg)
                                               nil))
     event-chan]))

(defn detach-listener
  [handler]
  (kcomms/detach-handler! @comms handler))

(defn wait-for-dynamo-consistency
  []
  (Thread/sleep 3000))

(defn collect-events
  [event-chan events-atom]
  (loop [[msg _] (async/alts!! [event-chan
                                (async/timeout 1000)])]
    (when msg
      (swap! events-atom conj msg)
      (recur (async/alts!! [event-chan
                            (async/timeout 1000)])))))

(defn resend-events
  [events]
  (doseq [event (sort-by :kixi.comms.event/created-at 
                         (fn [^String x ^String y] 
                           (.compareTo x y))
                         events)]
    (kcomms/send-event! @comms
                        (:kixi.comms.event/key event)
                        (:kixi.comms.event/version event)
                        (:kixi.comms.event/payload event)
                        {})))

(deftest events-are-idempotent
  (let [[event-handler event-chan] (attach-event-listener "idempotent-test")
        [username1 username2 groupname] (create-users-in-a-shared-group "idempotent-test-1"
                                                                        "idempotent-test-2"
                                                                        "idempotent-group")
        [user1 user2 group] (get-entities username1 username2 groupname)]
    (check-group-assignment user1 user2 group)
    (let [msg-set (atom #{})]
      (collect-events event-chan msg-set)
      (is (= 6 
             (count @msg-set)))
      (detach-listener event-handler)
      (resend-events @msg-set)
      (wait-for-dynamo-consistency)
      (check-group-assignment user1 user2 group))))

(defn delete-entries
  [user1 user2 group]
  (db/delete-item @db-session
                  member/members-table                  
                  {:user-id (:id user1)
                   :group-id (:id group)}
                  {:return :none})
  (db/delete-item @db-session
                  member/members-table                  
                  {:user-id (:id user2)
                   :group-id (:id group)}
                  {:return :none})
  (db/delete-item @db-session
                  member/members-table                  
                  {:user-id (:id user1)
                   :group-id (:group-id user1)}
                  {:return :none})
  (db/delete-item @db-session
                  member/members-table                  
                  {:user-id (:id user2)
                   :group-id (:group-id user2)}
                  {:return :none})

  (db/delete-item @db-session
                  group/groups-table
                  {:id (:id group)}
                  {:return :none})
  (db/delete-item @db-session
                  group/groups-table
                  {:id (:group-id user1)}
                  {:return :none})
  (db/delete-item @db-session
                  group/groups-table
                  {:id (:group-id user2)}
                  {:return :none})

  (db/delete-item @db-session
                  user/user-table
                  {:id (:id user1)}
                  {:return :none})
  (db/delete-item @db-session
                  user/user-table
                  {:id (:id user2)}
                  {:return :none}))

(deftest event-replay-restores-state
  (let [[event-handler event-chan] (attach-event-listener "replay-test")
        [username1 username2 groupname] (create-users-in-a-shared-group "replay-test-1"
                                                                        "replay-test-2"
                                                                        "replay-group")
        [user1 user2 group] (get-entities username1 username2 groupname)]
    (check-group-assignment user1 user2 group)
    (let [msg-set (atom #{})]
      (collect-events event-chan msg-set)
      (is (= 6 
             (count @msg-set)))
      (detach-listener event-handler)
      (delete-entries user1 user2 group)
      (wait-for-dynamo-consistency)
      (check-group-assignment user1 user2 group 0)
      (resend-events @msg-set))
    (wait-for-dynamo-consistency)
    (check-group-assignment user1 user2 group)))
