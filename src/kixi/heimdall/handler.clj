(ns kixi.heimdall.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [buddy.hashers :as hs]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.application :as app]
            [qbits.alia :as alia]
            ))

(defroutes app-routes
  (GET "/" [] "Hello World")
  ;; untested
  #_(GET "/database" [] (let [session (:database app/system)]
                          (alia/execute session "CREATE KEYSPACE alia
                       WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};")))
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
