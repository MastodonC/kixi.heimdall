(ns kixi.heimdall.config
  (:require [aero.core :as aero]
            [kixi.heimdall.util :as util]
            [clojure.java.io :as io]))

(defn relative-or-dummy-resolver
  [source include]
  (or (aero/relative-resolver source include)
      (io/resource (str include ".dummy")))) ;; for circle ci

(defn config [profile]
  (aero/read-config (io/resource "conf.edn") {:resolver relative-or-dummy-resolver :profile profile}))

(defn webserver-port [config]
  (get-in config [:jetty-server :port]))

(defn auth-conf [config]
  (get-in config [:auth-conf]))
