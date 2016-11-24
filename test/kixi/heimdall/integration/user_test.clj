(ns kixi.heimdall.integration.user-test
  (:require [clojure.test :refer :all]
            [kixi.heimdall.integration.base :refer :all]
            [kixi.heimdall.user :refer :all]))

(use-fixtures :each cycle-system)

(deftest add-user-test
  (let [user {:username "change-password-test@mastodonc.com"
              :password "changeme"}
        session (:cassandra-session @system)]
    (add! session user)
    (is (first (auth session user)))))

(deftest change-password-test-success
  (let [user {:username "change-password-test1@mastodonc.com"
              :password "changeme"}
        new-pass "iamnowchanged"
        session (:cassandra-session @system)]
    (add! session user)
    (is (change-password! session (:username user) new-pass))
    (is (first (auth session (assoc user :password new-pass))))))

(deftest change-password-test-fail
  (let [user {:username "change-password-test2@mastodonc.com"}
        session (:cassandra-session @system)]
    (is (not (change-password! session (:username user) "thisshouldfail")))))
