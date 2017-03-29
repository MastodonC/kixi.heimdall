(ns kixi.heimdall.components.database
  (:require [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [joplin.repl :as jrepl :refer [migrate load-config]]
            [taoensso.timbre :as log]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.application :as app]
            [taoensso.faraday :as far]))


(def app "heimdall")

(defn decorated-table
  [table prefix]
  (keyword (clojure.string/join "-" [app prefix table])))

(defn prefix
  [conf]
  (:prefix (:db-conf conf)))

(defn db
  [conf]
  (or (:db (:db-conf conf)) {}))

(defprotocol Database
  (create-table [this table index opts])
  (delete-table [this table])
  (put-item [this table record opts])
  (get-item [this table where opts])
  (query [this table where opts])
  (update-item [this table where opts])
  (delete-item [this table where opts])
  (scan [this table]))

(defrecord DynamoDB [db-conf profile]
  Database
  (create-table [this table index opts]
    (far/create-table (db this)
                      (decorated-table table (prefix this))
                      index
                      opts))
  (delete-table [this table]
    (far/delete-table (db this)
                      (decorated-table table (prefix this))))
  (put-item [this table record opts]
    (far/put-item (db this)
                  (decorated-table table (prefix this))
                  record
                  opts))
  (get-item [this table where opts]
    (far/get-item (db this)
                  (decorated-table table (prefix this))
                  where
                  opts))
  (query [this table where opts]
    (far/query (db this)
               (decorated-table table (prefix this))
               where
               opts))
  (update-item [this table where opts]
    (far/update-item (db this)
                     (decorated-table table (prefix this))
                     where
                     opts))
  (delete-item [this table where opts]
    (far/delete-item (db this)
                     (decorated-table table (prefix this))
                     where
                     opts))
  (scan [this table]
    (far/scan (db this)
              (decorated-table table (prefix this))))

  component/Lifecycle
  (start [component]
    (log/info "Starting dynamodb component ...")
    (let [joplin-config (jrepl/load-config (io/resource "joplin.edn"))]
      (log/info "About to migrate")
      (->> profile
           (migrate joplin-config)
           (with-out-str)
           (clojure.string/split-lines)
           (run! #(log/info "> JOPLIN:" %))))
    (log/info "Migrated")
    (log/info "Started")
    component)
  (stop [component]
    (log/info "Stopping dynamodb component")
    component))

(defn new-session
  [opts profile]
  (->DynamoDB opts profile)  )
