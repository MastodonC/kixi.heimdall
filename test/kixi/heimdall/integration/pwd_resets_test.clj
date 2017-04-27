(ns kixi.heimdall.integration.pwd-resets-test
  (:require [kixi.heimdall.pwd-resets :refer :all]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.integration.base :refer :all]
            [clojure.test :refer :all]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.user :as u]
            [kixi.comms :as kcomms]))

(use-fixtures :once cycle-system extract-db-session extract-comms)

(deftest save-test
  (let [name "save-test-foo@bar.com"
        code (util/create-code)]
    (save! @db-session code {:username name :id (str (java.util.UUID/randomUUID))})
    (let [r (db/get-item
             @db-session
             resets-table
             {:username name}
             {:consistent? true})]
      (is (= #{:reset-code :username :created-at :user-id} (set (keys r)))))))

(deftest consume-test
  (let [name "consume-test-foo@bar.com"
        code (util/create-code)]
    (save! @db-session code {:username name :id (str (java.util.UUID/randomUUID))})
    (is (consume! @db-session code name))
    (is (not (consume! @db-session code name)))))

(deftest no-save-test
  (let [name "no-save-test-foo@bar.com"
        code (util/create-code)]
    (is (not (consume! @db-session code name)))))


(deftest roundtrip-test
  "Start by creating a user, then sending the reset password command,
   then receiving the code and finally performing the reset."
  (let [username "test@test.com"
        get-user-fn #(u/find-by-username @db-session {:username username})
        get-reset-code-fn #(db/get-item @db-session
                                        resets-table
                                        {:username username}
                                        {:consistent? true})]
    (service/new-user @db-session @comms {:username username :password "Foobar123"})
    (wait-for get-user-fn
              #(throw (Exception. "User never arrived."))
              10)
    (when-let [user (get-user-fn)]
      (kcomms/send-command! @comms
                            :kixi.heimdall/create-password-reset-request
                            "1.0.0"
                            nil
                            {:username username})
      (wait-for get-reset-code-fn
                #(throw (Exception. "Reset code never arrived."))
                10)
      (when-let [reset-code-item (get-reset-code-fn)]
        (let [[ok? _] (service/complete-password-reset! @db-session
                                                        @comms
                                                        username
                                                        "Secret123"
                                                        (:reset-code reset-code-item))
              [auth-ok? _] (u/auth @db-session {:username username
                                                :password "Secret123"})]
          (is ok?)
          (is auth-ok?)
          (is (not (get-reset-code-fn))))))))
