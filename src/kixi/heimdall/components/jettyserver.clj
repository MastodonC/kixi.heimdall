(ns kixi.heimdall.components.jettyserver
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :as log]))

(defrecord JettyServer [handler port]
  component/Lifecycle
  (start [this]
    (log/info "Starting JettyServer")
    (assoc this ::server (jetty/run-jetty handler {:port port
                                                   :join? false})))
  (stop [this]
    (log/info "Stopping JettyServer")
    (.stop (::server this)) ;; this is the jetty shutdown fn.
    (dissoc this ::server)))
