(ns kixi.heimdall.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [kixi.heimdall.application :as app]
            [taoensso.timbre :as log]
            [kixi.heimdall.components.database :as db]
            [buddy.sign.jwt :as jwt]
            [buddy.sign.util :as sign]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.java.io :as io]
            [kixi.heimdall.user :as user]
            [clojure.edn :as edn]
            [environ.core :refer [env]]
            [kixi.heimdall.refresh-token :as refresh-token]))

(defn get-config
  [f]
  (edn/read-string (slurp f)))

(def auth-config
  (let [f (io/file (System/getProperty "user.home")
                   ".heimdall.auth-conf.edn")]
    (try (get-config f)
         (catch java.io.FileNotFoundException e
           (if-let [env-file (:auth-conf env)]
             (get-config (io/resource env-file))
             (throw e))))))

(defn- pkey [auth-conf]
  (ks/private-key
   (io/resource (:privkey auth-conf))
   (:passphrase auth-conf)))

(defn- make-auth-token [user auth-conf]
  (let [exp (-> (t/plus (t/now) (t/minutes 30)) (c/to-long))]
    (jwt/sign user
              (pkey auth-conf)
              {:alg :rs256 :exp exp})))

(defn unsign-token [auth-conf token]
  (and token
       (try (jwt/unsign token (ks/public-key (io/resource (:pubkey auth-conf)))
                        {:alg :rs256 :now (c/to-long (t/now))})
            (catch clojure.lang.ExceptionInfo e
              (do (log/debug "Unsign refresh token failed")
                  nil)))))

(defn make-refresh-token [issued-at-time auth-conf user]
  (let [exp (-> (t/plus (t/now) (t/days 30)) (c/to-long))]
    (jwt/sign {:user-id (:id user)}
              (pkey auth-conf)
              {:alg :rs256 :iat issued-at-time :exp exp})))

(defn make-token-pair! [session auth-conf user]
  (let [issued-at-time (c/to-long (t/now))
        refresh-token (make-refresh-token issued-at-time auth-conf user)]
    (refresh-token/add! session {:refresh-token refresh-token
                                 :issued issued-at-time
                                 :user-id (:id user)})
    {:token-pair {:auth-token (make-auth-token user auth-conf)
                  :refresh-token refresh-token}}))

(defn create-auth-token [session auth-conf credentials]
  (let [[ok? res] (user/auth session credentials)]
    (if ok?
      [true (make-token-pair! session auth-conf (:user res))]
      [false res])))

(defn auth-token [req]
  (let [[ok? res] (create-auth-token (:cassandra-session (:components req))
                                     (:auth-conf req)
                                     (:params req))]
    (if ok?
      {:status 201 :body res}
      {:status 401 :body res})))


(defn refresh-auth-token [session auth-conf refresh-token]
  (if-let [unsigned (unsign-token auth-conf refresh-token)]
    (let [user-uuid (java.util.UUID/fromString (:user-id unsigned))
          refresh-token-data (refresh-token/find-by-user-and-issued session
                                                                    user-uuid
                                                                    (:iat unsigned))
          user (user/find-by-id session user-uuid)]
      (if (:valid refresh-token-data)
        (do
          (refresh-token/invalidate! session (:id refresh-token-data))
          [true (make-token-pair! session auth-conf user)])
        [false {:message "Refresh token revoked/deleted or new refresh token already created"}]))
    [false {:message "Invalid or expired refresh token provided"}]))



(defn refresh-auth-token-route [req]
  (let [refresh-token (-> req :params :refresh-token)
        [ok? res] (refresh-auth-token (:cassandra-session (:components req))
                                      (:auth-conf req)
                                      refresh-token)]
    (if ok?
      {:status 201 :body res}
      {:status 401 :body res})))

(defn invalidate-refresh-token [session auth-conf refresh-token]
  (if-let [unsigned (unsign-token auth-conf refresh-token)]
    (let [user-uuid (java.util.UUID/fromString (:user-id unsigned))
          refresh-token-data (refresh-token/find-by-user-and-issued session
                                                                    user-uuid
                                                                    (:iat unsigned))]
      (do
        (refresh-token/invalidate! session (:id refresh-token-data))
        [true {:message "Invalidated successfully"}]))
    [false {:message "Invalid or expired refresh token provided"}]))

(defn invalidate-refresh-token-route [req]
  (let [refresh-token (-> req :params :refresh-token)
        [ok? res] (invalidate-refresh-token (:cassandra-session (:components req))
                                            (:auth-conf req)
                                            refresh-token)]
    (if ok?
      {:status 200 :body res}
      {:status 401 :body res})))

(defn wrap-config [handler]
  (fn [req]
    (handler (assoc req :auth-conf auth-config))))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (POST "/create-auth-token" [] auth-token)
  (POST "/refresh-auth-token" [] refresh-auth-token-route)
  (POST "/invalidate-refresh-token" [] invalidate-refresh-token-route)
  (route/not-found "Not Found"))

(defn wrap-catch-exceptions [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t (log/error t)))))

(def app
  (-> app-routes
      (wrap-config)
      (wrap-catch-exceptions)
      wrap-keyword-params
      wrap-json-params
      wrap-json-response))
