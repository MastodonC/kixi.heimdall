(ns kixi.heimdall.kaylee
  (:require [kixi.heimdall.service :as service]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.member :as member]
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

(defn wait-for
  [f]
  (loop []
    (if-let [x (f)]
      x
      (do (Thread/sleep 10)
          (recur)))))

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
          :failed-create-group)))
    :failed-no-user))

(defn add-user-to-group!
  [group-name user-name]
  (let [user (user/find-by-username (db) {:username user-name})
        group (group/find-by-name (db) group-name)]
    (if (service/add-member-event (db)
                                  (comms)
                                  (:id user) (:id group))
      (wait-for #(let [groups (member/retrieve-groups-ids (db) (:id user))]
                   (when (contains? (set groups) (:id group))
                     :success-user-added-to-group)))
      :failed-add-user-to-group)))

(defn invite-user!
  ([username name]
   (invite-user! username name []))
  ([username name group-names]
   {:pre [(string? username) (string? name) (vector? group-names)]}
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
    (if (service/remove-member-event (db)
                                     (comms)
                                     (:id user) (:id group))
      (wait-for #(let [groups (member/retrieve-groups-ids (db) (:id user))]
                   (when-not (contains? (set groups) (:id group))
                     :success-user-removed-from-group)))
      :failed-remove-user-from-group)))
