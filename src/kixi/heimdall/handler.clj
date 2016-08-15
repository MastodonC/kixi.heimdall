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
            [kixi.heimdall.user :as user]))

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

(defn auth-token [session auth-conf params]
  (let [[ok? res] (create-auth-token session
                                     auth-conf
                                     params)]
    (if ok?
      {:status 201 :body res}
      {:status 401 :body res})))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/database" [] (let [session (:cassandra-session app/system)]
                        (db/select session "users" "username" {:password "boo" :username "user"})))
  (POST "/create-auth-token" [& params] (let [session (:cassandra-session app/system)
                                         auth-conf {:privkey "auth_privkey.pem"
                                                    :passphrase "bigdata"}]
                                     (auth-token session auth-conf params)))
  (route/not-found "Not Found"))

(defn wrap-catch-exceptions [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t (log/error t)))))

(def app
  (-> app-routes
      (wrap-catch-exceptions)
      (wrap-defaults api-defaults)))
