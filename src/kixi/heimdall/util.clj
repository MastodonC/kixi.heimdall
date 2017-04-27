(ns kixi.heimdall.util
  (:require [clj-time.format :as tf]
            [clj-time.core :as t]
            [clojure.java.io :as io]))

(defn db-now
  "getting timestamp of now but in db format"
  []
  (tf/unparse (tf/formatters :date-time) (t/now)))

(defn replacer
  "Calls  replacement function on different types"
  [rfn x]
  (condp = (type x)
    clojure.lang.Keyword (-> x name rfn keyword)
    clojure.lang.MapEntry (update x 0 (partial replacer rfn))
    clojure.lang.PersistentArrayMap (map (partial replacer rfn) x)
    java.lang.String (rfn x)))

(defn underscore->hyphen
  "Converts underscores to hyphens"
  [x]
  (replacer #(clojure.string/replace % #"_" "-") x))

(defn hyphen->underscore
  "Convers hyphens to underscores"
  [x]
  (replacer #(clojure.string/replace % #"-" "_") x))

(defn file-exists?
  [& path]
  (when (and (first path) (.exists (apply io/file path)))
    (apply io/file path)))

(defn create-code
  []
  (letfn [(gen-block [] (->> #(rand-nth (range 65 90))
                             (repeatedly)
                             (take 6)
                             (map char)
                             (apply str)))]
    (clojure.string/join "-" (take 4 (repeatedly gen-block)))))
