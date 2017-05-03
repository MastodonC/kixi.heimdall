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
            [kixi.heimdall.invites :as invites]
            [kixi.heimdall.password-resets :as password-resets]
            [kixi.heimdall.refresh-token :as refresh-token]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.email :as email]
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

(defn make-token-pair! [db auth-conf user]
  (if user (let [issued-at-time (c/to-long (t/now))
                 refresh-token (make-refresh-token issued-at-time auth-conf user)
                 auth-token (make-auth-token user auth-conf)]
             (when (and refresh-token auth-conf)
               (refresh-token/add! db {:refresh-token refresh-token
                                       :issued issued-at-time
                                       :user-id (:id user)})
               {:token-pair {:auth-token auth-token
                             :refresh-token refresh-token}}))
      (log/debug "User credentials missing")))

(defn- token-data
  [db user]
  (let [groups (member/retrieve-groups-ids db (:id user))
        self-group (group/find-user-group db (:id user))]
    (-> (select-keys user [:username
                           :id
                           :name
                           :created])
        (merge {:user-groups groups
                :self-group (:id self-group)}))))

(defn create-auth-token [db communications auth-conf credentials]
  (let [[ok? res] (user/auth db credentials)]
    (if ok?
      (let [token-info (token-data db (:user res))]
        (if-let [token-pair (make-token-pair! db auth-conf token-info)]
          (do  (comms/send-event! communications :kixi.heimdall/user-logged-in "1.0.0" (select-keys credentials [:username]))
               (success token-pair))
          (fail "Invalid username or password")))
      (fail "Invalid username or password"))))

(defn refresh-auth-token [db auth-conf refresh-token]
  (if-let [unsigned (unsign-token auth-conf refresh-token)]
    (let [user-uuid (:user-id unsigned)
          refresh-token-data (refresh-token/find-by-user-and-issued db
                                                                    user-uuid
                                                                    (:iat unsigned))
          user (user/find-by-id db user-uuid)
          token-info (when user (token-data db user))
          new-token-pair (make-token-pair! db auth-conf token-info)]
      (if (and (:valid refresh-token-data) new-token-pair)
        (do
          (refresh-token/invalidate! db (:id refresh-token-data))
          (success new-token-pair))
        (fail "Refresh token revoked/deleted or new refresh token already created")))
    (fail "Invalid or expired refresh token provided")))

(defn invalidate-refresh-token [db auth-conf refresh-token]
  (if-let [unsigned (unsign-token auth-conf refresh-token)]
    (let [user-uuid (:user-id unsigned)
          refresh-token-data (refresh-token/find-by-user-and-issued db
                                                                    user-uuid
                                                                    (:iat unsigned))]
      (refresh-token/invalidate! db (:id refresh-token-data))
      (success {:message "Invalidated successfully"}))
    (fail "Invalid or expired refresh token provided")))

(defn create-group-event
  [db communications {:keys [group user-id] :as input}]
  (let [group-ok? (spec/valid? :kixi.heimdall.schema/group-params group)
        user-ok? (spec/valid? :kixi.heimdall.schema/id user-id)]
    (if (and user-ok? group-ok?)
      (and (comms/send-event! communications :kixi.heimdall/group-created "1.0.0" input) true)
      false)))

(defn- create-group
  [db {:keys [group user-id]}]
  (let [user-uuid user-id
        group-id (:group-id (group/add! db {:name (:group-name group)
                                            :user-id user-uuid}))]
    (member/add! db user-uuid group-id)
    {:group-id group-id}))

(defn add-self-group
  [db user]
  (let [self-group (group/add! db {:name (:name user)
                                   :user-id (:id user)
                                   :group-type "user"})]
    (member/add! db (:id user) (:group-id self-group))
    {:group-id self-group}))

(defn new-user
  [db communications params]
  (let [credentials (select-keys params [:username :password :name])
        [ok? res] (user/validate credentials)]
    (if ok?
      (if (user/find-by-username db {:username (:username credentials)})
        (fail "There is already a user with this username.")
        (let [added-user (user/add! db credentials)
              self-group (add-self-group db added-user)]

          (log/warn "user id created for " (:username credentials) " : " (:id added-user)) ;; needed for REPL admin
          (log/warn "self-group created: " (:group-id self-group))
          (comms/send-event! communications :kixi.heimdall/user-created "1.0.0" (select-keys params [:username]))
          (success {:message "User successfully created!"})))
      (fail (str "Please match the required format: " res)))))

