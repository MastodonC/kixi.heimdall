(ns kixi.heimdall.password-resets-test
  (:require [kixi.heimdall.password-resets :refer :all]
            [clojure.test :refer :all]))

(deftest reset-event
  (let [name "foo@bar.com"
        event (create-reset-event {:kixi.user/username name} nil)]
    (is event)
    (is #{:kixi.comms.event/key :kixi.comms.event/version :kixi.comms.event/payload}
        (= (set (keys event))))
    (let [{:keys [url user reset-code] :as p} (:kixi.comms.event/payload event)]
      (is (= (:kixi.user/username user) name) (str p))
      (is reset-code)
      (is url)
      (is (pos? (.indexOf url reset-code))))))

(deftest reset-event-reject
  (let [name "foo@bar.com"
        event (reject-reset-event "foo reason" name)]
    (is event)
    (is #{:kixi.comms.event/key :kixi.comms.event/version :kixi.comms.event/payload}
        (= (set (keys event))))
    (let [{:keys [reason username]} (:kixi.comms.event/payload event)]
      (is (= username name))
      (is (= "foo reason" reason)))))

(deftest reset-event-complete
  (let [name "foo@bar.com"
        event (create-reset-completed-event name)]
    (is event)
    (is #{:kixi.comms.event/key :kixi.comms.event/version :kixi.comms.event/payload}
        (= (set (keys event))))
    (let [{:keys [username]} (:kixi.comms.event/payload event)]
      (is (= username name)))))
