(ns kixi.heimdall.password-resets-test
  (:require [kixi.heimdall.password-resets :refer :all]
            [kixi.heimdall.integration.base :refer [random-email]]
            [clojure.test :refer :all]))

(deftest reset-event
  (let [name (random-email)
        event (create-reset-event {:username name} nil)]
    (is event)
    (is #{:kixi.comms.event/key :kixi.comms.event/version :kixi.comms.event/payload}
        (= (set (keys event))))
    (let [{:keys [url user reset-code] :as p} (:kixi.comms.event/payload event)]
      (is (= (clojure.string/lower-case name) (:username user)) (str p))
      (is reset-code)
      (is url)
      (is (pos? (.indexOf url reset-code))))))

(deftest reset-event-reject
  (let [name (random-email)
        event (reject-reset-event "foo reason" name)]
    (is event)
    (is #{:kixi.comms.event/key :kixi.comms.event/version :kixi.comms.event/payload}
        (= (set (keys event))))
    (let [{:keys [reason username]} (:kixi.comms.event/payload event)]
      (is (= (clojure.string/lower-case name) username))
      (is (= "foo reason" reason)))))

(deftest reset-event-complete
  (let [name (random-email)
        event (create-reset-completed-event name)]
    (is event)
    (is #{:kixi.comms.event/key :kixi.comms.event/version :kixi.comms.event/payload}
        (= (set (keys event))))
    (let [{:keys [username]} (:kixi.comms.event/payload event)]
      (is (= (clojure.string/lower-case name) username)))))
