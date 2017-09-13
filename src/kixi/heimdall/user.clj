(ns kixi.heimdall.user
  (:require [buddy.hashers :as hs]
            [clojure.spec :as spec]
            [kixi.heimdall
             [schema :as schema]
             [util :as util]]
            [kixi.heimdall.components.database :as db]
            [taoensso.faraday :as far]))

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
  (let [user-data (-> user
                      (select-keys [:id :created :username :name :pre-signup :group-id])
                      (update :username clojure.string/lower-case))]
    (db/put-item db
                 user-table
                 (update user-data :pre-signup far/freeze)
                 {:return :none
                  :cond-expr "attribute_not_exists(id)"})
    user-data))

(def frozen-true (far/freeze true))

(defn signed-up!
  [db user]
  (let [user-data (update user :password hs/encrypt)]
                                        ;add user-data spec
    (db/update-item db
                    user-table
                    {:id (:id user-data)}
                    {:update-expr (str "SET password = :p, "
                                       "#ps = :ps, "
                                       "#su = :su")
                     :expr-attr-vals {":p" (:password user-data)
                                      ":ps" (far/freeze (:pre-signup user-data))
                                      ":su" (:signed-up user-data)
                                      ":true" frozen-true}
                     :expr-attr-names {"#ps" "pre-signup"
                                       "#su" "signed-up"}
                     :return :none
                     :cond-expr "attribute_exists(id) AND #ps = :true"})
    user-data))

(defn find-by-username
  [db {:keys [username]}]
  (first
   (db/query db
             user-table
             {:username [:eq (clojure.string/lower-case username)]}
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
    (if (and user
             (not (:pre-signup user)))
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
