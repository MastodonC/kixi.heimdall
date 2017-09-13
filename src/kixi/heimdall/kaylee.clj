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
                                         :user-id (:id user)})
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
                   (contains? (set groups) (:id group))))
      :failed-add-user-to-group)))

(defn invite-user!
  ([username name]
   (invite-user! username name []))
  ([username name group-names]
   (let [[ok? user] (service/invite-user!
                     (db)
                     (comms)
                     {:username username
                      :name name})]
     (when ok?
       (wait-for #(user/find-by-username (db) {:username username}))
       (doseq [group-name group-names]
         (let [group-result (create-group! group-name username)]
           (when (= group-result
                    :failed-group-exists)
             (add-user-to-group! group-name username))))))))

(defn remove-user-from-group!
  [group-name user-name]
  (let [user (user/find-by-username (db) {:username user-name})
        group (group/find-by-name (db) group-name)]
    (if (service/remove-member-event (db)
                                     (comms)
                                     (:id user) (:id group))
      (wait-for #(let [groups (member/retrieve-groups-ids (db) (:id user))]
                   (contains? (set groups) (:id group))))
      :failed-remove-user-from-group)))
