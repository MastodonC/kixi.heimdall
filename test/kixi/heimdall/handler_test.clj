(ns kixi.heimdall.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.handler :refer :all]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.refresh-token :as rt]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.member :as member]
            [buddy.hashers :as hs]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [qbits.alia.uuid :as uuid]
            [buddy.sign.util :as sign]
            [clj-time.core :as t]
            [kixi.heimdall.config :as config]))

(defn json-request
  [request]
  (mock/content-type request "application/json"))

(def auth-config
  (config/auth-conf (config/config :test)))

(defn auth-config-added
  [request]
  (assoc request :auth-conf auth-config))

(defn heimdall-request
  [request]
  (auth-config-added (json-request request)))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (= (:body response) "Hello World"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))



(deftest test-authentication
  (testing "auth route"
    (testing "authentication succeeds"
      (with-redefs [user/find-by-username (fn [session m]
                                            {:username "user"
                                             :password (hs/encrypt "foo")
                                             :id (uuid/random)})
                    rt/add! (fn [session m] true)
                    member/retrieve-groups-ids (fn [session user-id]
                                                 '({:group-id "group-id-1"}
                                                   {:group-id "group-id-2"}))]
        (let [response (app (heimdall-request
                             (mock/request :post "/create-auth-token"
                                           (json/write-str {:username "user" :password "foo"}))))]
          (is (= (:status response) 201))
          (let [body-resp (json/read-str (:body response) :key-fn keyword)]
            (is (:token-pair body-resp))))))
    (testing "authentication fails wrong user"
      (with-redefs [user/find-by-username (fn [session m] nil)
                    member/retrieve-groups-ids (fn [session user-id]
                                                 '())]
        (let [response (app (heimdall-request
                             (mock/request :post "/create-auth-token"
                                           (json/write-str {:username "user" :password "foo"}))))]
          (println "Response" response)
          (is (= (:status response) 401)))))
    (testing "authentication fails wrong pass"
      (with-redefs [user/find-by-username
                    (fn [session m] {:username "user" :password (hs/encrypt "foobar")})
                    member/retrieve-groups-ids (fn [session user-id]
                                                 '({:group-id "group-id-1"}
                                                   {:group-id "group-id-2"}))]
        (let [response (app (heimdall-request
                             (mock/request :post "/create-auth-token"
                                           (json/write-str {:username "user" :password "foo"}))))]
          (is (= (:status response) 401)))))))


(defn  valid-refresh-token
  []
  (service/make-refresh-token (sign/to-timestamp (t/now))
                              auth-config
                              {:username "foo" :id #uuid "b14bf8f1-d98b-4ca2-97e9-7c95ebffbcb1"}))

(defn refresh-token-record
  [refresh-token]
  {:user-id #uuid "b14bf8f1-d98b-4ca2-97e9-7c95ebffbcb1"
   :issued 1472034878
   :id #uuid "803ad9b8-d482-43af-9409-a28bae2a95a0"
   :refresh-token refresh-token
   :valid true})

(deftest test-refresh-auth-token
  (testing "Creates a new valid token"
    (let [refresh-token (valid-refresh-token)]
      (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued]
                                                 (refresh-token-record refresh-token))
                    user/find-by-id (fn [session user-id]
                                      {:id #uuid "b14bf8f1-d98b-4ca2-97e9-7c95ebffbcb1"
                                       :username "foo"})
                    rt/invalidate! (fn [session id] '())
                    rt/add! (fn [session token] '())]
        (let [response (app (heimdall-request
                             (mock/request :post "/refresh-auth-token"
                                           (json/write-str {:refresh-token refresh-token}))))
              body (json/read-str (:body response) :key-fn keyword)]
          (is (= (:status response) 201))
          (is (:token-pair body))
          (is (not= (:refresh-token (:token-pair body))
                    refresh-token))))))
  (testing "Handles a refresh token not found"
    (let [refresh-token (valid-refresh-token)]
      (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued] nil)
                    user/find-by-id (fn [session user-id] nil)]
        (let [response (app (heimdall-request
                             (mock/request :post "/refresh-auth-token"
                                           (json/write-str {:refresh-token refresh-token}))))]
          (is (= (:status response) 401))
          (is (= (:message (json/read-str (:body response) :key-fn keyword))
                 "Refresh token revoked/deleted or new refresh token already created"))))))
  (testing "Handles case when token to refresh wasn't signed properly"
    (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued] nil)
                  user/find-by-id (fn [session user-id] nil)]
      (let [response (app (heimdall-request
                           (mock/request :post "/refresh-auth-token"
                                         (json/write-str {:refresh-token "foo"}))))]
        (is (= (:status response) 401))
        (is (= (:message (json/read-str (:body response) :key-fn keyword))
               "Invalid or expired refresh token provided"))))))

(deftest test-invalidate-refresh-token
  (testing "invalidate existing refresh token"
    (let [refresh-token (valid-refresh-token)]
      (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued]
                                                 (refresh-token-record refresh-token))
                    rt/invalidate! (fn [session id] '())]
        (let [response (app (heimdall-request (mock/request :post "/invalidate-refresh-token"
                                                            (json/write-str
                                                             {:refresh-token refresh-token}))))]
          (is (= (:status response) 200))
          (is (= (:message (json/read-str (:body response) :key-fn keyword))
                 "Invalidated successfully"))))))

  (testing "invalidate refresh token - token not valid signed"
    (let [response (app (heimdall-request (mock/request :post "/invalidate-refresh-token"
                                                        (json/write-str {:refresh-token "abc"}))))]
      (is (= (:status response) 401))
      (is (= (:message (json/read-str (:body response) :key-fn keyword))
             "Invalid or expired refresh token provided"))))
  (testing "invalidate refresh token not found"
    (let [refresh-token (valid-refresh-token)]
      (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued] nil)]
        (let [response (app (heimdall-request
                             (mock/request :post "/invalidate-refresh-token"
                                           (:json/write-str {:refresh-token refresh-token}))))]
          (is (= (:status response) 401))
          (is (= (:message (json/read-str (:body response) :key-fn keyword))
                 "Invalid or expired refresh token provided")))))))

(defn valid-auth-token
  []
  (service/make-auth-token {:username "test-user"
                            :id "29404f79-8825-4097-900b-bd7626a7cbc1"}
                           auth-config))

(deftest test-create-group
  (testing "the /create-route route works"
    (with-redefs [group/create! (fn [session group-name]
                                  {:group-id #uuid "bfa00b8a-f57e-43ff-829b-d7469e797000"})
                  member/add-user-to-group (fn [session user-id grp-id role]
                                             '())]
      (let [response (app (heimdall-request
                           (mock/header (mock/request :post "/create-group"
                                                      (json/write-str {:group-name "test-grp"}))
                                        "authorization" (format "Token %s" (valid-auth-token)))))]
        (is (= (:status response) 201))
        (is (= (:body response) "Group successfully created")))))
  (testing "without a token /create-group fails"
    (let [response (app (heimdall-request (mock/request :post "/create-group"
                                                        (json/write-str {:group-name "test-grp"}))))]
      (is (= (:status response) 401))
      (is (= (:body response) "Unauthenticated"))))
  (testing "without a valid token /create-group fails"
    (let [response (app (heimdall-request
                         (mock/header (mock/request :post "/create-group"
                                                    (json/write-str {:group-name "test-grp"}))
                                      "authorization" (format "Token 384905-6"))))]
      (is (= (:status response) 401))
      (is (= (:body response) "Unauthenticated")))))
