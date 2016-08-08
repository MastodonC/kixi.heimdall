(defproject kixi.heimdall "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [buddy "1.0.0"]
                 [cc.qbits/alia-all "3.1.10"]
                 [cc.qbits/hayt "3.0.1"]
                 [com.stuartsierra/component "0.3.1"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [com.taoensso/timbre "4.7.0"]
                 ;; these are dependencies around running the server in the repl
                 [org.clojure/tools.namespace    "0.2.11"]
                 [org.clojure/tools.nrepl        "0.2.12"]]
  :plugins [[lein-ring "0.9.7"]]
  :repl-options {:init-ns user}
  :ring {:handler kixi.heimdall.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
