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
            [kixi.heimdall.member :as member]
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

(defn public-key [auth-conf]
  (absolute-or-resource-key ks/public-key (:pubkey auth-conf)))

(defn make-auth-token [user auth-conf]
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

(defn- get-groups-for-user [session user-id]
  (let [groups-colls (member/retrieve-groups-ids session user-id)
        groups-ids (map :group-id groups-colls)]
    {:groups groups-ids}))

(defn create-auth-token [session auth-conf credentials]
  (let [[ok? res] (user/auth session credentials)
        groups (get-groups-for-user session (:id (:user res)))
        user (merge (:user res) {:user-groups groups})]
    (if (and ok? user)
      (if-let [token-pair (make-token-pair! session auth-conf user)]
        (success token-pair)
        (fail "Invalid username or password"))
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
  [session auth-conf {:keys [id] :as user} {:keys [group-name]}]
  (when user
    (let [group-id (:group-id (group/create! session {:name group-name}))]
      (member/add-user-to-group session (java.util.UUID/fromString id) group-id "owner")
      [true "Group successfully created"])))

(defn new-user
  [session params]
  (let [credentials (select-keys params [:username :password])
        [ok? res] (user/validate credentials)]
    (if ok?
      (if (user/find-by-username session {:username (:username credentials)})
        (fail "There is already a user with this username.")
        (do
          (user/add! session credentials)
          (success {:message "User successfully created!"})))
      (fail (str "Please match the required format: " res)))))
