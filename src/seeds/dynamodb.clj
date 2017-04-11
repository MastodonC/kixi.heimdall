(ns seeds.dynamodb
  (:require [kixi.heimdall.user :as user]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.application :as app]
            [taoensso.timbre :as log]))

(defn get-db-config
  []
  (let [conf (config/config @app/profile)]
    (:dynamodb conf)))

(defn run-dev [target & args]
  (let [dc (db/new-session (get-db-config) @app/profile)
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
   :jase "Jase"
   :lora "Lora"})

(def staging-groups
  {"Dev Team" [:antony :tom :elise]
   "Product Team" [:sam :bruce]
   "Cool Kids" [:seb :sunny :eleonore :jase :lora]
   "Sales Team" [:fran :chris]
   "Mastodon C" (keys staging-users)
   "Boys" [:antony :tom :bruce :seb :chris :jase]
   "Girls" [:elise :sam :sunny :eleonore :fran :lora]})

(def prod-users
  {:test "Test User"})

(def prod-groups
  {"Test Team" [:test]})

(defn create-user!
  [db [k user-name]]
  (let [email (str (name k)"@mastodonc.com")
        existing (user/find-by-username db {:username email})]
    (if existing
      existing
      (let [u (user/add! db {:username email
                             :password "Secret123"
                             :name user-name})]
        (service/add-self-group db u)
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
  (let [dc (db/new-session (get-db-config) @app/profile)
        ;; Add test users
        users (zipmap (keys staging-users)
                      (map (partial create-user! dc) staging-users))

        ;; Add test groups
        groups (zipmap (keys staging-groups)
                       (map (partial create-group! dc users) staging-groups))]))


(defn run-prod [target & args]
  (let [dc (db/new-session (get-db-config) @app/profile)
        ;; Add test users
        users (zipmap (keys prod-users)
                      (map (partial create-user! dc) prod-users))

        ;; Add test groups
        groups (zipmap (keys prod-groups)
                       (map (partial create-group! dc users) prod-groups))]))
