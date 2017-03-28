(ns kixi.heimdall.components.database
  (:require [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [joplin.repl :as jrepl :refer [migrate load-config]]
            [taoensso.timbre :as log]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.application :as app]
            [taoensso.faraday :as far]
            [amazonica.aws.cloudwatch :as cloudwatch]))


(def app "heimdall")

(defn decorated-table
  [table prefix]
  (keyword (clojure.string/join "-" [app prefix table])))

(defn prefix
  [conf]
  (:prefix (:db-conf conf)))

(defn alerts
  [conf]
  (:alerts (:db-conf conf)))

(defn db
  [conf]
  (or (:db (:db-conf conf)) {}))

(defn sns
  [conf]
  (get-in db-conf [:db-conf :alerts :sns]))

(defprotocol Database
  (create-table [this table index opts])
  (delete-table [this table])
  (put-item [this table record opts])
  (get-item [this table where opts])
  (query [this table where opts])
  (update-item [this table where opts])
  (delete-item [this table where opts])
  (scan [this table]))

(def threshold 0.9)
(def alarm-period 60)
(def evaluation-period 1)

(defn put-dynamo-table-alarm
  [{:keys [metric
           table-name
           sns
           description]}]
  (cloudwatch/put-metric-alarm {:endpoint "eu-central-1"}
                               :alarm-name (str metric "-" table-name)
                               :alarm-description description
                               :namespace "AWS/DynamoDB"
                               :metric-name metric
                               ;;                   :dimensions ["Tablename" table-name] ;;(str "name=TableName,value=" table-name)
                               :statistic "Sum"
                               :threshold threshold
                               :comparison-operator "GreaterThanOrEqualToThreshold"
                               :period alarm-period
                               :evaluation-periods 1
                               :alarm-actions [sns]))

(defn read-dynamo-alarm
  [{:keys [table-name
           sns]}]
  (put-dynamo-table-alarm {:metric "ConsumedReadCapacityUnits"
                           :table-name table-name
                           :sns sns
                           :description (str "Alarm: read capacity almost at provisioned read capacity for " table-name)}))

(defn write-dynamo-alarm
  [{:keys [table-name
           sns]}]
  (put-dynamo-table-alarm {:metric "ConsumedWriteCapacityUnits"
                           :table-name table-name
                           :sns sns
                           :description (str "Alarm: write capacity almost at provisioned write capacity for " table-name)}))

(defn table-dynamo-alarms
  [table-name
   sns]
  (read-dynamo-alarm {:table-name table-name :sns sns})
  (write-dynamo-alarm {:table-name table-name :sns sns}))

(defrecord DynamoDB [db-conf profile]
  Database
  (create-table [this table index opts]
    (let [table-name (decorated-table table (prefix this)) ]
      (far/create-table (db this)
                        table-name
                        index
                        opts)
      (when (alerts this)
        (try
          (table-dynamo-alarms table-name (sns this))
          (catch Exception e
            (log/error e "failed to create cloudwatch alarm with:"))))))
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
