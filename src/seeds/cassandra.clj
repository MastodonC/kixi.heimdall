(ns seeds.cassandra
  (:require [qbits.alia                 :as alia]
            [qbits.hayt                 :as hayt]
            [joplin.cassandra.database :refer [get-connection]]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.components.database :as db]))

(defn run-dev [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))
        dc (db/->DirectConnection {:session conn})
        ;; Add a test user
        test-user (user/add! dc {:username "test@mastodonc.com" :password "Secret123" :name "Test User"})
        _ (group/add! dc {:name "Test User"
                          :user-id (:id test-user)
                          :group-type "user"})]
    ;; Add a test group
    (#'service/create-group dc
                            {:group {:group-name "Test Group"}
                             :user-id (str (:id test-user))})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def staging-users
  {:test "Test User"
   :antony "Antony"
   :tom "Tom"
   :elise "Elise"
   :bruce "Bruce"
   :sam "Sam"
   :seb "Seb"
   :sunny "Sunny"
   :eleonore "Eleonore"
   :fran "Fran"
   :chris "Chris"
   :jase "Jase"})

(def staging-groups
  {"Dev Team" [:antony :tom :elise]
   "Product Team" [:sam :bruce]
   "Cool Kids" [:seb :sunny :eleonore :jase]
   "Sales Team" [:fran :chris]
   "Mastodon C" (keys staging-users)
   "Boys" [:antony :tom :bruce :seb :chris :jase]
   "Girls" [:elise :sam :sunny :eleonore :fran]})

(defn create-user!
  [dc [k user-name]]
  (let [email (str (name k)"@mastodonc.com")
        existing (user/find-by-username dc {:username email})]
    (if existing
      existing
      (let [u (user/add! dc {:username email
                             :password "Secret123"
                             :name user-name})]
        (service/add-self-group dc u)
        u))))

(defn create-group!
  [dc all-users [group-name users]]
  (let [group (#'service/create-group dc
                                      {:group {:group-name group-name}
                                       :user-id (str (:id (get all-users (first users))))})]
    (run! (fn [user]
            (#'service/add-member dc
                                  {:group-id (str (:group-id group))
                                   :user-id (str (:id (get all-users user)))}))
          (rest users))))

(defn run-staging [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))
        dc (db/->DirectConnection {:session conn})
        ;; Add test users
        users (zipmap (keys staging-users)
                      (map (partial create-user! dc) staging-users))

        ;; Add test groups
        groups (zipmap (keys staging-groups)
                       (map (partial create-group! dc users) staging-groups))]))

(defn run-prod [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))]))
