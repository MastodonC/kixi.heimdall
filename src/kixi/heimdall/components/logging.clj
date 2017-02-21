(ns kixi.heimdall.components.logging
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [environ.core :refer [env]]))

(def logback-timestamp-opts
  {:pattern  "yyyy-MM-dd HH:mm:ss,SSS"
   :locale   :jvm-default
   :timezone :utc})

(def upper-name
  (memoize
   (fn [level]
     (str/upper-case (name level)))))

(defn stacktrace-element->vec
  [^StackTraceElement ste]
  [(.getFileName ste) (.getLineNumber ste) (.getMethodName ste)])

(defn exception->map
  [^Throwable e]
  (merge
   {:type (str (type e))
    :trace (mapv stacktrace-element->vec (.getStackTrace e))}
   (when-let [m (.getMessage e)]
     {:message m})
   (when-let [c (.getCause e)]
     {:cause (exception->map c)})))

(defn not-empty-str
  [s]
  (when (not-empty s) s))

(defn log->json
  [data]
  (let [opts (get-in data [:config :options])
        exp (some-> (force (:?err data)) exception->map)
        msg (or (not-empty-str (force (:msg_ data))) (:message exp))]
    {:level (:level data)
     :namespace (:?ns-str data)
     :application "kixi.heimdall"
     :file (:?file data)
     :line (:?line data)
     :exception exp
     :hostname (force (:hostname_ data))
     :message msg
     "@timestamp" (force (:timestamp_ data))}))

(defn json->out
  [data]
  (json/generate-stream
   (log->json data)
   *out*)
  (prn))

(defrecord Log
    [level ns-blacklist metrics]
  component/Lifecycle
  (start [component]
    (when-not (:full-config component)
      (let [full-config {:level (keyword (env :log-level level))
                         :ns-blacklist ns-blacklist
                         :timestamp-opts logback-timestamp-opts ; iso8601 timestamps
                         :options {:stacktrace-fonts {}}
                         :appenders  {:direct-json {:enabled?   true
                                                    :async?     false
                                                    :output-fn identity
                                                    :fn json->out}}}]
        (log/merge-config! full-config)
        (log/handle-uncaught-jvm-exceptions!
         (fn [throwable ^Thread thread]
           (log/error throwable (str "Unhandled exception on " (.getName thread)))))
        (assoc component :full-config full-config))))
  (stop [component]
    (when (:full-config component)
      (dissoc component :full-config))))
