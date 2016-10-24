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
            [kixi.heimdall.config :as config]
            [kixi.comms :as comms]
            [taoensso.timbre :as log]))

(defn json-request
  [request]
  (mock/content-type request "application/json"))

(def auth-config
  (config/auth-conf (config/config :test)))

(defn auth-config-added
  [request]
  (assoc request :auth-conf auth-config))

(defn metrics-added
  [request]
  (assoc-in request [:components :metrics] {:insert-time-in-ctx identity
                                            :record-ctx-metrics (fn [a _] a)}))

(defrecord DummyCommunications []
  comms/Communications
  (send-event! [_ _ _ _]
    nil)
  (send-event! [_ _ _ _ _]
    nil)
  (send-command! [_ _ _ _]
    nil)
  (attach-event-handler! [_ _ _ _ _]
    nil))

(defrecord MockCommunications [triggered]
  comms/Communications
  (send-event! [_ event version payload]
    (log/info "send-event!")
    (swap! triggered update-in  [:comms :sent] concat [{:event event :version version :payload payload}]))
  (send-event! [_ event version payload command-id]
    (swap! triggered update-in [:comms :sent] concat [{:event event :version version :payload payload :command-id command-id}]))
  (send-command! [_ command version payload]
    (swap! triggered update-in [:comms :command] concat [{:command command :version version :payload payload}]))
  (attach-event-handler! [_ group-id event version handler]
    nil))

(defn heimdall-request
  [request]
  (-> request
      json-request
      auth-config-added
      metrics-added))

(defn comms-app
  "comms-app either takes an atom to test which Communication functions were triggered or makes sure something is there to fake comms presence"
  ([handler request triggered]
   (let [mock-comms (->MockCommunications triggered)]
     (handler (assoc-in request [:components :communications] mock-comms))))
  ([handler request]
   (handler (assoc-in request [:components :communications] (->DummyCommunications)))))

(deftest unsecured-routes
  (testing "main route"
    (let [response (comms-app app (heimdall-request (mock/request :get "/")))]
      (is (= (:status response) 200))
      (is (= (:body response) "Hello World"))))

  (testing "not-found route"
    (let [response (comms-app app (heimdall-request (mock/request :get "/invalid")))]
      (is (= (:status response) 404)))))


