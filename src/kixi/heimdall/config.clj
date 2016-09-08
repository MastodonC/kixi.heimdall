(ns kixi.heimdall.config
  (:require [aero.core :as aero]
            [kixi.heimdall.util :as util]
            [clojure.java.io :as io]))

(defn resource-or-dummy-resolver
  [_ include]
  (println "in dummy resolver" include)
  (or (util/file-exists? (io/resource include))
      (io/resource (str include ".dummy"))))

(defn config [profile]
  (aero/read-config (io/resource "conf.edn") {:resolver resource-or-dummy-resolver :profile profile}))

(defn webserver-port [config]
  (get-in config [:jetty-server :port]))

(defn auth-conf [config]
  (get-in config [:auth-conf]))
