(ns kixi.heimdall.unit.invites-test
  (:require [clojure.test :refer :all]
            [clojure.spec.test.alpha :as stest]
            [clojure.test.check :as tc]
            [kixi.heimdall.invites :as sut]))

(def sample-size 100)

(defn check
  [sym]
  (-> sym
      (stest/check {:clojure.spec.test.alpha.check/opts {:num-tests sample-size}})
      first
      stest/abbrev-result
      :failure))

(deftest check-create-invite-event
  (is (nil?
       (check `sut/create-invite-event))))

(deftest check-failed-event
  (is (nil?
       (check `sut/failed-event))))
