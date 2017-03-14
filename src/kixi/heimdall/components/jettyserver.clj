(ns kixi.heimdall.components.jettyserver
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]
            [kixi.metrics.name-safety :refer [safe-name]]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [metrics.timers :refer [time! timer]]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.handler :as handler])
  (:import [java.util.concurrent TimeUnit]))

(defn wrap-key-value
  [handler key value]
  (fn [req]
    (handler (assoc req key value))))

(defn metric-name
  [request response]
  (let [name (-> (str/upper-case (name (:request-method request)))
                 (str "." (:uri request))
                 safe-name
                 (str "." (:status response)))]
    ["info" "resources" name]))

(defn wrap-per-resource-metrics
  "A middleware function to add metrics for all routes in the given
  handler. The simpler form adds default aggregators that replace GUIDs,
  Mongo IDs and Numbers with the constants GUID, MONGOID and NUMBER so
  that metric paths are sensible limited. Use the second form to specify
  your own replacements."
  [handler metrics]
  (fn [request]
    (let [start (System/currentTimeMillis)
          response (handler request)
          duration (- (System/currentTimeMillis) start)
          timer (timer (:registry metrics) (metric-name request response))]
      (.update timer duration TimeUnit/MILLISECONDS)
      response)))

(defrecord JettyServer [handler port conf]
  component/Lifecycle
  (start [this]
    (log/info "Starting JettyServer")
    (assoc this ::server (jetty/run-jetty (-> handler
                                              (wrap-key-value :components this)
                                              (wrap-key-value :auth-conf (:auth-conf conf))
                                              (wrap-per-resource-metrics (:metrics this)))
                                          {:port port
                                           :join? false})))
  (stop [this]
    (log/info "Stopping JettyServer")
    (.stop (::server this)) ;; this is the jetty shutdown fn.
    (dissoc this ::server)))

(defn new-http-server
  [api-port auth-conf]
  (->JettyServer handler/app api-port auth-conf))
