(ns kixi.heimdall.integration.auth-token-test
  (:require [clojure.test :refer :all]
            [kixi.heimdall.integration.base :refer :all]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.config :as config]
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

(deftest auth-token
  (testing "auth token contains required fields"
    (let [username (str "boo" (rand-int 1000) "@test.com")
          _ (service/new-user @db-session @comms {:username username
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
  (testing "refreshed token contains required fields"
    (let [username (str "boo" (rand-int 1000) "@test.com")
          _ (service/new-user @db-session @comms {:username username
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
