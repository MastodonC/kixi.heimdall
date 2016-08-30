(ns kixi.heimdall.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [kixi.heimdall.application :as app]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.util :as util]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [environ.core :refer [env]]))

(defn get-config
  [f]
  (edn/read-string (slurp f)))

(def auth-config
  (-> (if (util/file-exists? (System/getProperty "user.home") ".heimdall.auth-conf.edn")
        (io/file (System/getProperty "user.home" ".heimdall.auth-conf.edn"))
        (io/resource env-file))
      (get-config))

(defn auth-token [req]
  (let [[ok? res] (service/create-auth-token (:cassandra-session (:components req))
                                             (:auth-conf req)
                                             (:params req))]
    (if ok?
      {:status 201 :body res}
      {:status 401 :body res})))


(defn refresh-auth-token [req]
  (let [refresh-token (-> req :params :refresh-token)
        [ok? res] (service/refresh-auth-token (:cassandra-session (:components req))
                                              (:auth-conf req)
                                              refresh-token)]
    (if ok?
      {:status 201 :body res}
      {:status 401 :body res})))

(defn invalidate-refresh-token [req]
  (let [refresh-token (-> req :params :refresh-token)
        [ok? res] (service/invalidate-refresh-token (:cassandra-session (:components req))
                                                    (:auth-conf req)
                                                    refresh-token)]
    (if ok?
      {:status 200 :body res}
      {:status 401 :body res})))

(defn wrap-config [handler]
  (fn [req]
    (handler (assoc req :auth-conf auth-config))))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (POST "/create-auth-token" [] auth-token)
  (POST "/refresh-auth-token" [] refresh-auth-token)
  (POST "/invalidate-refresh-token" [] invalidate-refresh-token)
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
