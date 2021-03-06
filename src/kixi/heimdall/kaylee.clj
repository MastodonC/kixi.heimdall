(ns kixi.heimdall.kaylee
  (:require [kixi.heimdall.service :as service]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.member :as member]
            [kixi.heimdall.invites :as invites]
            [kixi.heimdall.util :as util]
            [clojure.pprint :refer [pprint]]))

;; This namespace is for useful functions, designed too make ops a bunch easier

(println "<<< THE CURRENT PROFILE IS:" @kixi.heimdall.application/profile ">>>")

(defn db
  []
  (:db @kixi.heimdall.application/system))

(defn comms
  []
  (:communications @kixi.heimdall.application/system))

(defn change-user-password!
  [username new-password]
  (user/change-password! (db) username new-password))

(def wait-tries 120)
(def wait-per-try 500)

(defn wait-for
  ([fn]
   (wait-for fn #(throw (ex-info "Operation never succeeded." {}))))
  ([fn fail-fn]
   (wait-for fn fail-fn 1))
  ([fn fail-fn cnt]
   (if (< cnt wait-tries)
     (let [value (fn)]
       (or
         value
         (do
           (Thread/sleep wait-per-try)
           (recur fn fail-fn (inc cnt)))))
     (fail-fn))))

(defn find-user
  [username]
  (when-let [user (user/find-by-username (db) {:username username})]
    (let [grp-ftlr (juxt :group-name :id :group-type)]
      (pprint
       (-> user
           (dissoc :password)
           (assoc :groups {:owns (map grp-ftlr (group/find-by-user (db) (:id user)))
                           :member-of (map
                                       #(grp-ftlr (group/find-by-id (db) %))
                                       (member/retrieve-groups-ids (db) (:id user)))}))))))

(defn find-invite
  [username]
  (invites/get-invite (db) username))

(defn- find-group-members
  [group-name]
  (when-let [group (group/find-by-name (db) group-name)]
    (not-empty (member/retrieve-member-ids (db) (:id group)))))

(defn find-group
  [group-name]
  (when-let [members (find-group-members group-name)]
    (let [user-ftlr (juxt :name :username)]
      (pprint (map #(user-ftlr (user/find-by-id (db) %)) members)))))

(defn create-group!
  [group-name owner-name]
  (if-let [user (user/find-by-username (db) {:username owner-name})]
    (if-let [group (group/find-by-name (db) group-name)]
      :failed-group-exists
      (let [group-id (str (java.util.UUID/randomUUID))]
        (if (service/create-group-event (db)
                                        (comms)
                                        {:group-name group-name
                                         :group-id group-id
                                         :created (util/db-now)
                                         :user-id (:id user)
                                         :group-type "group"})
          (wait-for #(group/find-by-name (db) group-name))
          {:failed-create-group {:user user
                                 :group-id group-id
                                 :owner-name owner-name
                                 :group-name group-name}})))
    :failed-no-user))

(defn add-user-to-group!
  [group-name user-name]
  (let [user (user/find-by-username (db) {:username user-name})
        group (group/find-by-name (db) group-name)]
    (if (and user
             group
             (service/add-member-event (db)
                                       (comms)
                                       (:id user) (:id group)))
      (wait-for #(let [groups (member/retrieve-groups-ids (db) (:id user))]
                   (when (contains? (set groups) (:id group))
                     :success-user-added-to-group)))
      {:failed-add-user-to-group {:user user
                                  :group group}})))

(defn invite-user!
  ([username name]
   (invite-user! username name []))
  ([username name group-names]
   {:pre [(string? username) (string? name) (vector? group-names) (every? string? group-names)]}
   (let [[ok? user] (service/invite-user!
                     (db)
                     (comms)
                     {:username username
                      :name name})]
     (if ok?
       (do
         (wait-for #(user/find-by-username (db) {:username username}))
         (doall
          (for [group-name group-names]
            (let [group-result (create-group! group-name username)]
              (if (= group-result
                     :failed-group-exists)
                (add-user-to-group! group-name username)
                group-result)))))
       [ok? user]))))

(defn remove-user-from-group!
  [group-name user-name]
  (let [user (user/find-by-username (db) {:username user-name})
        group (group/find-by-name (db) group-name)]
    (if (and user
             group
             (service/remove-member-event (db)
                                          (comms)
                                          (:id user) (:id group)))
      (wait-for #(let [groups (member/retrieve-groups-ids (db) (:id user))]
                   (when-not (contains? (set groups) (:id group))
                     :success-user-removed-from-group)))
      {:failed-remove-user-from-group {:user user
                                       :group group}})))

(defn delete-group!
  [group-name deleter-username]
  (if-let [members (find-group-members group-name)]
    :failed-group-still-has-members
    (if-let [user (user/find-by-username (db) {:username deleter-username})]
      (do (service/delete-group-event (db)
                                      (comms)
                                      (:id (group/find-by-name (db) group-name))
                                      (:id user))
          :success-group-deleted)
      :failed-user-not-found)))
