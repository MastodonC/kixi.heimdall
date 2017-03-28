(ns kixi.heimdall.cloudwatch
  (:require [amazonica.aws.cloudwatch :as cloudwatch]))

(def threshold 0.9)
(def alarm-period 60)
(def evaluation-period 1)

(defn put-dynamo-table-alarm
  [{:keys [metric
           table-name
           sns
           description] :as params}]
  (cloudwatch/put-metric-alarm {:endpoint "eu-central-1"}
                               :alarm-name (str (name table-name) "-" metric)
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
