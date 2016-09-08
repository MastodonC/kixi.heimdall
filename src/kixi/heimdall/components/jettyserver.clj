(ns kixi.heimdall.components.jettyserver
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :as log]))

(defn wrap-components
  "Assoc given components to the request."
  [handler components]
  (fn [req]
    (handler (assoc req :components components))))

(defn wrap-config
  "Assoc keys and passphrase configurations to request"
  [handler auth-conf]
  (fn [req]
    (handler (assoc req :auth-conf auth-conf))))

(defrecord JettyServer [handler port auth-conf]
  component/Lifecycle
  (start [this]
    (log/info "Starting JettyServer")
    (assoc this ::server (jetty/run-jetty (-> handler (wrap-components this) (wrap-config auth-conf))
                                          {:port port
                                           :join? false})))
  (stop [this]
    (log/info "Stopping JettyServer")
    (.stop (::server this)) ;; this is the jetty shutdown fn.
    (dissoc this ::server)))

(defn new-http-server
  [api-port]
  (->JettyServer #'kixi.heimdall.handler/app api-port auth-conf))
