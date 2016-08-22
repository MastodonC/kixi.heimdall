(ns kixi.heimdall.refresh-token
  (:require [kixi.heimdall.components.database :as db]
            [qbits.alia.uuid :as uuid]))

(defn add!
  [session refresh-token]
  (db/insert! session :refresh_tokens
              (assoc refresh-token :id (uuid/random))))
