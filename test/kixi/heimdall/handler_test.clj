(ns kixi.heimdall.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [kixi.heimdall.handler :refer :all]
            [kixi.heimdall.user :as user]
            [buddy.hashers :as hs]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(def auth-config-test
  (let [f (io/resource "auth-conf-test.edn")]
    (edn/read-string (slurp f))))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (= (:body response) "Hello World"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404))))

  (testing "auth route"
    (with-redefs [auth-config auth-config-test]
      (testing "authentication succeeds"
        (with-redefs [user/find-by-username (fn [session m] {:username "user" :password (hs/encrypt "foo")})]
          (let [response (app (mock/request :post "/create-auth-token"
                                            {:username "user" :password "foo"}))]
            (is (= (:status response) 201))
            (is (:token (:body response))))))
      (testing "authentication fails wrong user"
        (with-redefs [user/find-by-username (fn [session m] nil)]
          (let [response (app (mock/request :post "/create-auth-token"
                                            {:username "user" :password "foo"}))]
            (is (= (:status response) 401)))))
      (testing "authentication fails wrong pass"
        (with-redefs [user/find-by-username (fn [session m] {:username "user" :password (hs/encrypt "foobar")})]
          (let [response (app (mock/request :post "/create-auth-token"
                                            {:username "user" :password "foo"}))]
            (is (= (:status response) 401)))))))
  )
