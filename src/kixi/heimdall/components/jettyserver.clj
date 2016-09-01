(ns kixi.heimdall.components.jettyserver
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :as log]))

(defn wrap-components
  "Assoc given components to the request."
  [handler components]
  (fn [req]
    (handler (assoc req :components components))))

(defrecord JettyServer [handler port]
  component/Lifecycle
  (start [this]
    (log/info "Starting JettyServer")
    (assoc this ::server (jetty/run-jetty (wrap-components handler this)
                                          {:port port
                                           :join? false})))
  (stop [this]
    (log/info "Stopping JettyServer")
    (.stop (::server this)) ;; this is the jetty shutdown fn.
    (dissoc this ::server)))

(defn new-http-server
  [api-port]
  (->JettyServer #'kixi.heimdall.handler/app api-port))
