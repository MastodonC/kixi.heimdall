(ns kixi.heimdall.invites-test
  (:require [kixi.heimdall.invites :refer :all]
            [clojure.test :refer :all]))

(deftest invite-codes
  (let [code-a (create-invite-code)
        code-b (create-invite-code)]
    (is code-a)
    (is code-b)
    (is (not= code-a code-b))))

(deftest invite-event
  (let [name "foo@bar.com"
        event (create-invite-event name)]
    (is event)
    (is #{:event/key :event/version :event/payload}
        (= (set (keys event))))
    (let [{:keys [url username invite-code]} (:event/payload event)]
      (is (= username name))
      (is invite-code)
      (is url)
      (is (pos? (.indexOf url invite-code))))))
