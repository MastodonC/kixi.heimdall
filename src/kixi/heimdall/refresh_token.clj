(ns kixi.heimdall.refresh-token
  (:require [kixi.heimdall.components.database :as db]
            [qbits.alia.uuid :as uuid]))

(defn add!
  [session {:keys [valid] :as refresh-token}]
  (let [token-data (assoc refresh-token
                          :id (uuid/random)
                          :valid (or (nil? valid) valid))]
    (db/insert! session :refresh_tokens token-data)
    (db/insert! session :refresh_tokens_by_user_id_and_issued token-data)))

(defn find-by-user-and-issued
  [session user-id issued]
  (first (db/select* session :refresh_tokens_by_user_id_and_issued
                     {:user_id user-id
                      :issued issued})))

(defn invalidate!
  [session id]
  (db/update! session :refresh_tokens
              {:valid false}
              {:id id}))
