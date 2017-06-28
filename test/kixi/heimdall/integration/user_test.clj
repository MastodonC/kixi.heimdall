(ns kixi.heimdall.integration.user-test
  (:require [clojure.test :refer :all]
            [kixi.heimdall.integration.base :refer :all]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.user :refer :all]
            [kixi.heimdall.util :as util]))

(use-fixtures :once cycle-system extract-db-session extract-comms)

(deftest add-user-test
  (let [user {:id (str (java.util.UUID/randomUUID))
              :username "change-password-test@mastodonc.com"
              :password "changeme"}]
    (add! @db-session user)
    (is (first (auth @db-session user)))))

(deftest change-password-test-success
  (let [user {:id (str (java.util.UUID/randomUUID))
              :username "change-password-test1@mastodonc.com"
              :password "changeme"}
        new-pass "iamnowchanged"]
    (add! @db-session user)
    (is (change-password! @db-session (:username user) new-pass))
    (is (first (auth @db-session (assoc user :password new-pass))))))

(deftest change-password-test-fail
  (let [user {:username "change-password-test2@mastodonc.com"}]
    (is (not (change-password! @db-session (:username user) "thisshouldfail")))))

(deftest add-user-from-event
  (let [user {:id (str (java.util.UUID/randomUUID))
              :name "name"
              :username "event-test@test.com"
              :password "noworky"
              :created (util/db-now)
              :group-id (str (java.util.UUID/randomUUID))}]
    (service/send-user-created-event! @comms user)
    (wait-for #(find-by-username @db-session {:username "event-test@test.com"})
              #(throw (new Exception "User never arrived")))
    (is (false? (first (auth @db-session user))))))
