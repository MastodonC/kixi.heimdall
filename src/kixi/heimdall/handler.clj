(ns kixi.heimdall.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [buddy.hashers :as hs]
            [kixi.heimdall.application :as app]
            [qbits.alia :as alia]
            [taoensso.timbre :as log]))

(defn find-user
  [password-hash username]
  {:username username :password-hash password-hash})

(defn add-user!
  []
  true)

(defn auth-user [ds credentials]
  (let [user (find-user ds (:username credentials))
        unauthed [false {:message "Invalid username or password"}]]
    (if user
      (if (hs/check (:password credentials) (:password user))
        [true {:user (dissoc user :password)}]
        unauthed)
      unauthed)))

(defroutes app-routes
  (GET "/" [] "Hello World")
  ;; untested
  (GET "/database" [] (let [session (:session (:cassandra-session app/system))]
                        (alia/execute session "USE heimdall;")))
  (route/not-found "Not Found"))

(defn wrap-catch-exceptions [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t (log/error t)))))

(def app
  (-> app-routes
      (wrap-catch-exceptions)
      (wrap-defaults api-defaults)))
