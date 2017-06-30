(ns kixi.heimdall.patches.patch-20170629
  (:require [kixi.comms :as comms]
            [kixi.heimdall.user :as user]
            [kixi.heimdall.group :as group]
            [kixi.heimdall.member :as member]
            [kixi.heimdall.service :as service]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Patch - Heimdall Event Idempotency
;; Date: 29/06/2017
;; Created by: AW, TC
;;
;; The original, v1.0.0 events for both user and group creation were not
;; idempotent as they didn't preserve the user or group IDs in the event. During
;; a restore process new IDs would have been created, breaking other parts of
;; the system which relied on those IDs being true. To rectify this, we have
;; add the IDs to the events and have re-sent them. It's our understanding that
;; other parts of the system don't contact Heimdall to check the validity of an
;; ID and so will assume they are true and correct - this allows us to drop all
;; relevant v1.0.0 events during a restore and once the v2.0.0 events have been
;; sent and processed, the correct state will be restored.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn db
  []
  (:db @kixi.heimdall.application/system))

(defn comms
  []
  (:communications @kixi.heimdall.application/system))

(defn send-user-created-event!
  [u]
  (service/send-user-created-event! (comms) u))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Users
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def users
  (map #(dissoc % :password) (user/all (db))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Groups
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def groups
  (group/all (:db @kixi.heimdall.application/system)))

(defn send-group-created-event!
  [group]
  (comms/send-event! (comms) :kixi.heimdall/group-created "2.0.0" group))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn apply-patch!
  []
  (run! send-user-created-event! users)
  (run! send-group-created-event! groups))
