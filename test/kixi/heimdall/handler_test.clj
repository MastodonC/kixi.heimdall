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
            [kixi.heimdall.invites :as invites]
            [kixi.heimdall.util :as util]
            [buddy.hashers :as hs]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [buddy.sign.util :as sign]
            [clj-time.core :as t]
            [kixi.heimdall.config :as config]
            [kixi.comms :as comms]
            [taoensso.timbre :as log]
            [environ.core :refer [env]]
            [cognitect.transit :as tr]
            [kixi.heimdall.integration.base :refer [rand-str]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [org.httpkit.BytesInputStream]))

(def transit-encoding-level :json-verbose) ;; DO NOT CHANGE
(defn transit-decode-bytes [in]
  (let [reader (tr/reader in transit-encoding-level)]
    (tr/read reader)))
(defn transit-decode [^String s]
  (let [sbytes (.getBytes s)
        in (ByteArrayInputStream. sbytes)
        reader (tr/reader in transit-encoding-level)]
    (tr/read reader)))
(defn transit-decode-stream [in]
  (let [reader (tr/reader in transit-encoding-level)]
    (tr/read reader)))
(defn transit-encode [s]
  (let [out (ByteArrayOutputStream. 4096)
        writer (tr/writer out transit-encoding-level)]
    (tr/write writer s)
    (.toString out)))

(defn transit-json-request
  [request]
  (mock/content-type request "application/transit+json"))

(def auth-config
  (:auth-conf (config/config (keyword (env :system-profile "test")))))

(defn auth-config-added
  [request]
  (assoc request :auth-conf auth-config))

(defrecord DummyCommunications []
  comms/Communications
  (send-event! [_ _ _ _]
    nil)
  (send-event! [_ _ _ _ _]
    nil)
  (send-command! [_ _ _ _ _]
    nil)
  (attach-event-handler! [_ _ _ _ _]
    nil))

(defrecord MockCommunications [triggered]
  comms/Communications
  (send-event! [_ event version payload]
    (log/info "send-event!")
    (swap! triggered update-in  [:comms :sent] concat [{:event event :version version :payload payload}]))
  (send-event! [_ event version payload opts]
    (swap! triggered update-in [:comms :sent] concat [{:event event :version version :payload payload :opts opts}]))
  (send-command! [_ command version user payload]
    (swap! triggered update-in [:comms :command] concat [{:command command :user user :version version :payload payload}]))
  (attach-event-handler! [_ group-id event version handler]
    nil))

(defn auth-info-added
  [request]
  (mock/header
   (mock/header request
                "user-id" (java.util.UUID/randomUUID)
                )
   "user-groups" [(java.util.UUID/randomUUID)]))

(defn heimdall-request
  [request]
  (-> request
      transit-json-request
      auth-config-added
      auth-info-added))

(defn comms-app
  "comms-app either takes an atom to test which Communication functions were triggered or makes sure something is there to fake comms presence"
  ([handler request triggered]
   (let [mock-comms (->MockCommunications triggered)]
     (handler (assoc-in request [:components :communications] mock-comms))))
  ([handler request]
   (handler (assoc-in request [:components :communications] (->DummyCommunications)))))

