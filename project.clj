(def metrics-version "2.7.0")
(def slf4j-version "1.7.21")
(defproject kixi.heimdall "0.2.0-SNAPSHOT"
  :description "Authentication and authorization service"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-beta1"]
                 [org.clojure/test.check "0.9.0"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [kixi/buddy "1.2.1" :exclusions [cheshire] :upgrade :kixi]
                 [com.stuartsierra/component "0.3.1"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [com.taoensso/timbre "4.8.0"]
                 [com.taoensso/faraday "1.9.0" :exclusions [com.amazonaws/aws-java-sdk-dynamodb]]
                 [kixi/kixi.log "0.1.6" :upgrade :kixi]
                 [kixi/kixi.metrics "0.4.1" :upgrade :kixi]
                 [kixi/kixi.spec "0.1.27" :upgrade :kixi]
                 [metrics-clojure ~metrics-version]
                 [metrics-clojure-jvm ~metrics-version]
                 [metrics-clojure-ring ~metrics-version]
                 [clj-time "0.12.0"]
                 [kixi/joplin.core "0.3.10-SNAPSHOT" :upgrade :kixi]
                 [kixi/joplin.dynamodb "0.3.10-SNAPSHOT" :upgrade :kixi]
                 [cider/cider-nrepl "0.15.1"]
                 ;; these are dependencies around running the server in the repl
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.namespace    "0.2.11"]
                 [org.clojure/tools.nrepl        "0.2.12"]
                 [org.clojure/tools.cli "0.3.5"]
                 [environ "1.1.0"]
                 [aero "1.0.0"]
                 [kixi/kixi.comms "0.2.37" :upgrade :kixi]
                 [org.clojure/tools.analyzer "0.6.9"]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 [ring-middleware-format "0.7.2"] ;; transit
                 ;; not really dependency, dep collision https://groups.google.com/forum/#!topic/clojure/D_s9Drua6D4
                 [spootnik/signal "0.2.1"]]
  :plugins [[lein-ring "0.9.7"]]
  :pedantic? true
  :repl-options {:init-ns user}
  :jvm-opts ["-XX:+HeapDumpOnOutOfMemoryError"
             "-XX:+UseG1GC"
             "-Xloggc:gc.log"
             "-XX:+PrintGCCause"
             "-XX:+UseGCLogFileRotation"
             "-XX:NumberOfGCLogFiles=3"
             "-XX:GCLogFileSize=2M"]
  :profiles
  {:uberjar {:aot [kixi.heimdall.bootstrap]
             :main kixi.heimdall.bootstrap
             :uberjar-name "kixi.heimdall.jar"}
   :dev {:source-paths ["dev"]
         :dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]
                        [clj-http "3.4.1"]
                        [com.cognitect/transit-clj "0.8.290"]]}}
  :aliases {"seed" ["run" "-m" "user/seed" "joplin.edn"]}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
