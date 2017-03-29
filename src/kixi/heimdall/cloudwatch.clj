(ns kixi.heimdall.cloudwatch
  (:require [amazonica.aws.cloudwatch :as cloudwatch]
            [taoensso.timbre :as log]))

(def threshold 0.9)
(def alarm-period 60)
(def evaluation-period 1)

(defn put-dynamo-table-alarm
  [{:keys [metric
           table-name
           sns
           region
           description] :as params}]
  (cloudwatch/put-metric-alarm {:endpoint region}
                               :alarm-name (str metric "-" (name table-name))
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
           sns
           region]}]
  (put-dynamo-table-alarm {:metric "ConsumedReadCapacityUnits"
                           :table-name table-name
                           :sns sns
                           :region region
                           :description (str "Alarm: read capacity almost at provisioned read capacity for " table-name)}))

(defn write-dynamo-alarm
  [{:keys [table-name
           sns
           region]}]
  (put-dynamo-table-alarm {:metric "ConsumedWriteCapacityUnits"
                           :table-name table-name
                           :sns sns
                           :region region
                           :description (str "Alarm: write capacity almost at provisioned write capacity for " table-name)}))

(defn table-dynamo-alarms
  [table-name
   {:keys [sns region]}]
  (read-dynamo-alarm {:table-name table-name :sns sns :region region})
  (write-dynamo-alarm {:table-name table-name :sns sns :region region}))
