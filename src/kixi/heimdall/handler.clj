(ns kixi.heimdall.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [kixi.heimdall.application :as app]
            [taoensso.timbre :as log]
            [kixi.heimdall.components.database :as db]            ))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/database" [] (let [session (:cassandra-session app/system)]
                        (db/select session "users" "username" {:password "boo" :username "user"})))
  (route/not-found "Not Found"))

(defn wrap-catch-exceptions [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t (log/error t)))))

(def app
  (-> app-routes
      (wrap-catch-exceptions)
      (wrap-defaults api-defaults)))
