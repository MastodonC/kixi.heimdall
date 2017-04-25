(ns kixi.heimdall.integration.invites-test
  (:require [kixi.heimdall.invites :refer :all]
            [kixi.heimdall.integration.base :refer :all]
            [clojure.test :refer :all]
            [kixi.heimdall.components.database :as db]))

(use-fixtures :once cycle-system extract-db-session)

(deftest save-test
  (let [name "save-test-foo@bar.com"
        code (create-invite-code)]
    (save! @db-session code name)
    (let [r (db/get-item
             @db-session
             invites-table
             {:username name}
             {:consistent? true})]
      (is (= #{:invite-code :username} (set (keys r)))))))

(deftest consume-test
  (let [name "consume-test-foo@bar.com"
        code (create-invite-code)]
    (save! @db-session code name)
    (is (consume! @db-session code name))
    (is (not (consume! @db-session code name)))))

(deftest no-save-test
  (let [name "no-save-test-foo@bar.com"
        code (create-invite-code)]
    (is (not (consume! @db-session code name)))))
