(ns kixi.heimdall.integration.user-test
  (:require [clojure.test :refer :all]
            [kixi.heimdall.integration.base :refer :all]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.user :refer :all]
            [kixi.heimdall.util :as util]))

(use-fixtures :once cycle-system extract-db-session extract-comms)

(deftest add-user-test
  (let [username-wacky-case (random-email)
        user {:id (str (java.util.UUID/randomUUID))
              :username username-wacky-case
              :password "changeme"}]
    (add! @db-session user)
    (is (first (auth @db-session (update user :username clojure.string/lower-case))))))

(deftest change-password-test-success
  (let [username-wacky-case (random-email)
        user {:id (str (java.util.UUID/randomUUID))
              :username (clojure.string/lower-case username-wacky-case)
              :password "changeme"}
        new-pass "iamnowchanged"]
    (add! @db-session user)
    (is (change-password! @db-session username-wacky-case new-pass))
    (is (first (auth @db-session (assoc user
                                        :password new-pass
                                        :username username-wacky-case))))))

(deftest change-password-test-fail
  (let [user {:username (random-email)}]
    (is (not (change-password! @db-session (:username user) "thisshouldfail")))))

(deftest add-user-from-event
  (let [username (random-email)
        user {:id (str (java.util.UUID/randomUUID))
              :name "name"
              :username username
              :password "noworky"
              :created (util/db-now)
              :group-id (str (java.util.UUID/randomUUID))}]
    (service/send-user-created-event! @comms user)
    (wait-for #(find-by-username @db-session {:username (clojure.string/lower-case username)})
              #(throw (new Exception "User never arrived")))
    (is (false? (first (auth @db-session user))))))
