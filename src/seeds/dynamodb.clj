(ns seeds.dynamodb
  (:require [kixi.heimdall.user :as user]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.application :as app]
            [kixi.heimdall.util :as util]
            [taoensso.timbre :as log]))

(defn run-dev [target & args])

(defn run-staging [target & args])

(defn run-prod [target & args])
