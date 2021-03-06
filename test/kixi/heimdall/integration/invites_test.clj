(ns kixi.heimdall.integration.invites-test
  (:require [kixi.heimdall.invites :refer :all]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.integration.base :refer :all]
            [clojure.test :refer :all]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.service :as service]
            [kixi.comms :as kcomms]))

(use-fixtures :once cycle-system extract-db-session extract-comms)

(deftest save-test
  (let [name (random-email)
        code (util/create-code)]
    (save! @db-session code name)
    (let [r (db/get-item
             @db-session
             invites-table
             {:username (clojure.string/lower-case name)}
             {:consistent? true})]
      (is (= #{:invite-code :username :created-at} (set (keys r)))))))

(deftest consume-test
  (let [name (random-email)
        code (util/create-code)]
    (save! @db-session code name)
    (is (consume! @db-session code name))
    (is (not (consume! @db-session code name)))))

(deftest no-save-test
  (let [name (random-email)
        code (util/create-code)]
    (is (not (consume! @db-session code name)))))

(deftest roundtrip-test
  "Invite a user and then have them sign up"
  (let [username (random-email)
        get-invite-code-fn #(db/get-item @db-session
                                         invites-table
                                         {:username (clojure.string/lower-case username)}
                                         {:consistent? true})]
    (service/invite-user! @db-session @comms {:username username :name "Invite Test"})
    (wait-for get-invite-code-fn
              #(throw (Exception. "Invite code never arrived.")))
    (when-let [invite-code-item (get-invite-code-fn)]
      (let [[username-fail? username-res]
            (service/signup-user! @db-session @comms {:username "wrong-email@foo.com"
                                                      :name "Invite Test"
                                                      :password "Foobar123"
                                                      :invite-code (:invite-code invite-code-item)})
            [ic-fail? ic-res]
            (service/signup-user! @db-session @comms {:username username
                                                      :name "Invite Test"
                                                      :password "Foobar123"
                                                      :invite-code "123456789"})
            [ok? res] (service/signup-user! @db-session @comms {:username username
                                                                :name "Invite Test"
                                                                :password "Foobar123"
                                                                :invite-code (:invite-code invite-code-item)})]
        (is (not username-fail?))
        (is (not ic-fail?))
        (is ok? (str res))))))
