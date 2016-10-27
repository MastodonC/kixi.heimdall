(ns kixi.heimdall.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
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
            [kixi.comms :refer [Communications] :as comms]
            [clojure.spec :as spec]))

(defn auth-token [req]
  (let [[ok? res] (service/create-auth-token (:cassandra-session (:components req))
                                             (:auth-conf req)
                                             (:params req))]
    (if ok?
      (do (comms/send-event! (:communications (:components req)) :kixi.heimdall/user-logged-in "1.0.0" (select-keys (:params req) [:username]))
          {:status 201 :body res})
      {:status 401 :body res})))

(defn new-user [req]
  (let [[ok? res] (service/new-user (:cassandra-session (:components req))
                                    (:params req))]
    (if ok?
      (do  (comms/send-event! (:communications (:components req)) :kixi.heimdall/user-created "1.0.0" (select-keys (:params req) [:username]))
           {:status 201 :body res})
      {:status 401 :body res})))

(defn refresh-auth-token [req]
  (let [refresh-token (-> req :params :refresh-token)
        [ok? res] (service/refresh-auth-token (:cassandra-session (:components req))
                                              (:auth-conf req)
                                              refresh-token)]
    (if ok?
      {:status 201 :body res}
      {:status 401 :body res})))

(defn invalidate-refresh-token [req]
  (let [refresh-token (-> req :params :refresh-token)
        [ok? res] (service/invalidate-refresh-token (:cassandra-session (:components req))
                                                    (:auth-conf req)
                                                    refresh-token)]
    (if ok?
      {:status 200 :body res}
      {:status 401 :body res})))

(defn- parse-header
  [request token-name]
  (some->> (get (:headers request) "authorization")
           (re-find (re-pattern (str "^" token-name " (.+)$")))
           (second)))


(defn create-group [req]
  (let [ok? (and (spec/valid? :kixi.heimdall.schema/group-params (:params req)) (spec/valid? :kixi.heimdall.schema/user (:user req)))]
    (if ok?
      (do (comms/send-event! (:communications (:components req)) :kixi.heimdall/group-created "1.0.0" (merge {:group (:params req)} {:user (:user req)}))
          {:status 201 :body "Group successfully created"})
      {:status 401 :body "Please provide valid parameters (name for the group), and make sure you are authenticated"})))

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

(defn- parse-header
  [request token-name]
  (some->> (get (:headers request) "authorization")
           (re-find (re-pattern (str "^" token-name " (.+)$")))
           (second)))

(defn wrap-authentication
  [handler]
  (fn [request]
    (let [token (parse-header request "Token")
          unsigned (when token (service/unsign-token (:auth-conf request) token))]
      (if (and token unsigned)
        (handler (assoc request :user unsigned))
        (do (log/warn "Unauthenticated") {:status 401 :body "Unauthenticated"})))))


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

(defroutes public-routes
  (GET "/" [] "Hello World")
  (POST "/user" [] new-user)
  (POST "/create-auth-token" [] auth-token)
  (POST "/refresh-auth-token" [] refresh-auth-token)
  (POST "/invalidate-refresh-token" [] invalidate-refresh-token))

(defroutes secured-routes
  (POST "/create-group" [] create-group))

(defroutes app-routes
  public-routes
  (wrap-routes secured-routes
               wrap-authentication)
  (route/not-found "Not Found"))

(defn wrap-catch-exceptions [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t
           (do (log/error t)
               {:status 500 :body {:message "Something went wrong ..."}})))))

(def app
  (-> app-routes
      wrap-escape-html
      wrap-record-metric
      wrap-catch-exceptions
      wrap-keyword-params
      wrap-json-params
      wrap-json-response))
