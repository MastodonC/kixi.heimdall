(ns kixi.heimdall.invites-test
  (:require [kixi.heimdall.invites :refer :all]
            [clojure.test :refer :all]))

(deftest invite-event
  (let [name "foo@bar.com"
        event (create-invite-event name)]
    (is event)
    (is #{:kixi.comms.event/key :kixi.comms.event/version :kixi.comms.event/payload}
        (= (set (keys event))))
    (let [{:keys [url username invite-code]} (:kixi.comms.event/payload event)]
      (is (= username name))
      (is invite-code)
      (is url)
      (is (pos? (.indexOf url invite-code))))))

(deftest invite-event-fail
  (let [name "foo@bar"
        event (create-invite-event name)]
    (is event)
    (is #{:kixi.comms.event/key :kixi.comms.event/version :kixi.comms.event/payload}
        (= (set (keys event))))
    (let [{:keys [error]} (:kixi.comms.event/payload event)]
      (is error))))
