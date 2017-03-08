(ns kixi.heimdall.refresh-token
  (:require [kixi.heimdall.components.database :as db]))

(def refresh-token-table "refresh-tokens")
(def refresh-token-by-user-id "refresh-tokens-by-user-id")

(defn add!
  [db {:keys [valid] :as refresh-token}]
  (let [token-data (assoc refresh-token
                          :id (str (java.util.UUID/randomUUID))
                          :valid (or (nil? valid) valid))]
    (db/put-item db
                 refresh-token-table
                 token-data
                 {:return :none})))

(defn find-by-user-and-issued
  [db user-id issued]
  (first (db/query db
                   refresh-token-table
                   {:user-id [:eq user-id]
                    :issued [:eq issued]}
                   {:index refresh-token-by-user-id
                    :limit 1
                    :return :all-attributes})))

(defn find-by-id
  [db id]
  (db/get-item db
               refresh-token-table
               {:id id}
               {:consistent? true}))

(defn invalidate!
  [db id]
  (let [refresh-token (find-by-id db id)
        user-id (:user_id refresh-token)
        issued (:issued refresh-token)]
    (db/update-item db
                    refresh-token-table
                    {:id id}
                    {:update-expr "SET valid = :v"
                     :expr-attr-vals {":v" false}})))
