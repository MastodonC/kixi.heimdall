(ns kixi.heimdall.integration.kaylee-test
  (:require [kixi.heimdall.kaylee :as k]
            [kixi.heimdall.invites :as invites]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.integration.base :refer :all]
            [clojure.test :refer :all]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.service :as service]
            [kixi.comms :as kcomms]
            [kixi.heimdall.member :as member]))

(use-fixtures :once cycle-system extract-db-session extract-comms)

(defn wait-for-user-invite
  [username]
  (wait-for #(db/get-item @db-session
                          invites/invites-table
                          {:username username}
                          {:consistent? true})
            #(throw (Exception. "Invite code never arrived."))))

(defmacro with-user
  [uname username rest]
  `(do
     (with-redefs [k/db    (fn [] @db-session)
                   k/comms (fn [] @comms)]
       (k/invite-user! ~username ~uname))
     (let [r# (wait-for-user-invite ~username)]
       (is r#)
       (when r#
         (service/signup-user! @db-session @comms {:username ~username
                                                   :name ~uname
                                                   :password "Foobar123"
                                                   :invite-code (:invite-code r#)})
         ~rest))))

(deftest delete-group-test
  (let [uname (str "kaylee-test-" (java.util.UUID/randomUUID))
        groupname (str "group-name-" (java.util.UUID/randomUUID))
        username (str uname "@mastodonc.com")]
    (with-user uname username
      (with-redefs [k/db    (fn [] @db-session)
                    k/comms (fn [] @comms)]
        (let [group (k/create-group! groupname username)]
          (if (map? group)
            (let [r (wait-for #(db/get-item @db-session
                                            group/groups-table
                                            {:id (:id group)}
                                            {:consistent? true})
                              #(throw (Exception. "Group was never created.")))]
              (is (= :failed-group-still-has-members (k/delete-group! groupname username)))
              (k/remove-user-from-group! groupname username)
              (wait-for #(nil? (not-empty (member/retrieve-member-ids @db-session (:id group))))
                        #(throw (Exception. "User was never removed from group")))
              (is (= :success-group-deleted (k/delete-group! groupname username)))
              (wait-for #(nil? (group/find-by-id @db-session (:id group)))
                        #(throw (Exception. "Group was never deleted"))))
            (is false (str "Failed to create group " group))))))))

(deftest kaylee-fns
  (let [name (str "kaylee-test-" (java.util.UUID/randomUUID))
        username (str name "@mastodonc.com")
        groupname (str "group-name-" (java.util.UUID/randomUUID))
        new-pass "Barfoo321"]
    (testing "Invite user and change pass"
      (with-redefs [k/db    (fn [] @db-session)
                    k/comms (fn [] @comms)]
        (k/invite-user! username name))
      (let [r (wait-for-user-invite username)]
        (is r)
        (when r
          (service/signup-user! @db-session @comms {:username username
                                                 :name "Kaylee Invite Test"
                                                 :password "Foobar123"
                                                 :invite-code (:invite-code r)})
          (with-redefs [k/db    (fn [] @db-session)
                        k/comms (fn [] @comms)]
            (k/change-user-password! username new-pass))
          (is (first (user/auth @db-session {:username username :password new-pass}))))))
    (testing "Create group and add user"
      (with-redefs [k/db    (fn [] @db-session)
                    k/comms (fn [] @comms)]
        (is (map? (k/create-group! groupname username)))))))

(defn user-groups
  [user]
  (member/retrieve-groups-ids @db-session (:id user)))

(deftest add-remove-user-from-group
  (let [name (str "group-owner-" (java.util.UUID/randomUUID))
        username (str name "@mastodonc.com")
        unwanted-name (str "unwanted-group-member-" (java.util.UUID/randomUUID))
        unwanted-username (str unwanted-name "@mastodonc.com")
        groupname (str "remove-usergroup-name-" (java.util.UUID/randomUUID))]
    (with-redefs [k/db    (fn [] @db-session)
                  k/comms (fn [] @comms)]
      (k/invite-user! username name)
      (k/invite-user! unwanted-username unwanted-name)
      (let [owner-ic (wait-for-user-invite username)
            unwanted-ic (wait-for-user-invite unwanted-username)]
        (is owner-ic)
        (is unwanted-ic)
        (when (and owner-ic unwanted-ic)
          (service/signup-user! @db-session @comms {:username username
                                                    :name "Kaylee remove user test - owner"
                                                    :password "Foobar123"
                                                    :invite-code (:invite-code owner-ic)})
          (service/signup-user! @db-session @comms {:username unwanted-username
                                                    :name "Kaylee remove user test - unwanted"
                                                    :password "Foobar123"
                                                    :invite-code (:invite-code unwanted-ic)})
          (is (map? (k/create-group! groupname username)))

          (let [unwanted-user (user/find-by-username @db-session {:username unwanted-username})
                user (user/find-by-username @db-session {:username username})]
            (is (= 2
                   (count (user-groups user))))
            (is (= 1
                   (count (user-groups unwanted-user))))
            (k/add-user-to-group! groupname unwanted-username)
            (wait-for #(= 2
                          (count (user-groups unwanted-user)))
                      #(throw (Exception. "Unwanted user was never added to group")))
            (k/remove-user-from-group! groupname unwanted-username)
            (wait-for #(= 1
                          (count (user-groups unwanted-user)))
                      #(throw (Exception. "Unwanted user was never removed group")))
            (is (= 1
                   (count (group/find-by-user @db-session (:id unwanted-user)))))))))))

(deftest invite-user-pre-group-new-group
  (let [name (str "kaylee-test-" (java.util.UUID/randomUUID))
        username (str name "@mastodonc.com")
        groupname (str "pre-group-name-" (java.util.UUID/randomUUID))
        new-pass "Barfoo321"]
    (with-redefs [k/db    (fn [] @db-session)
                  k/comms (fn [] @comms)]
      (k/invite-user! username name [groupname]))
    (let [r (wait-for #(db/get-item @db-session
                                    invites/invites-table
                                    {:username username}
                                    {:consistent? true})
                      #(throw (Exception. "Invite code never arrived.")))]
      (is r)
      (when r
        (let [user (user/find-by-username @db-session {:username username})
              groups (group/find-by-user @db-session (:id user))]
          (is (= 2
                 (count groups)))
          (is (= groupname
                 (:group-name (first groups))))
          (is (= name
                 (:group-name (second groups)))))))))
