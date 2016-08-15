(ns kixi.heimdall.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [kixi.heimdall.application :as app]
            [taoensso.timbre :as log]
            [kixi.heimdall.components.database :as db]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as sign]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clojure.java.io :as io]
            [kixi.heimdall.user :as user]
            [clojure.edn :as edn]
            [environ.core :refer [env]]))

(defn get-config
  [f]
  (edn/read-string (slurp f)))

(def auth-config
  (let [f (io/file (System/getProperty "user.home")
                   ".heimdall.auth-conf.edn")
        env-file (io/resource (:auth-conf env))]
    (try (get-config f)
         (catch java.io.FileNotFoundException _
           (get-config env-file)))))

(defn- pkey [auth-conf]
  (ks/private-key
   (io/resource (:privkey auth-conf))
   (:passphrase auth-conf)))

(defn create-auth-token [session auth-conf credentials]
  (let [[ok? res] (user/auth session credentials)
        exp (-> (t/plus (t/now) (t/days 1)) (sign/to-timestamp))]
    (if ok?
      [true {:token (jws/sign (str res)
                              (pkey auth-conf)
                              {:alg :rs256 :exp exp})}]
      [false res])))

(defn auth-token [req]
  (let [[ok? res] (create-auth-token (:cassandra-session req)
                                     (:auth-conf req)
                                     (:params req))]
    (if ok?
      {:status 201 :body res}
      {:status 401 :body res})))


(defn wrap-datasource [handler]
  (fn [req]
    (handler (assoc req :cassandra-session (:cassandra-session app/system)))))

(defn wrap-config [handler]
  (fn [req]
    (handler (assoc req :auth-conf auth-config))))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (POST "/create-auth-token" [] auth-token)
  (route/not-found "Not Found"))

(defn wrap-catch-exceptions [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t (log/error t)))))

(def app
  (-> app-routes
      (wrap-datasource)
      (wrap-config)
      (wrap-catch-exceptions)
      (wrap-defaults api-defaults)))