(defn new-user-with-invite
  [db communications params]
  (let [credentials (select-keys params [:username :password :name])
        [ok? res] (user/validate credentials)]
    (if ok?
      (if (user/find-by-username db {:username (:username credentials)})
        (fail "There is already a user with this username.")
        (if-not (invites/consume! db (:invite-code params) (:username credentials))
          (fail "The invite code was invalid.")
          (let [added-user (user/add! db credentials)
                self-group (add-self-group db added-user)]

            (log/warn "user id created for " (:username credentials) " : " (:id added-user)) ;; needed for REPL admin
            (log/warn "self-group created: " (:group-id self-group))
            (comms/send-event! communications :kixi.heimdall/user-created "1.0.0" (select-keys params [:username]))
            (success {:message "User successfully created!"}))))
      (fail (str "Please match the required format: " res)))))

(defn- add-member
  [db {:keys [user-id group-id]}]
  (member/add! db user-id group-id))

(defn add-member-event
  [db communications user-id group-id]
  (let [user-ok? (and (spec/valid? :kixi.heimdall.schema/id user-id)
                      (user/find-by-id db user-id))
        group-ok? (and (spec/valid? :kixi.heimdall.schema/id group-id)
                       (group/find-by-id db group-id))]
    (if (and user-ok? group-ok?)
      (and (comms/send-event! communications
                              :kixi.heimdall/member-added
                              "1.0.0"
                              {:user-id user-id
                               :group-id group-id}) true)
      false)))

(defn remove-member
  [db {:keys [user-id group-id]}]
  (member/remove-member db user-id group-id))

(defn remove-member-event
  [db communications user-id group-id]
  (let [user-ok? (and (spec/valid? :kixi.heimdall.schema/id user-id)
                      (user/find-by-id db user-id))
        group-ok? (and (spec/valid? :kixi.heimdall.schema/id group-id)
                       (group/find-by-id db group-id))]
    (if (and user-ok? group-ok?)
      (and (comms/send-event! communications
                              :kixi.heimdall/member-removed
                              "1.0.0"
                              {:user-id user-id
                               :group-id group-id}) true)
      false)))

(defn update-group
  [db {:keys [group-id name]}]
  (group/update! db group-id {:name name}))

(defn update-group-event
  [db communications group-id new-group-name]
  (let [group-ok? (and (spec/valid? :kixi.heimdall.schema/id group-id)
                       (group/find-by-id db group-id))
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
  [db]
  (map #(clojure.set/rename-keys %
                                 {:id :kixi.group/id
                                  :group-name :kixi.group/name
                                  :group-type :kixi.group/type
                                  :created-by :kixi.group/created-by
                                  :created :kixi.group/created})
       (group/all db)))

(defn vec-if-not
  [value]
  (if (coll? value)
    value
    (vector value)))

(defn users
  [db user-ids]
  (keep #(when-let [raw-user (user/find-by-id db %)]
           (clojure.set/rename-keys  (select-keys raw-user
                                                  [:id :name :created_by :created :username])
                                     {:id :kixi.user/id
                                      :name :kixi.user/name
                                      :created-by :kixi.user/created-by
                                      :created :kixi.user/created
                                      :username :kixi.user/username}))
        (vec-if-not user-ids)))

(defn groups
  [db group-ids]
  (keep #(when-let [raw-group (group/find-by-id db %)]
           (clojure.set/rename-keys  (select-keys raw-group
                                                  [:id :group-name :created-by :created :group-type])
                                     {:id :kixi.group/id
                                      :group-name :kixi.group/name
                                      :group-type :kixi.group/type
                                      :created-by :kixi.group/created-by
                                      :created :kixi.group/created}))
        (vec-if-not group-ids)))

(defn invite-user!
  "Use this to invite a new user to the system."
  [communications username]
  (let [{:keys [kixi.comms.event/key
                kixi.comms.event/version
                kixi.comms.event/payload]} (invites/create-invite-event username)]
    (comms/send-event! communications key version payload)
    payload))

(defn save-invite
  "Persist details of an invite"
  [db {:keys [invite-code username]}]
  (invites/save! db invite-code username))

(defn reset-password!
  [db communications {:keys [username]}]
  (let [user (user/find-by-username db {:username username})
        event-fn (if user
                   (partial password-resets/create-reset-event user)
                   (partial password-resets/reject-reset-event "No matching user found"))
        result (event-fn username)]
    result))

(defn save-password-reset-request
  "Persist details of a reset request if the user exists"
  [db communications {:keys [reset-code user url] :as payload}]
  (password-resets/save! db reset-code user)
  (email/send-email! :password-reset-request communications {:user user :url url}))

(defn complete-password-reset!
  [db communications username password reset-code]
  (let [[ok? res] (user/validate {:username username :password password})]
    (if ok?
      (let [result (password-resets/consume! db reset-code username)]
        (if result
          (let [{:keys [kixi.comms.event/key
                        kixi.comms.event/version
                        kixi.comms.event/payload]} (password-resets/create-reset-completed-event username)]
            (user/change-password! db username password)
            (comms/send-event! communications key version payload)
            (success {:message "Password was reset"}))
          (fail (str "No matching reset code - requested for " username))))
      (fail (str "Please match the required format: " res)))))
