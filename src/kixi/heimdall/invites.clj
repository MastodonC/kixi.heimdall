(ns kixi.heimdall.invites
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [taoensso.timbre :as log]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [kixi.heimdall.user :as user]
            [kixi.heimdall
             [schema :as schema]
             [util :as util]]
            [kixi.spec.conformers :as ks]))

(def invites-table "invites")

(defn invite-code->url
  [ic un]
  (str "/#/invite?ic=" ic "&un=" un))

(s/def ::reason #{:invalid-data :user-signedup})

(s/def ::explain
  (s/coll-of (s/keys) :min 1))

(s/def ::failed-payload
  (s/keys :req-un [::reason
                   ::schema/username]
          :opt-un [::explain]))

(s/def ::failed-event
  (s/and
   (s/keys :req [:kixi.comms.event/key
                 :kixi.comms.event/version
                 :kixi.comms.event/payload])
   #(= :kixi.heimdall/invite-failed
       (:kixi.comms.event/key %))
   #(= "2.0.0"
       (:kixi.comms.event/version %))
   #(s/valid? ::failed-payload
              (:kixi.comms.event/payload %))))

(s/fdef failed-event
        :args (s/cat :username ::schema/username
                     :reason ::reason
                     :explain (s/? ::explain))
        :fn (fn [{:keys [args ret]}]
              (and (= (:username args) (get-in ret [:kixi.comms.event/payload :username]))))
        :ret ::failed-event)

(defn failed-event
  ([username reason]
   (failed-event username reason nil))
  ([username reason explain]
   {:kixi.comms.event/key :kixi.heimdall/invite-failed
    :kixi.comms.event/version "2.0.0"
    :kixi.comms.event/payload (merge {:reason reason
                                      :username username}
                                     (when explain
                                       {:explain explain}))}))

(def invite-code-re-str "(?:[A-Z]{6}-){3}[A-Z]{6}")

(s/def ::invite-code #(re-matches (re-pattern invite-code-re-str) %))


(s/def ::url #(re-matches (re-pattern (str "/#/invite\\?ic="
                                           invite-code-re-str
                                           "\\&un="
                                           ks/email-re-str))
                          %))

(s/def ::invited-user (s/keys :req-un [::schema/id ::schema/username]))
(s/def ::user ::invited-user)

(s/def ::create-payload
  (s/keys :req-un [::user
                   ::invite-code
                   ::url]))

(s/def ::create-invite-event
  (s/and
   (s/keys :req [:kixi.comms.event/key
                 :kixi.comms.event/version
                 :kixi.comms.event/payload])
   #(= :kixi.heimdall/invite-created
       (:kixi.comms.event/key %))
   #(= "2.0.0"
       (:kixi.comms.event/version %))
   #(s/valid? ::create-payload
              (:kixi.comms.event/payload %))))

(s/fdef create-invite-event
        :args (s/cat :user ::user)
        :ret ::create-invite-event)

(defn create-invite-event
  [user]
  (let [ic (util/create-code)]
    {:kixi.comms.event/key :kixi.heimdall/invite-created
     :kixi.comms.event/version "2.0.0"
     :kixi.comms.event/payload {:user user
                                :invite-code ic
                                :url (invite-code->url ic (:username user))}}))

(defn save!
  [db invite-code username']
  (let [username (clojure.string/lower-case username')]
    (db/put-item db
                 invites-table
                 {:username username
                  :invite-code invite-code
                  :created-at (util/db-now)}
                 {:return :none})))

(defn get-invite
  [db username]
  (db/get-item db
               invites-table
               {:username username}
               {:consistent? true}))

(defn consume!
  [db invite-code username']
  (let [username (clojure.string/lower-case username')
        item (get-invite db username)]
    (when (= invite-code (:invite-code item))
      (nil? (db/delete-item db
                            invites-table
                            {:username username}
                            {:consistent? true})))))
