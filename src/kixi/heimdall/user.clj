(ns kixi.heimdall.user
  (:require [buddy.hashers :as hs]
            [clojure.spec :as spec]
            [kixi.heimdall
             [schema :as schema]
             [util :as util]]
            [kixi.heimdall.components.database :as db]))

(def user-table "users")
(def users-by-username "users-by-username")

(defn all
  [db]
  (db/scan db
           user-table))

(defn validate
  [user]
  (let [validation (spec/valid? :kixi.heimdall.schema/login user)]
    (if validation
      [true nil]
      [false (if (spec/valid? :kixi.heimdall.schema/username (:username user))
               "password should have at least 8 letters, one uppercase and one lowercase letter, and one number"
               "username should have an email format")])))


;; data functions

(defn add!
  [db user]
  (let [user-id (str (java.util.UUID/randomUUID))
        user-data (-> user
                      (update-in [:password] #(hs/encrypt %))
                      (assoc :created (util/db-now)
                             :id user-id))]
                                        ;add user-data spec
    (db/put-item db
                 user-table
                 user-data
                 {:return :none})
    user-data))

(defn find-by-username
  [db {:keys [username]}]
  (first
   (db/query db
             user-table
             {:username [:eq username]}
             {:index users-by-username
              :limit 1
              :return :all-attributes})))

(defn find-by-id
  [db id]
  (db/get-item db
               user-table
               {:id id}
               {:consistent? true}))

(defn auth
  [db {:keys [username password]}]
  (let [user (find-by-username db {:username username})
        unauthed [false {:message "Invalid username or password"}]]
    (if user
      (if (hs/check password (:password user))
        [true {:user (dissoc user :password)}]
        unauthed)
      unauthed)))

(defn change-password!
  "Change the user's password"
  [db username new-password]
  (if-let [user (find-by-username db {:username username})]
    (let [pwd (hs/encrypt new-password)]
      (db/update-item db
                      user-table
                      {:id (:id user)}
                      {:update-expr "SET password = :p"
                       :expr-attr-vals {":p" pwd}
                       :return :none})
      true)
    false))
