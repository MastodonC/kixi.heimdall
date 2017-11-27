(ns migrators.dynamodb.20171127120500-presignup-bool-type-change
  (:require [kixi.heimdall.components.database :as db]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.application :as app]
            [taoensso.faraday :as far]))

(defn get-db-config
  []
  (let [conf (config/config @app/profile)]
    (:dynamodb conf)))

(defn up
  [db]
  (let [conn (db/new-session (get-db-config) @app/profile)
        users (db/scan conn "users")
        ;; we read the nippy encoded value out of the pre-signup field (which is automatically typed to a bool)
        ;; then we write it back as an actual bool rather than the nippy format.
        pre-signup-type-fn (fn [{:keys [pre-signup id]}]
                             (db/update-item conn
                                             "users"
                                             {:id id}
                                             {:update-expr    "SET #ps = :v"
                                              :expr-attr-names {"#ps" "pre-signup"}
                                              :expr-attr-vals {":v" pre-signup}}))]
    (run! pre-signup-type-fn users)))

(defn down
  [db])
