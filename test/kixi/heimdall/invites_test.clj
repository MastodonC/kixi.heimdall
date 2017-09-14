(ns kixi.heimdall.invites-test
  (:require [kixi.heimdall.invites :refer :all]
            [clojure.test :refer :all]))

(deftest invite-event
  (let [name "foo@bar.com"
        event (create-invite-event name)]
    (is event)
    (is #{:kixi.comms.event/key :kixi.comms.event/version :kixi.comms.event/payload}
        (= (set (keys event))))
    (let [{:keys [url user invite-code]} (:kixi.comms.event/payload event)]
      (is (= user name))
      (is invite-code)
      (is url)
      (is (pos? (.indexOf url invite-code))))))

(deftest invite-event-fail
  (let [name "foo@bar"
        event (failed-event "You failed" name)]
    (is event)
    (is #{:kixi.comms.event/key :kixi.comms.event/version :kixi.comms.event/payload}
        (= (set (keys event))))
    (let [{:keys [reason]} (:kixi.comms.event/payload event)]
      (is reason))))
