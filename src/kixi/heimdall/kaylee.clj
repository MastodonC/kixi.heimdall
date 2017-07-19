(ns kixi.heimdall.kaylee
  (:require [kixi.heimdall.service :as service]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.member :as member]
            [kixi.heimdall.util :as util]))

;; This namespace is for useful functions, designed too make ops a bunch easier

(println "<<< THE CURRENT PROFILE IS:" @kixi.heimdall.application/profile ">>>")

(defn db
  []
  (:db @kixi.heimdall.application/system))

(defn comms
  []
  (:communications @kixi.heimdall.application/system))

(defn invite-user!
  [username]
  (service/invite-user!
   (db)
   (comms)
   username))

(defn change-user-password!
  [username new-password]
  (user/change-password! (db) username new-password))

(defn create-group!
  [group-name owner-name]
  (if-let [user (user/find-by-username (db) {:username owner-name})]
    (let [group-id (str (java.util.UUID/randomUUID))]
      (if (service/create-group-event (db)
                                      (comms)
                                      {:group-name group-name
                                       :group-id group-id
                                       :created (util/db-now)
                                       :user-id (:id user)})
        (loop []
          (if-let [group (group/find-by-name (db) group-name)]
            group
            (do (Thread/sleep 1000)
                (recur))))
        :failed-create-group))
    :failed-no-user))

(defn add-user-to-group!
  [group-name user-name]
  (let [user (user/find-by-username (db) {:username user-name})
        group (group/find-by-name (db) group-name)]
    (if (service/add-member-event (db)
                                  (comms)
                                  (:id user) (:id group))
      (loop []
        (let [groups (member/retrieve-groups-ids (db) (:id user))]
          (if (contains? (set groups) (:id group))
            true
            (do (Thread/sleep 1000)
                (recur)))))
      :failed-add-user-to-group)))

(defn remove-user-from-group!
  [group-name user-name]
  (let [user (user/find-by-username (db) {:username user-name})
        group (group/find-by-name (db) group-name)]
    (if (service/remove-member-event (db)
                                     (comms)
                                     (:id user) (:id group))
      (loop []
        (let [groups (member/retrieve-groups-ids (db) (:id user))]
          (if-not (contains? (set groups) (:id group))
            true
            (do (Thread/sleep 1000)
                (recur)))))
      :failed-remove-user-from-group)))
