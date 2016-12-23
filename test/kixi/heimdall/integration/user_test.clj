(ns kixi.heimdall.integration.user-test
  (:require [clojure.test :refer :all]
            [kixi.heimdall.integration.base :refer :all]
            [kixi.heimdall.user :refer :all]))

(use-fixtures :each cycle-system extract-cassandra-session)

(deftest add-user-test
  (let [user {:username "change-password-test@mastodonc.com"
              :password "changeme"}]
    (add! @cassandra-session user)
    (is (first (auth @cassandra-session user)))))

(deftest change-password-test-success
  (let [user {:username "change-password-test1@mastodonc.com"
              :password "changeme"}
        new-pass "iamnowchanged"]
    (add! @cassandra-session user)
    (is (change-password! @cassandra-session (:username user) new-pass))
    (is (first (auth @cassandra-session (assoc user :password new-pass))))))

(deftest change-password-test-fail
  (let [user {:username "change-password-test2@mastodonc.com"}]
    (is (not (change-password! @cassandra-session (:username user) "thisshouldfail")))))
