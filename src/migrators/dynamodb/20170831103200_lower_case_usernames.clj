(ns migrators.dynamodb.20170831103200-lower-case-usernames
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.application :as app]
            [taoensso.faraday :as far]))

;; all usernames need to be made lower case

(defn get-db-config
  []
  (let [conf (config/config @app/profile)]
    (:dynamodb conf)))

(defn up
  [db]
  (let [conn  (db/new-session (get-db-config) @app/profile)
        users (db/scan conn "users")
        lc-username-fn (fn [{:keys [username id]}]
                         (db/update-item conn
                                         "users"
                                         {:id id}
                                         {:update-expr "SET username = :v"
                                          :expr-attr-vals {":v" (clojure.string/lower-case username)}}))]
    (run! lc-username-fn users)))

(defn down
  [db])
