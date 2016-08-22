(ns kixi.heimdall.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [kixi.heimdall.handler :refer :all]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.refresh-token :as rt]
            [buddy.hashers :as hs]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [qbits.alia.uuid :as uuid]))

(defn json-request
  [request]
  (mock/content-type request "application/json"))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (= (:body response) "Hello World"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404))))

  (testing "auth route"
    (testing "authentication succeeds"
      (with-redefs [user/find-by-username (fn [session m] {:username "user" :password (hs/encrypt "foo") :id (uuid/random)})
                    rt/add! (fn [session m] true)]
        (let [response (app (json-request (mock/request :post "/create-auth-token"
                                                        (json/write-str {:username "user" :password "foo"}))))]
          (is (= (:status response) 201))
          (is (:token-pair (json/read-str (:body response) :key-fn keyword))))))
    (testing "authentication fails wrong user"
      (with-redefs [user/find-by-username (fn [session m] nil)]
        (let [response (app (json-request (mock/request :post "/create-auth-token"
                                                        (json/write-str {:username "user" :password "foo"}))))]
          (is (= (:status response) 401)))))
    (testing "authentication fails wrong pass"
      (with-redefs [user/find-by-username (fn [session m] {:username "user" :password (hs/encrypt "foobar")})]
        (let [response (app (json-request (mock/request :post "/create-auth-token"
                                                        (json/write-str {:username "user" :password "foo"}))))]
          (is (= (:status response) 401))))))
  )