(deftest test-authentication
  (testing "auth route"
    (testing "authentication succeeds"
      (with-redefs [user/find-by-username (fn [session m]
                                            {:username "user@boo.com"
                                             :password (hs/encrypt "foo12CCbb")
                                             :id (uuid/random)})
                    rt/add! (fn [session m] true)
                    member/retrieve-groups-ids (fn [session user-id]
                                                 '({:group-id "group-id-1"}
                                                   {:group-id "group-id-2"}))]
        (let [events (atom {})
              response (comms-app app (heimdall-request
                                       (mock/request :post "/create-auth-token"
                                                     (json/write-str {:username "user@boo.com" :password "foo12CCbb"}))) events)]
          (is (= (:status response) 201))
          (let [body-resp (json/read-str (:body response) :key-fn keyword)]
            (is (:token-pair body-resp)))
          (is (= @events {:comms {:sent '({:event :kixi.heimdall/user-logged-in :version "1.0.0" :payload {:username "user@boo.com"}})}}))          )))
    (testing "authentication fails wrong user"
      (with-redefs [user/find-by-username (fn [session m] nil)
                    member/retrieve-groups-ids (fn [session user-id]
                                                 '())]
        (let [response (comms-app app (heimdall-request
                                       (mock/request :post "/create-auth-token"
                                                     (json/write-str {:username "user@boo.com" :password "foo12CCbb"}))))]
          (is (= (:status response) 401)))))
    (testing "authentication fails wrong pass"
      (with-redefs [user/find-by-username
                    (fn [session m] {:username "user" :password (hs/encrypt "foobar")})
                    member/retrieve-groups-ids (fn [session user-id]
                                                 '({:group-id "group-id-1"}
                                                   {:group-id "group-id-2"}))]
        (let [response (comms-app app (heimdall-request
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
        (let [response (comms-app app (heimdall-request
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
        (let [response (comms-app app (heimdall-request
                                       (mock/request :post "/refresh-auth-token"
                                                     (json/write-str {:refresh-token refresh-token}))))]
          (is (= (:status response) 401))
          (is (= (:message (json/read-str (:body response) :key-fn keyword))
                 "Refresh token revoked/deleted or new refresh token already created"))))))
  (testing "Handles case when token to refresh wasn't signed properly"
    (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued] nil)
                  user/find-by-id (fn [session user-id] nil)]
      (let [response (comms-app app (heimdall-request
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
        (let [response (comms-app app (heimdall-request (mock/request :post "/invalidate-refresh-token"
                                                                      (json/write-str
                                                                       {:refresh-token refresh-token}))))]
          (is (= (:status response) 200))
          (is (= (:message (json/read-str (:body response) :key-fn keyword))
                 "Invalidated successfully"))))))

  (testing "invalidate refresh token - token not valid signed"
    (let [response (comms-app app (heimdall-request (mock/request :post "/invalidate-refresh-token"
                                                                  (json/write-str {:refresh-token "abc"}))))]
      (is (= (:status response) 401))
      (is (= (:message (json/read-str (:body response) :key-fn keyword))
             "Invalid or expired refresh token provided"))))
  (testing "invalidate refresh token not found"
    (let [refresh-token (valid-refresh-token)]
      (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued] nil)]
        (let [response (comms-app app (heimdall-request
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
      (let [events (atom {})
            response (comms-app app (heimdall-request
                                     (mock/header (mock/request :post "/create-group"
                                                                (json/write-str {:group-name "test-grp"}))
                                                  "authorization" (format "Token %s" (valid-auth-token))))
                                events)]
        (is (= (:status response) 201))
        (is (= (:body response) "Group successfully created"))
        (is (= @events {:comms {:sent '({:event :kixi.heimdall/group-created :version "1.0.0" :payload {:group-name "test-grp"}})}})))))
  (testing "without a token /create-group fails"
    (let [events (atom {})
          response (comms-app app (heimdall-request (mock/request :post "/create-group"
                                                                  (json/write-str {:group-name "test-grp"}))) events)]
      (is (= (:status response) 401))
      (is (= (:body response) "Unauthenticated"))
      (is (= @events {}))))
  (testing "without a valid token /create-group fails"
    (let [response (comms-app app (heimdall-request
                                   (mock/header (mock/request :post "/create-group"
                                                              (json/write-str {:group-name "test-grp"}))
                                                "authorization" (format "Token 384905-6"))))]
      (is (= (:status response) 401))
      (is (= (:body response) "Unauthenticated")))))

(deftest new-user-test
  (testing "new user can be added if password passes the validation"
    (with-redefs [user/add! (fn [_ _] '())
                  user/find-by-username (fn [_ _] nil)]
      (let [response (comms-app app (heimdall-request
                                     (mock/request :post "/user"
                                                   (json/write-str {:username "user@boo.com"
                                                                    :password "secret1Pass"}))))]
        (is (= (:status response) 201)))))
  (testing "new user can not be added if password fails the validation"
    (with-redefs [user/add! (fn [_ _] '())
                  user/find-by-username (fn [_ _] nil)]
      (let [response (comms-app app (heimdall-request
                                     (mock/request :post "/user"
                                                   (json/write-str {:username "user@boo.com"
                                                                    :password "foo"}))))]
        (is (= (:status response) 401))
        (is (= (:message (json/read-str (:body response) :key-fn keyword))
               "Please match the required format: password should have at least 8 letters, one uppercase and one lowercase letter, and one number")))))
  (testing "new user triggers a send-event!"
    (with-redefs [user/add! (fn [_ _] '())
                  user/find-by-username (fn [_ _] nil)]
      (let [events (atom {})
            response (comms-app app (heimdall-request
                                     (mock/request :post "/user"
                                                   (json/write-str {:username "user@boo.com"
                                                                    :password "secret1Pass"})))
                                events)]
        (is (= @events {:comms {:sent '({:event :kixi.heimdall/user-created :version "1.0.0" :payload {:username "user@boo.com"}})}}))))    ))