(deftest unsecured-routes
  (testing "main route"
    (let [response (comms-app app (heimdall-request (mock/request :get "/healthcheck")))]
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
                                             :id (str (java.util.UUID/randomUUID))})
                    rt/add! (fn [session m] true)
                    member/retrieve-groups-ids (fn [session user-id]
                                                 '({:group-id "group-id-1"}
                                                   {:group-id "group-id-2"}))
                    group/find-user-group (fn [session user-id]
                                            {:id (str (java.util.UUID/randomUUID))})]
        (let [events (atom {})
              response (comms-app app (heimdall-request
                                       (mock/request :post "/create-auth-token"
                                                     (transit-encode {:username "user@boo.com" :password "foo12CCbb"}))) events)]
          (is (= (:status response) 201))
          (let [body-resp (transit-decode-stream (:body response))]
            (is (:token-pair body-resp)))
          (let [event (first (get-in @events [:comms :sent]))]
            (is (= (dissoc event :opts)
                   {:event :kixi.heimdall/user-logged-in
                    :version "1.0.0"
                    :payload {:username "user@boo.com"}}))
            (is (= (keys (:opts event))
                   '(:kixi.comms.event/partition-key)))))))

    (testing "authentication fails wrong user"
      (with-redefs [user/find-by-username (fn [session m] nil)
                    member/retrieve-groups-ids (fn [session user-id]
                                                 '())
                    group/find-user-group (fn [session _] nil)]
        (let [response (comms-app app (heimdall-request
                                       (mock/request :post "/create-auth-token"
                                                     (transit-encode {:username "user@boo.com" :password "foo12CCbb"}))))]
          (is (= (:status response) 401)))))

    (testing "authentication fails wrong pass"
      (with-redefs [user/find-by-username
                    (fn [session m] {:username "user" :password (hs/encrypt "foobar")})
                    member/retrieve-groups-ids (fn [session user-id]
                                                 '({:group-id "group-id-1"}
                                                   {:group-id "group-id-2"}))]
        (let [response (comms-app app (heimdall-request
                                       (mock/request :post "/create-auth-token"
                                                     (transit-encode {:username "user" :password "foo"}))))]
          (is (= (:status response) 401)))))))


(defn  valid-refresh-token
  []
  (service/make-refresh-token (sign/to-timestamp (t/now))
                              auth-config
                              {:id #uuid "b14bf8f1-d98b-4ca2-97e9-7c95ebffbcb1"}))

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
                    rt/add! (fn [session token] '())
                    member/retrieve-groups-ids (fn [_ _] [])
                    group/find-user-group (fn [_ _] {:id (java.util.UUID/randomUUID)
                                                     :name "username@boo.com"})]
        (let [response (comms-app app (heimdall-request
                                       (mock/request :post "/refresh-auth-token"
                                                     (transit-encode {:refresh-token refresh-token}))))
              body (transit-decode-stream (:body response))]
          (is (= (:status response) 201))
          (is (:token-pair body))
          (is (not= (:refresh-token (:token-pair body))
                    refresh-token))))))
  (testing "Handles a refresh token not found"
    (let [refresh-token (valid-refresh-token)]
      (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued] nil)
                    user/find-by-id (fn [session user-id] nil)
                    member/retrieve-groups-ids (fn [_ _] [])
                    group/find-user-group (fn [_ _] {:id (java.util.UUID/randomUUID)
                                                     :name "username@boo.com"})]
        (let [response (comms-app app (heimdall-request
                                       (mock/request :post "/refresh-auth-token"
                                                     (transit-encode {:refresh-token refresh-token}))))]
          (is (= (:status response) 401))
          (is (= (:error (transit-decode-stream (:body response)))
                 "unauthenticated"))))))
  (testing "Handles case when token to refresh wasn't signed properly"
    (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued] nil)
                  user/find-by-id (fn [session user-id] nil)
                  member/retrieve-groups-ids (fn [_ _] [])]
      (let [response (comms-app app (heimdall-request
                                     (mock/request :post "/refresh-auth-token"
                                                   (transit-encode {:refresh-token "foo"}))))]
        (is (= (:status response) 401))
        (is (= (:error (transit-decode-stream (:body response)))
               "unauthenticated"))))))

(deftest test-invalidate-refresh-token
  (testing "invalidate existing refresh token"
    (let [refresh-token (valid-refresh-token)]
      (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued]
                                                 (refresh-token-record refresh-token))
                    rt/invalidate! (fn [session id] '())]
        (let [response (comms-app app (heimdall-request (mock/request :post "/invalidate-refresh-token"
                                                                      (transit-encode
                                                                       {:refresh-token refresh-token}))))]
          (is (= (:status response) 200))
          (is (= (:message (transit-decode-stream (:body response)))
                 "Invalidated successfully"))))))

  (testing "invalidate refresh token - token not valid signed"
    (let [response (comms-app app (heimdall-request (mock/request :post "/invalidate-refresh-token"
                                                                  (transit-encode {:refresh-token "abc"}))))]
      (is (= (:status response) 400))
      (is (= (:error (transit-decode-stream (:body response)))
             "invalidation-failed"))))
  (testing "invalidate refresh token not found"
    (let [refresh-token (valid-refresh-token)]
      (with-redefs [rt/find-by-user-and-issued (fn [session user-id issued] nil)]
        (let [response (comms-app app (heimdall-request
                                       (mock/request :post "/invalidate-refresh-token"
                                                     (:transit-encode {:refresh-token refresh-token}))))]
          (is (= (:status response) 400))
          (is (= (:error (transit-decode-stream (:body response)))
                 "invalidation-failed")))))))

(defn valid-auth-token
  []
  (service/make-auth-token {:username "test-user"
                            :id "29404f79-8825-4097-900b-bd7626a7cbc1"}
                           auth-config))

