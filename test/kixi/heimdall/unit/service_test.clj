(ns kixi.heimdall.unit.service-test
  (:require [clojure.test :refer :all]
            [clojure.spec.test.alpha :as stest]
            [clojure.test.check :as tc]
            [kixi.heimdall.service :as sut]))

(def sample-size 100)

(defn check
  [sym]
  (-> sym
      (stest/check {:clojure.spec.test.alpha.check/opts {:num-tests sample-size}})
      first
      stest/abbrev-result
      :failure))

(deftest check-user-invite-event
  (is (nil?
       (check `sut/user-invite-event))))
