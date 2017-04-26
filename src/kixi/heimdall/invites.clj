(ns kixi.heimdall.invites
  (:require [taoensso.timbre :as log]
            [clojure.spec :as s]
            [kixi.heimdall.components.database :as db]
            [kixi.heimdall.util :as util]
            [kixi.heimdall
             [schema :as schema]
             [util :as util]]))

(def invites-table "invites")

(defn create-invite-code
  []
  (letfn [(gen-block [] (->> #(rand-nth (range 65 90))
                             (repeatedly)
                             (take 6)
                             (map char)
                             (apply str)))]
    (clojure.string/join "-" (take 4 (repeatedly gen-block)))))

(defn invite-code->url
  [ic]
  (str "/#/invite/" ic))

(defn create-invite-event
  [username]
  (if (s/valid? ::schema/username username)
    (let [ic (create-invite-code)]
      {:event/key :kixi.heimdall/invite-created
       :event/version "1.0.0"
       :event/payload {:username username
                       :invite-code ic
                       :url (invite-code->url ic)}})
    {:event/key :kixi.heimdall/invite-failed
     :event/version "1.0.0"
     :event/payload {:error (str "The provided username was not valid: " username)}}))

(defn save!
  [db invite-code username]
  (db/put-item db
               invites-table
               {:username username
                :invite-code invite-code}
               {:return :none}))

(defn consume!
  [db invite-code username]
  (let [item (db/get-item db
                          invites-table
                          {:username username}
                          {:consistent? true})]
    (when (= invite-code (:invite-code item))
      (nil? (db/delete-item db
                            invites-table
                            {:username username}
                            {:consistent? true})))))
