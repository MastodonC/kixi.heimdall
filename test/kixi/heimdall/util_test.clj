(ns kixi.heimdall.util-test
  (:require [kixi.heimdall.util :refer :all]
            [clojure.test :refer :all]))

(deftest codes
  (let [code-a (create-code)
        code-b (create-code)]
    (is code-a)
    (is code-b)
    (is (not= code-a code-b))))
