(ns kixi.heimdall.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.format :refer [wrap-restful-format]]
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

(defn- dynamodb
  [req]
  (:db (:components req)))

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
   (log/info "Returning error" status information)
   {:status status
    :body {:error (name error)}})

  ([ctx status error-key msg]
   (return-error ctx status {:kixi.heimdall.schema/error error-key
                             :kixi.heimdall.schema/msg msg})))

(defn auth-token [req]
  (let [[ok? res] (service/create-auth-token (dynamodb req)
                                             (communications req)
                                             (:auth-conf req)
                                             (:params req))]
    (if ok?
      {:status 201 :body res}
      (return-error {:msg res :fn "auth-token"} :unauthenticated 401))))

(defn new-user [req]
  (let [[ok? res] (service/signup-user! (dynamodb req)
                                        (communications req)
                                        (:params req))]
    (if ok?
      {:status 201 :body res}
      (return-error {:msg res :fn "new-user"} :user-creation-failed 400))))

(defn refresh-auth-token [req]
  (let [refresh-token (-> req :params :refresh-token)
        [ok? res] (service/refresh-auth-token (dynamodb req)
                                              (:auth-conf req)
                                              refresh-token)]
    (if ok?
      {:status 201 :body res}
      (return-error {:msg res :fn "refresh-auth-token"} :unauthenticated 401))))

(defn invalidate-refresh-token [req]
  (let [refresh-token (-> req :params :refresh-token)
        [ok? res] (service/invalidate-refresh-token (dynamodb req)
                                                    (:auth-conf req)
                                                    refresh-token)]
    (if ok?
      {:status 200 :body res}
      (return-error {:msg res :fn "invalidate-refresh-token"} :invalidation-failed 400))))

(defn create-group [req]
  (let [{:keys [group-id group-name created]
         :or {created (util/db-now)}} (:params req)
        ok? (and
             (:user-id req)
             (service/create-group-event (dynamodb req)
                                         (communications req)
                                         {:group-name group-name
                                          :group-id group-id
                                          :user-id (:user-id req)
                                          :created created
                                          :group-type "group"}))]
    (if ok?
      {:status 201 :body "Group successfully created"}
      (return-error {:msg "Please provide valid parameters (name for the group)" :fn "create-group"} :group-creation-failed 400))))

(defn get-users [req]
  {:status 200 :body {:type "users" :items (service/users (dynamodb req) (get (:params req) "id"))}})

(defn get-groups [req]
  {:status 200 :body {:type "groups" :items (service/groups (dynamodb req) (get (:params req) "id"))}})

(defn get-all-groups [req]
  {:status 200 :body {:type "groups" :items (service/all-groups (dynamodb req))}})

(defn reset-password
  [req]
  (let [username (:username (:params req))
        password (:password (:params req))
        reset-code (:reset-code (:params req))]
    (if (and username password reset-code)
      (let [[ok? res] (service/complete-password-reset! (dynamodb req)
                                                        (communications req)
                                                        username
                                                        password
                                                        reset-code)]
        (if ok?
          {:status 200 :body res}
          (return-error {:msg res :fn "service/complete-password-reset!"} :password-reset-failed 400)))
      {:status 400 :body "Missing fields"})))

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
  (GET "/groups/search" [] get-all-groups)
  (GET "/groups" [] get-groups))

(defroutes public-routes
  (GET "/healthcheck" [] "Hello World")
  (POST "/user" [] new-user)
  (POST "/create-auth-token" [] auth-token)
  (POST "/refresh-auth-token" [] refresh-auth-token)
  (POST "/invalidate-refresh-token" [] invalidate-refresh-token)
  (POST "/reset-password" [] reset-password))

(defroutes app-routes
  public-routes
  (wrap-routes secured-routes wrap-insert-auth-info)
  (route/not-found "Not Found"))

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
      wrap-catch-exceptions
      wrap-keyword-params
      (wrap-restful-format :formats [:transit-json])))