(deftest test-create-group
  (testing "the /create-route route works"
    (let [group-id (str (java.util.UUID/randomUUID))]
      (with-redefs [group/add! (fn [session _] {:group-id group-id})
                    group/find-by-name (fn [session name] nil)
                    member/add! (fn [_ _]
                                  '())]
        (let [events (atom {})
              response (comms-app app (heimdall-request
                                       (mock/request :post "/group"
                                                     (transit-encode {:group-id group-id
                                                                      :group-name "test-grp"
                                                                      :created (util/db-now)})))
                                  events)]
          (is (= (:status response) 201))
          (is (= (:body response) "Group successfully created"))
          (is (= (:event (first (get-in @events [:comms :sent]))) :kixi.heimdall/group-created)))))))

(defn uuid
  []
  (java.util.UUID/randomUUID))

(deftest new-user-test
  (testing "new user can be added if password passes the validation"
    (with-redefs [user/find-by-username (fn [_ _] {:pre-signup true})
                  user/signed-up! (fn [_ _]
                                    {:pre-signup false})
                  invites/consume! (fn [_ _ _] true)
                  group/add! (fn [_ _] {:group-id (java.util.UUID/randomUUID)})
                  member/add! (fn [_ _] '())]
      (let [response (comms-app app (heimdall-request
                                     (mock/request :post "/user"
                                                   (transit-encode {:username (str "user-" (str (uuid)) "@boo.com")
                                                                    :password "secret1Pass"
                                                                    :name "Jane Doe"}))))]
        (is (= (:status response) 201)))))
  (testing "new user can not be added if password fails the validation"
    (with-redefs [user/find-by-username (fn [_ _] nil)
                  group/add! (fn [_ _] {:group-id (java.util.UUID/randomUUID)})
                  member/add! (fn [_ _] '())]
      (let [response (comms-app app (heimdall-request
                                     (mock/request :post "/user"
                                                   (transit-encode {:username "user@boo.com"
                                                                    :password "foo"
                                                                    :name "Bob Marley"}))))]
        (is (= (:status response) 400))
        (is (= (:error (transit-decode-stream (:body response)))
               "user-creation-failed"))))))

(deftest reset-password-test
  (testing "passwords can be reset"
    (with-redefs [user/find-by-username (fn [_ _] {:pre-signup true})
                  user/signed-up! (fn [_ _] {:pre-signup false})
                  invites/consume! (fn [_ _ _] true)
                  group/add! (fn [_ _] {:group-id (java.util.UUID/randomUUID)})
                  member/add! (fn [_ _] '())]
      (let [response (comms-app app (heimdall-request
                                     (mock/request :post "/user"
                                                   (transit-encode {:username "user@boo.com"
                                                                    :password "secret1Pass"
                                                                    :name "Jane Doe"}))))]
        (is (= (:status response) 201))))))

(deftest groups-can-be-returned-and-paged
  (let [random-group (fn []
                       {:group-id (java.util.UUID/randomUUID)
                        :group-name (rand-str)
                        :type "group"})]
    (testing "groups-are-returned"
      (with-redefs [group/all (fn [_] (repeatedly 10 random-group))]
        (let [response (comms-app app (heimdall-request
                                       (mock/request :get "/groups/search")))]
          (is (= (:status response) 200)))))
    (testing "groups-are-returned- count=10"
      (with-redefs [group/all (fn [_] (repeatedly 20 random-group))]
        (let [response (comms-app app (heimdall-request
                                       (mock/request :get "/groups/search?count=10")))]
          (is (= (:status response) 200))
          (when (= 200 (:status response))
            (let [body-resp (transit-decode-stream (:body response))]
              (is (= 10 (count (:items body-resp))))
              (is (= 10 (get-in body-resp [:paging :count])))
              (is (= 20 (get-in body-resp [:paging :total])))
              (is (= 0 (get-in body-resp [:paging :index]))))))))

    (testing "groups-are-returned- index=7"
      (with-redefs [group/all (fn [_] (repeatedly 20 random-group))]
        (let [response (comms-app app (heimdall-request
                                       (mock/request :get "/groups/search?index=7")))]
          (is (= (:status response) 200))
          (when (= 200 (:status response))
            (let [body-resp (transit-decode-stream (:body response))]
              (is (= 13 (get-in body-resp [:paging :count])))
              (is (= 20 (get-in body-resp [:paging :total])))
              (is (= 7 (get-in body-resp [:paging :index]))))))))))
