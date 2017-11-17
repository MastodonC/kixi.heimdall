(ns kixi.heimdall.service
  (:require [taoensso.timbre :as log]
            [buddy.sign.jwt :as jwt]
            [buddy.sign.util :as sign]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.member :as member]
            [kixi.heimdall.invites :as invites]
            [kixi.heimdall.password-resets :as password-resets]
            [kixi.heimdall.refresh-token :as refresh-token]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.email :as email]
            [kixi.heimdall.schema :as schema]
            [kixi.heimdall.components.database :as database]
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
          (do  (comms/send-event! communications
                                  :kixi.heimdall/user-logged-in
                                  "1.0.0"
                                  (select-keys credentials [:username])
                                  {:kixi.comms.event/partition-key (get-in res [:user :id])})
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
  [db communications {:keys [group-name group-id user-id] :as input}]
  (let [group-ok? (and (s/valid? ::schema/group-params input)
                       (not (group/find-by-name db group-name)))
        user-ok? (s/valid? ::schema/id user-id)
        input-dated (assoc input
                           :created (str (util/db-now)))]
    (if (and user-ok? group-ok?)
      (do (comms/send-event! communications
                             :kixi.heimdall/group-created
                             "2.0.0"
                             input-dated
                             {:kixi.comms.event/partition-key group-id}) true)
      (do (comms/send-event! communications
                             :kixi.heimdall/create-group-failed
                             "2.0.0"
                             {:user-valid user-ok?
                              :group-valid group-ok?
                              :group-name group-name
                              :group-id group-id
                              :user-id user-id}
                             {:kixi.comms.event/partition-key group-id}) false))))

(defn- create-group
  [db group]
  (group/add! db group)
  (member/add! db {:user-id (:user-id group)
                   :group-id (:group-id group)}))

(defn add-self-group
  [db user]
  (let [self-group (group/add! db {:group-name (:name user)
                                   :user-id (:id user)
                                   :group-id (:group-id user)
                                   :group-type "user"
                                   :created (:created user)})]
    (member/add! db {:user-id (:id user)
                     :group-id (:group-id user)})
    {:group-id self-group}))

(defn send-user-created-event!
  [communications user]
  (comms/send-event! communications
                     :kixi.heimdall/user-created
                     "2.0.0"
                     (dissoc user :password)
                     {:kixi.comms.event/partition-key (:id user)}))

(defn- create-user
  "Event consumer: we will attempt to add a user - it may already exist."
  [db user]
  (try
    (->> user
         (user/add! db)
         (add-self-group db))
    (catch com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException e
      (comment "This should only work on event replay, when the condition will succeed.")))
  nil)

(s/fdef signup-user!
        :args (s/cat :db (s/keys)
                     :communications (s/keys)
                     :params (s/keys :req-un [::schema/username
                                              ::schema/password])))

