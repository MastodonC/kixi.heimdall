(ns kixi.heimdall.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [kixi.heimdall.application :as app]
            [taoensso.timbre :as log]
            [kixi.heimdall.components.database :as db]
            [buddy.sign.jwt :as jwt]
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
                   ".heimdall.auth-conf.edn")]
    (try (get-config f)
         (catch java.io.FileNotFoundException e
           (if-let [env-file (:auth-conf env)]
             (get-config (io/resource env-file))
             (throw e))))))

(defn- pkey [auth-conf]
  (ks/private-key
   (io/resource (:privkey auth-conf))
   (:passphrase auth-conf)))

(defn create-auth-token [session auth-conf credentials]
  (let [[ok? res] (user/auth session credentials)
        exp (-> (t/plus (t/now) (t/days 1)) (sign/to-timestamp))]
    (if ok?
      [true {:token (jwt/sign res
                              (pkey auth-conf)
                              {:alg :rs256 :exp exp})}]
      [false res])))

(defn auth-token [req]
  (let [[ok? res] (create-auth-token (:cassandra-session (:components req))
                                     (:auth-conf req)
                                     (:params req))]
    (if ok?
      {:status 201 :body res}
      {:status 401 :body res})))

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
      (wrap-config)
      (wrap-catch-exceptions)
      wrap-keyword-params
      wrap-json-params
      wrap-json-response))
