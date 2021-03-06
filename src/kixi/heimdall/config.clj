(ns kixi.heimdall.config
  (:require [aero.core :as aero]
            [kixi.heimdall.util :as util]
            [clojure.java.io :as io]))

(defmethod aero/reader 'rand-uuid
  [{:keys [profile] :as opts} tag value]
  (str (java.util.UUID/randomUUID)))

(defn relative-or-dummy-resolver
  [source include]
  (or (aero/relative-resolver source include)
      (io/resource (str include ".dummy")))) ;; for circle ci

(defn config [profile]
  (aero/read-config (io/resource "conf.edn") {:resolver relative-or-dummy-resolver :profile profile}))

(defn webserver-port [config]
  (get-in config [:jetty-server :port]))
