(ns kixi.heimdall.integration.kaylee-test
  (:require [kixi.heimdall.kaylee :as k]
            [kixi.heimdall.invites :as invites]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.integration.base :refer :all]
            [clojure.test :refer :all]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.service :as service]
            [kixi.comms :as kcomms]))

(use-fixtures :once cycle-system extract-db-session extract-comms)

(deftest kaylee-fns
  (let [username (str "kaylee-test-" (java.util.UUID/randomUUID) "@mastodonc.com")
        extra-username (str "kaylee-test-" (java.util.UUID/randomUUID) "@mastodonc.com")
        groupname (str "group-name-" (java.util.UUID/randomUUID))
        new-pass "Barfoo321"]
    (testing "Invite user and change pass"
      (with-redefs [k/db    (fn [] @db-session)
                    k/comms (fn [] @comms)]
        (k/invite-user! username)
        (k/invite-user! extra-username))
      (let [r (wait-for #(db/get-item @db-session
                                      invites/invites-table
                                      {:username username}
                                      {:consistent? true})
                        #(throw (Exception. "Invite code never arrived.")))]
        (is r)
        (when r
          (service/new-user-with-invite @db-session @comms {:username username
                                                            :name "Kaylee Invite Test"
                                                            :password "Foobar123"
                                                            :invite-code (:invite-code r)})
          (service/new-user-with-invite @db-session @comms {:username extra-username
                                                            :name "Kaylee Invite Test Extra"
                                                            :password "Foobar123"
                                                            :invite-code (:invite-code r)})
          (with-redefs [k/db    (fn [] @db-session)
                        k/comms (fn [] @comms)]
            (k/change-user-password! username new-pass)
            (k/change-user-password! extra-username new-pass))
          (is (first (user/auth @db-session {:username username :password new-pass}))))))
    (testing "Create group and add user"
      (with-redefs [k/db    (fn [] @db-session)
                    k/comms (fn [] @comms)]
        (is (map? (k/create-group! groupname username)))))
    (testing "Add user to group"
      (with-redefs [k/db    (fn [] @db-session)
                    k/comms (fn [] @comms)]
        (is (not (keyword? (k/add-user-to-group! groupname username))))))
    (testing "Remove user from group"
      (with-redefs [k/db    (fn [] @db-session)
                    k/comms (fn [] @comms)]
        (is (not (keyword? (k/remove-user-from-group! groupname username))))))))
