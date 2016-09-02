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
            [kixi.heimdall.group :as group]
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
  (key-fn (or (util/file-exists? path)
              (io/resource path))))

(defn- private-key [auth-conf]
  (absolute-or-resource-key #(ks/private-key % (:passphrase auth-conf)) (:privkey auth-conf)))

(defn- public-key [auth-conf]
  (absolute-or-resource-key ks/public-key (:pubkey auth-conf)))

(defn- make-auth-token [user auth-conf]
  (let [exp (c/to-long (t/plus (t/now) (t/minutes 30)))]
    (jwt/sign user
              (private-key auth-conf)
              {:alg :rs256 :exp exp})))

(defn unsign-token [auth-conf token]
  (and token
       (try (jwt/unsign token (public-key auth-conf)
                        {:alg :rs256 :now (c/to-long (t/now))})
            (catch Exception e
              (log/debug (format "Unsign refresh token failed due to %s"
                                 (.getMessage e)))))))

(defn make-refresh-token [issued-at-time auth-conf user]
  (let [exp (c/to-long (t/plus (t/now) (t/days 30)))]
    (jwt/sign {:user-id (:id user)}
              (private-key auth-conf)
              {:alg :rs256 :iat issued-at-time :exp exp})))

(defn make-token-pair! [session auth-conf user]
  (if user (let [issued-at-time (c/to-long (t/now))
                 refresh-token (make-refresh-token issued-at-time auth-conf user)
                 auth-token (make-auth-token user auth-conf)]
             (when (and refresh-token auth-conf)
               (refresh-token/add! session {:refresh-token refresh-token
                                            :issued issued-at-time
                                            :user-id (:id user)})
               {:token-pair {:auth-token auth-token
                             :refresh-token refresh-token}}))
      (log/debug "User credentials missing")))

(defn create-auth-token [session auth-conf credentials]
  (let [[ok? res] (user/auth session credentials)
        token-pair (make-token-pair! session auth-conf (:user res))]
    (if (and ok? token-pair)
      (success token-pair)
      (fail "Invalid username or password"))))

(defn refresh-auth-token [session auth-conf refresh-token]
  (if-let [unsigned (unsign-token auth-conf refresh-token)]
    (let [user-uuid (java.util.UUID/fromString (:user-id unsigned))
          refresh-token-data (refresh-token/find-by-user-and-issued session
                                                                    user-uuid
                                                                    (:iat unsigned))
          user (user/find-by-id session user-uuid)
          new-token-pair (make-token-pair! session auth-conf user)]
      (if (and (:valid refresh-token-data) new-token-pair)
        (do
          (refresh-token/invalidate! session (:id refresh-token-data))
          (success new-token-pair))
        (fail "Refresh token revoked/deleted or new refresh token already created")))
    (fail "Invalid or expired refresh token provided")))

(defn invalidate-refresh-token [session auth-conf refresh-token]
  (if-let [unsigned (unsign-token auth-conf refresh-token)]
    (let [user-uuid (java.util.UUID/fromString (:user-id unsigned))
          refresh-token-data (refresh-token/find-by-user-and-issued session
                                                                    user-uuid
                                                                    (:iat unsigned))]
      (refresh-token/invalidate! session (:id refresh-token-data))
      (success {:message "Invalidated successfully"}))
    (fail "Invalid or expired refresh token provided")))

(defn create-group
  [session auth-conf auth-token group-info]
  (when auth-token
    (try (unsign-token auth-conf auth-token)
         (catch Exception e (log/debug (format "Unsign of the token failed due to %s"
                                               (.getMessage e)))))
    (let [{:keys [group-name user-id]} group-info
          group-id (:group-id
                    (group/create! session {:name group-name :owner_id user-id}))]
      (user/add-group-id session user-id group-id))))
