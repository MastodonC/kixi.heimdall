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

(deftest change-password-test
  (let [user {:username "change-password-test@mastodonc.com"
              :password "changeme"}
        new-pass "iamnowchanged"
        session (:cassandra-session @system)]
    (add! session user)
    (change-password! session (:username user) new-pass)
    (is (first (auth session (assoc user :password new-pass))))))
