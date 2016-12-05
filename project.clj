(def metrics-version "2.7.0")
(def slf4j-version "1.7.21")
(defproject kixi.heimdall "0.2.0-SNAPSHOT"
  :description "Authentication and authorization service"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-json "0.4.0"]
                 [buddy "1.2.0" :exclusions [cheshire]]
                 [cc.qbits/alia-all "3.2.0" :exclusions [cc.qbits/hayt org.clojure/clojure]]
                 [cc.qbits/hayt "3.0.1"]
                 [com.stuartsierra/component "0.3.1"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [com.taoensso/timbre "4.7.0"]
                 [com.izettle/dropwizard-metrics-influxdb "1.1.6"
                  :exclusions [ch.qos.logback/logback-classic
                               org.eclipse.jetty/jetty-util
                               com.fasterxml.jackson.core/jackson-core
                               org.apache.commons/commons-lang3]]
                 [com.fzakaria/slf4j-timbre "0.3.2"]
                 [metrics-clojure ~metrics-version]
                 [metrics-clojure-jvm ~metrics-version]
                 [metrics-clojure-ring ~metrics-version]
                 [clj-time "0.12.0"]
                 [joplin.core "0.3.9"]
                 [joplin.cassandra "0.3.9"]
                 [cider/cider-nrepl "0.13.0"]
                 ;; these are dependencies around running the server in the repl
                 [org.clojure/tools.namespace    "0.2.11"]
                 [org.clojure/tools.nrepl        "0.2.12"]
                 [org.clojure/tools.cli "0.3.5"]
                 [environ "1.1.0"]
                 [aero "1.0.0"]
                 [kixi/kixi.comms "0.1.17"]
                 [org.clojure/tools.analyzer "0.6.9"]
                 ;; not really dependency, dep collision https://groups.google.com/forum/#!topic/clojure/D_s9Drua6D4
                 ]
  :plugins [[lein-ring "0.9.7"]]
  :pedantic? true
  :repl-options {:init-ns user}
  :profiles
  {:uberjar {:aot [kixi.heimdall.bootstrap]
             :main kixi.heimdall.bootstrap
             :uberjar-name "kixi.heimdall.jar"}
   :dev {:source-paths ["dev"]
         :dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]
                        [org.clojure/data.json "0.2.6"]]}}
  :aliases {"seed" ["run" "-m" "joplin.alias/seed" "joplin.edn"]})