(defn signup-user!
  [db communications params]
  {:post [(vector? %)
          (boolean? (first %))
          (map? (second %))
          (not-empty (:message (second %)))
          (string? (:message (second %)))]}
  (let [user' (-> params
                  (select-keys [:username :password])
                  (update :username clojure.string/lower-case))
        [ok? res] (user/validate user')
        stored-user (user/find-by-username db {:username (:username user')})
        user (merge user'
                    stored-user)]
    (cond
      (not (s/valid? ::schema/login user)) (fail (str "Please match the required format: " res))
      (not stored-user) (fail "Pre-signup user not found")
      (not (:pre-signup user)) (fail "User is not in pre-sign up state")
      (not (invites/consume! db (:invite-code params) (:username user))) (fail "The invite code was invalid.")
      :else (let [user' (assoc user
                               :signed-up (str (util/db-now))
                               :pre-signup false)
                  added-user (user/signed-up! db user')]
              (send-user-created-event! communications added-user)
              (success {:message "User successfully created"})))))

(defn create-user-data
  [user]
  (assoc user
         :id (str (java.util.UUID/randomUUID))
         :created (str (util/db-now))
         :pre-signup true
         :group-id (str (java.util.UUID/randomUUID))))

(s/fdef user-invite-event
        :args (s/cat :stored-user (s/nilable ::schema/stored-user)
                     :invitee (s/keys :req-un [::schema/username ::schema/name]))
        :fn (fn [{:keys [args ret]}]
              (let [{:keys [stored-user invitee]} args
                    [_ event] ret
                    tested-keys [:id :group-id :created]]
                (cond
                  (:pre-signup stored-user)
                  (= (select-keys stored-user
                                  tested-keys)
                     (select-keys (get-in event [:kixi.comms.event/payload :user])
                                  tested-keys))
                  :else true)))

        :ret (s/or :created ::invites/create-invite-event
                   :failed ::invites/failed-event))

(defn user-invite-event
  [stored-user {:keys [username name] :as user'}]
  (let [user (-> user'
                 (select-keys [:username :name])
                 (update :username clojure.string/lower-case)
                 create-user-data
                 (merge (select-keys stored-user [:id :group-id :created])))]
    (cond
      (not (s/valid? ::schema/user-invite user)) (invites/failed-event username :invalid-data
                                                                       (s/explain-data ::schema/user-invite user))
      (and stored-user
           (not (:pre-signup stored-user))) (invites/failed-event username :user-signedup)
      :else (invites/create-invite-event user))))

(defn user-invite-event-payload->kixi-user
  [{{:keys [id group-id]}
    :user}]
  {:kixi.user/id id
   :kixi.user/groups [group-id]})

(defn invite-user!
  [db communications {:keys [username name] :as user}]
  (let [stored-user (user/find-by-username db {:username username})
        {:keys [kixi.comms.event/key
                kixi.comms.event/version
                kixi.comms.event/payload] :as event} (user-invite-event stored-user user)]
    (comms/send-event! communications
                       key
                       version
                       payload
                       {:kixi.comms.event/partition-key username})
    (if (= :kixi.heimdall/invite-created key)
      (do (email/send-email! :user-invite communications {:url (:url payload)
                                                          :username username
                                                          :user (user-invite-event-payload->kixi-user payload)})
          (success event))
      (fail event))))

(defn- add-member
  [db {:keys [user-id group-id] :as member}]
  (member/add! db member))

(defn add-member-event
  [db communications user-id group-id]
  (let [user-ok? (and (s/valid? ::schema/id user-id)
                      (user/find-by-id db user-id))
        group-ok? (and (s/valid? ::schema/id group-id)
                       (group/find-by-id db group-id))]
    (if (and user-ok? group-ok?)
      (do (comms/send-event! communications
                             :kixi.heimdall/member-added
                             "1.0.0"
                             {:user-id user-id
                              :group-id group-id}
                             {:kixi.comms.event/partition-key group-id}) true)
      (do (comms/send-event! communications
                             :kixi.heimdall/member-added-failed
                             "1.0.0"
                             {:user-valid user-ok?
                              :group-valid group-ok?
                              :group-id group-id
                              :user-id user-id}
                             {:kixi.comms.event/partition-key group-id}) false))))

(defn remove-member-event
  [db communications user-id group-id]
  (let [user-ok? (and (s/valid? ::schema/id user-id)
                      (user/find-by-id db user-id))
        group-ok? (and (s/valid? ::schema/id group-id)
                       (group/find-by-id db group-id))]
    (if (and user-ok? group-ok?)
      (do (comms/send-event! communications
                             :kixi.heimdall/member-removed
                             "1.0.0"
                             {:user-id user-id
                              :group-id group-id}
                             {:kixi.comms.event/partition-key group-id}) true)
      (do (comms/send-event! communications
                             :kixi.heimdall/member-removed-failed
                             "1.0.0"
                             {:user-valid user-ok?
                              :group-valid group-ok?
                              :group-id group-id
                              :user-id user-id}
                             {:kixi.comms.event/partition-key group-id}) false))))

(defn remove-member
  [db {:keys [user-id group-id] :as member}]
  (member/remove-member db member))

(defn update-group
  [db {:keys [group-id name]}]
  (group/update! db group-id {:name name}))

(defn update-group-event
  [db communications group-id new-group-name]
  (let [group-ok? (and (s/valid? ::schema/id group-id)
                       (group/find-by-id db group-id))
        name-ok? (s/valid? ::schema/group-name new-group-name)]
    (if (and group-ok? name-ok?)
      (and (comms/send-event! communications
                              :kixi.heimdall/group-updated
                              "1.0.0"
                              {:group-id group-id
                               :name new-group-name}
                              {:kixi.comms.event/partition-key group-id})
           true)
      false)))

(defn all-groups
  [db dex cnt sort-order]
  (let [groups (group/all db)]
    [(count groups)
     (->> groups
          (sort-by :group-name)
          (#(if (= "desc" sort-order) (reverse %) %))
          (drop dex)
          (map #(clojure.set/rename-keys %
                                         {:id         :kixi.group/id
                                          :group-name :kixi.group/name
                                          :group-type :kixi.group/type
                                          :created-by :kixi.group/created-by
                                          :created    :kixi.group/created}))
          (take cnt))]))

(defn vec-if-not
  [value]
  (if (coll? value)
    value
    (vector value)))

(defn users
  [db user-ids]
  (keep #(when-let [raw-user (user/find-by-id db %)]
           (clojure.set/rename-keys (select-keys raw-user
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

(defn save-invite
  "Create pre-signup user and store invite"
  [db {:keys [invite-code user]}]
  (create-user db user)
  (invites/save! db invite-code (:username user)))

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
  (let [username (clojure.string/lower-case username)
        [ok? res] (user/validate {:username username :password password})]
    (if ok?
      (let [result (password-resets/consume! db reset-code username)]
        (if result
          (let [{:keys [kixi.comms.event/key
                        kixi.comms.event/version
                        kixi.comms.event/payload]
                 :as event} (password-resets/create-reset-completed-event username)]
            (user/change-password! db username password)
            (comms/send-event! communications
                               key
                               version
                               payload
                               (select-keys event
                                            [:kixi.comms.event/partition-key]))
            (success {:message "Password was reset"}))
          (fail (str "No matching reset code - requested for " username))))
      (fail (str "Please match the required format: " res)))))
