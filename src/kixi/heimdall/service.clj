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
            [kixi.heimdall.util :as util]
            [clojure.spec :as spec]
            [kixi.comms :refer [Communications] :as comms]))


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

(defn- token-data
  [session user]
  (let [groups (member/retrieve-groups-ids session (:id user))
        self-group (group/find-user-group session (:id user))]
    (-> (select-keys user [:username
                           :id
                           :name
                           :created])
        (merge {:user-groups groups
                :self-group (:id self-group)}))))

(defn create-auth-token [session communications auth-conf credentials]
  (let [[ok? res] (user/auth session credentials)]
    (if ok?
      (let [token-info (token-data session (:user res))]
        (if-let [token-pair (make-token-pair! session auth-conf token-info)]
          (do  (comms/send-event! communications :kixi.heimdall/user-logged-in "1.0.0" (select-keys credentials [:username]))
               (success token-pair))
          (fail "Invalid username or password")))
      (fail "Invalid username or password"))))

(defn refresh-auth-token [session auth-conf refresh-token]
  (if-let [unsigned (unsign-token auth-conf refresh-token)]
    (let [user-uuid (java.util.UUID/fromString (:user-id unsigned))
          refresh-token-data (refresh-token/find-by-user-and-issued session
                                                                    user-uuid
                                                                    (:iat unsigned))
          user (user/find-by-id session user-uuid)
          token-info (when user (token-data session user))
          new-token-pair (make-token-pair! session auth-conf token-info)]
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

(defn create-group-event
  [session communications {:keys [group user-id] :as input}]
  (let [group-ok? (spec/valid? :kixi.heimdall.schema/group-params group)
        user-ok? (spec/valid? :kixi.heimdall.schema/id user-id)]
    (if (and user-ok? group-ok?)
      (and (comms/send-event! communications :kixi.heimdall/group-created "1.0.0" input) true)
      false)))

(defn- create-group
  [session {:keys [group user-id]}]
  (let [user-uuid  (java.util.UUID/fromString user-id)
        group-id (:group-id (group/add! session {:name (:group-name group)
                                                 :user-id user-uuid}))]
    (member/add! session user-uuid group-id)
    {:group-id group-id}))

(defn add-self-group
  [session user]
  (let [self-group (group/add! session {:name (:name user)
                                        :user-id (:id user)
                                        :group-type "user"})]
    (member/add! session (:id user) (:group-id self-group))
    {:group-id self-group}))

(defn new-user
  [session communications params]
  (let [credentials (select-keys params [:username :password :name])
        [ok? res] (user/validate credentials)]
    (if ok?
      (if (user/find-by-username session {:username (:username credentials)})
        (fail "There is already a user with this username.")
        (let [added-user (user/add! session credentials)
              self-group (add-self-group session added-user)]

          (log/warn "user id created for " (:username credentials) " : " (:id added-user)) ;; needed for REPL admin
          (log/warn "self-group created: " (:group-id self-group))
          (comms/send-event! communications :kixi.heimdall/user-created "1.0.0" (select-keys params [:username]))
          (success {:message "User successfully created!"})))
      (fail (str "Please match the required format: " res)))))

(defn- add-member
  [session {:keys [user-id group-id]}]
  (let [user-id  (java.util.UUID/fromString user-id)
        group-id (java.util.UUID/fromString group-id)]
    (member/add! session user-id group-id)))

(defn add-member-event
  [session communications user-id group-id]
  (let [user-ok? (and (spec/valid? :kixi.heimdall.schema/id user-id)
                      (user/find-by-id session (java.util.UUID/fromString user-id)))
        group-ok? (and (spec/valid? :kixi.heimdall.schema/id group-id)
                       (group/find-by-id session (java.util.UUID/fromString group-id)))]
    (if (and user-ok? group-ok?)
      (and (comms/send-event! communications
                              :kixi.heimdall/member-added
                              "1.0.0"
                              {:user-id user-id
                               :group-id group-id}) true)
      false)))

(defn remove-member
  [session {:keys [user-id group-id]}]
  (let [user-id  (java.util.UUID/fromString user-id)
        group-id (java.util.UUID/fromString group-id)]
    (member/remove-member session user-id group-id)))

(defn remove-member-event
  [session communications user-id group-id]
  (let [user-ok? (and (spec/valid? :kixi.heimdall.schema/id user-id)
                      (user/find-by-id session (java.util.UUID/fromString user-id)))
        group-ok? (and (spec/valid? :kixi.heimdall.schema/id group-id)
                       (group/find-by-id session (java.util.UUID/fromString group-id)))]
    (if (and user-ok? group-ok?)
      (and (comms/send-event! communications
                              :kixi.heimdall/member-removed
                              "1.0.0"
                              {:user-id user-id
                               :group-id group-id}) true)
      false)))

(defn update-group
  [session {:keys [group-id name]}]
  (group/update! session group-id {:name name}))

(defn update-group-event
  [session communications group-id new-group-name]
  (let [group-ok? (and (spec/valid? :kixi.heimdall.schema/id group-id)
                       (group/find-by-id session (java.util.UUID/fromString group-id)))
        name-ok? (spec/valid? :kixi.heimdall.schema/group-name new-group-name)]
    (if (and group-ok? name-ok?)
      (and (comms/send-event! communications
                              :kixi.heimdall/group-updated
                              "1.0.0"
                              {:group-id group-id
                               :name new-group-name})
           true)
      false)))

(defn all-groups
  [session]
  (map #(clojure.set/rename-keys %
                                 {:id :kixi.group/id
                                  :name :kixi.group/name
                                  :group-type :kixi.group/type
                                  :created-by :kixi.group/created-by
                                  :created :kixi.group/created})
       (group/all session)))

(defn vec-if-not
  [value]
  (if (coll? value)
    value
    (vector value)))

(defn toUUID
  [id]
  (try (java.util.UUID/fromString id)
       (catch IllegalArgumentException _ nil)))

(defn users
  [session user-ids]
  (keep #(when-let [raw-user (user/find-by-id session %)]
           (clojure.set/rename-keys  (select-keys raw-user
                                                  [:id :name :created_by :created :username])
                                     {:id :kixi.user/id
                                      :name :kixi.user/name
                                      :created-by :kixi.user/created-by
                                      :created :kixi.user/created
                                      :username :kixi.user/username}))
        (keep toUUID (vec-if-not user-ids))))

(defn groups
  [session group-ids]
  (keep #(when-let [raw-group (group/find-by-id session %)]
           (clojure.set/rename-keys  (select-keys raw-group
                                                  [:id :name :created-by :created :group-type])
                                     {:id :kixi.group/id
                                      :name :kixi.group/name
                                      :group-type :kixi.group/type
                                      :created-by :kixi.group/created-by
                                      :created :kixi.group/created}))
        (keep toUUID (vec-if-not group-ids))))
