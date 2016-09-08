(ns kixi.heimdall.config
  (:require [aero.core :as aero]
            [kixi.heimdall.util :as util]
            [clojure.java.io :as io]))

(defn config [profile]
  (aero/read-config (io/resource "conf.edn") {:profile profile}))

(defn webserver-port [config]
  (get-in config [:jetty-server :port]))

(defn auth-conf [config]
  (get-in config [:auth-conf]))
