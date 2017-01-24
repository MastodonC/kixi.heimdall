(ns kixi.heimdall.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [kixi.heimdall.application :as app]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.schema :as schema]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [environ.core :refer [env]]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as ks]
            [clojure.spec :as spec]))

(defn- cassandra-session
  [req]
  (:cassandra-session (:components req)))

(defn- communications
  [req]
  (:communications (:components req)))


(spec/fdef return-error
           :args (spec/cat :ctx :kixi.heimdall.schema/context
                           :args (spec/alt :error-map :kixi.heimdall.schema/error-map
                                           :error-parts (spec/cat :error :kixi.heimdall.schema/error
                                                                  :msg :kixi.heimdall.schema/msg))))

(defn return-error
  ([information error status]
   (log/info information)
   {:status status
    :body (name error)})

  ([ctx status error-key msg]
   (return-error ctx status {:kixi.heimdall.schema/error error-key
                             :kixi.heimdall.schema/msg msg})))

(defn auth-token [req]
  (let [[ok? res] (service/create-auth-token (cassandra-session req)
                                             (communications req)
                                             (:auth-conf req)
                                             (:params req))]
    (if ok?
      {:status 201 :body res}
      (return-error {:msg res :fn "auth-token"} :unauthenticated 401))))

(defn new-user [req]
  (let [[ok? res] (service/new-user (cassandra-session req)
                                    (communications req)
                                    (:params req))]
    (if ok?
      {:status 201 :body res}
      (return-error {:msg res :fn "new-user"} :user-creation-failed 500))))

(defn refresh-auth-token [req]
  (let [refresh-token (-> req :params :refresh-token)
        [ok? res] (service/refresh-auth-token (cassandra-session req)
                                              (:auth-conf req)
                                              refresh-token)]
    (if ok?
      {:status 201 :body res}
      (return-error {:msg res :fn "refresh-auth-token"} :unauthenticated 401))))

(defn invalidate-refresh-token [req]
  (let [refresh-token (-> req :params :refresh-token)
        [ok? res] (service/invalidate-refresh-token (cassandra-session req)
                                                    (:auth-conf req)
                                                    refresh-token)]
    (if ok?
      {:status 200 :body res}
      (return-error {:msg res :fn "invalidate-refresh-token"} :invalidation-failed 500))))

(defn create-group [req]
  (let [ok? (and
             (:user-id req)
             (service/create-group-event (cassandra-session req)
                                         (communications req)
                                         {:group (:params req) :user-id (:user-id req)}))]
    (if ok?
      {:status 201 :body "Group successfully created"}
      (return-error {:msg "Please provide valid parameters (name for the group)" :fn "create-group"} :group-creation-failed 500))))

(defn get-users [req]
  {:status 200 :body {:type "users" :items (service/users (cassandra-session req) (get (:params req) "id"))}})

(defn get-groups [req]
  {:status 200 :body {:type "groups" :items (service/groups (cassandra-session req) (get (:params req) "id"))}})

(defn get-all-groups [req]
  {:status 200 :body {:type "groups" :items (service/all-groups (cassandra-session req))}})

(defn escape-html
  "Change special characters into HTML character entities."
  [text]
  (.. #^String (str text)
      (replace "&" "&amp;")
      (replace "<" "&lt;")
      (replace ">" "&gt;")
      (replace "\"" "&quot;")))

(defn wrap-escape-html
  [handler]
  (fn [request]
    (handler (update-in request [:params] #(clojure.walk/prewalk (fn [v] (if (string? v)
                                                                           (escape-html v)
                                                                           v)) %)))))

(defn wrap-record-metric
  [handler]
  (fn [request]
    (let [metrics (:metrics (:components request))
          start-fn (:insert-time-in-ctx metrics)
          record-fn (:record-ctx-metrics metrics)]
      (let [metric-started-request (start-fn request)
            response (try (handler metric-started-request)
                          (catch Throwable t
                            (do (record-fn request 500)
                                (throw t))))]
        (record-fn metric-started-request (:status response))
        response))))

(defn vec-if-not
  [x]
  (if (or (nil? x)
          (vector? x))
    x
    (vector x)))

(defn wrap-insert-auth-info
  [handler]
  (fn [request]
    (let [user-id (get (:headers request) "user-id")
          user-groups (-> (get (:headers request) "user-groups")
                          (clojure.string/split #",")
                          vec-if-not)
          new-req (assoc request
                         :user-id user-id
                         :user-groups user-groups)]
      (handler new-req))))


(defroutes secured-routes
  (POST "/group" [] create-group)
  (GET "/users" [] get-users)
  (GET "/groups" [] get-groups)
  (GET "/search" [] get-all-groups))

(defroutes public-routes
  (GET "/" [] "Hello World")
  (POST "/user" [] new-user)
  (POST "/create-auth-token" [] auth-token)
  (POST "/refresh-auth-token" [] refresh-auth-token)
  (POST "/invalidate-refresh-token" [] invalidate-refresh-token))

(defroutes app-routes
  public-routes
  (wrap-routes secured-routes wrap-insert-auth-info)
  (route/not-found "Not Found")  )

(defn wrap-catch-exceptions [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t
           (do (log/error t)
               (return-error {:msg "Something went wrong" :fn "wrap-catch-exception"} :runtime-exception 500))))))

(def app
  (-> app-routes
      wrap-escape-html
      wrap-params
      wrap-record-metric
      wrap-catch-exceptions
      wrap-keyword-params
      wrap-json-params
      wrap-json-response))
