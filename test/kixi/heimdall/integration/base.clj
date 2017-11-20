(ns kixi.heimdall.integration.base
  (:require [environ.core :refer [env]]
            [kixi.heimdall.integration.repl :as repl]
            [kixi.comms.components.kinesis :as kinesis]
            [taoensso.timbre :as log]
            [amazonica.aws.dynamodbv2 :as ddb]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha :as gen]
            [kixi.heimdall.config :as config]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.service :as service]
            [kixi.heimdall.invites :refer [get-invite]]
            [kixi.heimdall.user :as u :refer [find-by-username]]
            [kixi.spec.conformers :as kc]))

(def system (atom nil))
(def comms-mode (first (keys (:communications (config/config (keyword (env :system-profile "test")))))))
(def wait-tries (Integer/parseInt (env :wait-tries (case comms-mode
                                                     :coreasync "10"
                                                     "300"))))
(def wait-per-try (Integer/parseInt (env :wait-per-try "100")))

(defn uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn rand-str
  ([]
   (rand-str 10))
  ([len]
   (apply str (take len (repeatedly #(if (zero? (rand-int 2)) (char (+ (rand 26) 65)) (char (+ (rand 26) 97))))))))

(defn random-email
  []
  (first (gen/sample (s/gen kc/email?) 1)))

(defn table-exists?
  [endpoint table]
  (try
    (ddb/describe-table {:endpoint endpoint} table)
    (catch Exception e false)))

(defn clear-tables
  [endpoint table-names]
  (doseq [sub-table-names (partition-all 10 table-names)]
    (doseq [table-name sub-table-names]
      (ddb/delete-table {:endpoint endpoint} :table-name table-name))
    (loop [tables sub-table-names]
      (when (not-empty tables)
        (recur (doall (filter (partial table-exists? endpoint) tables)))))))

(defn teardown-kinesis!
  [{:keys [endpoint dynamodb-endpoint streams app profile]}]
  (log/info "Deleting dynamo tables ...")
  (clear-tables dynamodb-endpoint [(kinesis/event-worker-app-name app profile)
                                   (kinesis/command-worker-app-name app profile)])

  (log/info "Deleting streams...")
  (kinesis/delete-streams! endpoint (vals streams)))

(defn instrument-fns
  []
  (stest/instrument ['kixi.heimdall.service/signup-user!]))


(defn cycle-system
  [all-tests]
  (kixi.comms/set-verbose-logging! true)
  (instrument-fns)
  (set! *assert* true)
  (repl/start)
  (try
    (all-tests)
    (finally
      (set! *assert* false)
      (let [config (config/config (keyword (env :system-profile "test")))
            {:keys [endpoint dynamodb-endpoint streams app profile] :as args}
            (first (vals (:communications config)))
            _ (log/info app profile)]
        (repl/stop)

        (when (= :kinesis
                 (first (keys (:communications config))))
          (teardown-kinesis! args))

        (log/info "Finished")))))

(def comms (atom nil))

(defn extract-comms
  [all-tests]
  (reset! comms (:communications @repl/system))
  (all-tests)
  (reset! comms nil))

(def db-session (atom nil))

(defn extract-db-session
  [all-tests]
  (reset! db-session (:db @repl/system))
  (all-tests)
  (reset! db-session nil))

(defn wait-for
  ([fn fail-fn]
   (wait-for fn fail-fn 1))
  ([fn fail-fn cnt]
   (if (< cnt wait-tries)
     (let [value (fn)]
       (if value
         value
         (do
           (log/info "waiting ... " cnt)
           (Thread/sleep wait-per-try)
           (recur fn fail-fn (inc cnt)))))
     (do
       (log/info "calling fail fn")
       (fail-fn)))))

(defn invite-user!
  [{:keys [username name] :as user}]
  (let [[_ {:keys [kixi.comms.event/payload]}] (service/invite-user! @db-session @comms {:username username :name name})
        {:keys [invite-code]} payload]
    (wait-for #(get-invite @db-session username)
              #(log/error "COULDN'T CREATE USER" username "1"))))

(defn create-user!
  [{:keys [username name] :as user}]
  (let [[_ {:keys [kixi.comms.event/payload]}] (service/invite-user! @db-session @comms {:username username :name name})
        {:keys [invite-code]} payload]
    (wait-for #(get-invite @db-session username)
              #(log/error "COULDN'T CREATE USER" username "1"))
    (service/signup-user! @db-session @comms (assoc user
                                                    :invite-code invite-code))
    (wait-for #(find-by-username @db-session user)
              #(log/error "COULDN'T CREATE USER" username "2"))))

(defn create-group!
  [session owner-name group-name]
  (let [_ (create-user! {:username owner-name :password "Local123" :name "randomName"})
        group-id (uuid)
        owner-id (:id (u/find-by-username session {:username owner-name}))
        _ (#'service/create-group session {:group-id group-id
                                           :created (util/db-now)
                                           :group-name group-name
                                           :user-id (str owner-id)
                                           :group-type "group"})]
    [owner-id group-id]))
