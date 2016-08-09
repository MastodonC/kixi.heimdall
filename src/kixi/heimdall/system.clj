(ns kixi.heimdall.system
  (:require [kixi.heimdall.handler :refer :all]
            [kixi.heimdall.components.database :refer :all]
            [kixi.heimdall.components.jettyserver :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn combine
  "Merge maps, recursively merging nested maps whose keys collide."
  ([] {})
  ([m] m)
  ([m1 m2]
   (reduce (fn [m1 [k2 v2]]
             (if-let [v1 (get m1 k2)]
               (if (and (map? v1) (map? v2))
                 (assoc m1 k2 (combine v1 v2))
                 (assoc m1 k2 v2))
               (assoc m1 k2 v2)))
           m1 m2))
  ([m1 m2 & more]
   (apply combine (combine m1 m2) more)))

(defn- pushback-reader [x]
  (java.io.PushbackReader. (io/reader x)))

(defn config []
  (let [f (io/file (System/getProperty "user.home") ".heimdall.edn")]
    (combine (edn/read (pushback-reader (io/resource "default.heimdall.edn")))
             (when (.exists f)
               (edn/read (pushback-reader (io/input-stream f)))))))

(defn system []
  (let [api-port 3000
        cassandra-host "localhost"]
    (-> (component/system-map
         :jetty-server (->JettyServer #'kixi.heimdall.handler/app api-port)
         :repl-server  (Object.) ; dummy - replaced when invoked via uberjar.
         :cluster (new-cluster {})
         :cassandra-session (new-session {:keyspace "heimdall"}))
        (component/system-using
         {:cassandra-session [:cluster]}))))
