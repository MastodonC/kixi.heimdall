(ns kixi.heimdall.integration.auth-token-test
  (:require [clojure.test :refer :all]
            [kixi.heimdall.integration.base :refer :all]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.invites :refer [get-invite]]
            [kixi.heimdall.user :refer [find-by-username]]
            [clj-http.client :as client]
            [environ.core :refer [env]]
            [taoensso.timbre :as log :refer [debug]]
            [clojure.spec :as spec]))

(use-fixtures :once cycle-system extract-db-session extract-comms)

(defn post-to-auth [uri params]
  (client/post uri
               {:content-type :transit+json
                :accept :transit+json
                :throw-exceptions false
                :as :transit+json
                :form-params params}))

(defn uid
  []
  (str (java.util.UUID/randomUUID)))

(deftest auth-token
  (testing "auth token contains required fields"
    (let [username (str "auth-" (uid) "@test.com")
          _ (create-user! {:username username
                           :password "Secret123"
                           :name "Bravo Charlie"})
          conf (config/config (keyword (env :system-profile "test")))
          body (:body (post-to-auth (str "http://localhost:" (config/webserver-port conf) "/create-auth-token")
                                    {:username username
                                     :password "Secret123"}))]
      (is (get-in body [:token-pair :auth-token]))
      (let [unsigned (service/unsign-token (:auth-conf conf)
                                           (get-in body [:token-pair :auth-token]))]
        (is (spec/valid? :kixi.heimdall.schema/auth-token unsigned) (pr-str unsigned)))))
  (testing "invited user can not sign in"
    (let [username (str "not-sign-in-" (uid) "@test.com")
          _ (invite-user! {:username username
                           :password "Secret123"
                           :name "Bravo Charlie"})
          conf (config/config (keyword (env :system-profile "test")))
          auth-fn (fn [pword] 
                    (:body (post-to-auth (str "http://localhost:" (config/webserver-port conf) "/create-auth-token")
                                         (merge {:username username}
                                                (when pword
                                                  {:password pword})))))]
      (is (get-in (auth-fn "Secret123")
                  [:error]))
      (is (get-in (auth-fn "")
                  [:error]))
      (is (get-in (auth-fn nil)
                  [:error]))))
  (testing "refreshed token contains required fields"
    (let [username (str "refresh-" (uid) "@test.com")
          _ (create-user! {:username username
                           :password "Secret123"
                           :name "Bravo Charlie"})
          conf (config/config (keyword (env :system-profile "test")))
          auth-body (:body (post-to-auth (str "http://localhost:" (config/webserver-port conf) "/create-auth-token")
                                         {:username username
                                          :password "Secret123"}))
          refresh-token (get-in auth-body [:token-pair :refresh-token])
          refresh-body (:body (post-to-auth (str "http://localhost:" (config/webserver-port conf) "/refresh-auth-token")
                                            {:refresh-token refresh-token}))]
      (is (get-in refresh-body [:token-pair :auth-token]))
      (let [unsigned (service/unsign-token (:auth-conf conf)
                                           (get-in refresh-body [:token-pair :auth-token]))]
        (is (spec/valid? :kixi.heimdall.schema/auth-token unsigned)))))  )
