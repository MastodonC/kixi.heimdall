(ns kixi.heimdall.service
  (:require [taoensso.timbre :as log]
            [buddy.sign.jwt :as jwt]
            [buddy.sign.util :as sign]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.java.io :as io]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.refresh-token :as refresh-token]
            [kixi.heimdall.util :as util]))

(defn fail
  [message]
  [false {:message message}])

(defn success
  [result]
  [true result])

(defn- absolute-or-resource-key
  [key-fn path]
  (-> (if-let [key (util/file-exists? path)]
        key
        (io/resource path))
      (key-fn)))

(defn- private-key [auth-conf]
  (absolute-or-resource-key #(ks/private-key % (:passphrase auth-conf)) (:privkey auth-conf)))

(defn- public-key [auth-conf]
  (absolute-or-resource-key ks/public-key (:pubkey auth-conf)))

(defn- make-auth-token [user auth-conf]
  (let [exp (-> (t/plus (t/now) (t/minutes 30)) (c/to-long))]
    (jwt/sign user
              (pkey auth-conf)
              {:alg :rs256 :exp exp})))

(defn unsign-token [auth-conf token]
  (and token
       (try (jwt/unsign token (public-key auth-conf)
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
      (success (make-token-pair! session auth-conf (:user res)))
      (fail "Invalid username or password"))))

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
          (success  (make-token-pair! session auth-conf user)))
        (fail "Refresh token revoked/deleted or new refresh token already created")))
    (fail "Invalid or expired refresh token provided")))

(defn invalidate-refresh-token [session auth-conf refresh-token]
  (if-let [unsigned (unsign-token auth-conf refresh-token)]
    (let [user-uuid (java.util.UUID/fromString (:user-id unsigned))
          refresh-token-data (refresh-token/find-by-user-and-issued session
                                                                    user-uuid
                                                                    (:iat unsigned))]
      (do
        (refresh-token/invalidate! session (:id refresh-token-data))
        (success {:message "Invalidated successfully"})))
    (fail "Invalid or expired refresh token provided")))
