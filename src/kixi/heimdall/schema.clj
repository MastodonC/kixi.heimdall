(ns kixi.heimdall.schema
  (:require [clojure.spec :as spec]))

(defn uuid?
  [s]
  (when-not (clojure.string/blank? s)
    (re-find #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$" s)))

;; regex from here http://www.lispcast.com/clojure.spec-vs-schema
(defn email?
  [s]
  (when-not (clojure.string/blank? s)
    (re-find #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}" s)))

(defn password?
  [s]
  (when-not (clojure.string/blank? s)
    (re-find #"(?=.*\d.*)(?=.*[a-z].*)(?=.*[A-Z].*).{8,}" s)))

(spec/def ::group-id uuid?)
(spec/def ::group-name string?)
(spec/def ::group-type #{"user" "group"})
(spec/def ::group-params
  (spec/keys :req-un [::group-name ::group-id]
             :opts [::group-type]))


(spec/def ::uuid #(instance? java.util.UUID %))
(spec/def ::id uuid?)
(spec/def ::username email?)
(spec/def ::created string?)
(spec/def ::exp integer?)
(spec/def ::groups (spec/coll-of ::uuid))
(spec/def ::user-groups (spec/keys :req-un [::groups] :opts []))
(spec/def ::user
  (spec/keys :req-un [::id]
             :opts [::username ::name ::created ::user-groups]))


(spec/def ::password password?)
(spec/def ::login
  (spec/keys :req-un [::username ::password]))


(spec/def ::name string?)
(spec/def ::self-group uuid?)
(spec/def ::user-groups (spec/coll-of ::id))
(spec/def ::auth-token
  (spec/keys :req-un [::id
                      ::username
                      ::name
                      ::created
                      ::user-groups
                      ::self-group
                      ::exp]))


;; Returning error messages with context
(spec/def ::context
  (spec/keys :req []
             :opts []))

(spec/def ::error #{:unauthenticated :user-creation-failed :invalidation-failed :runtime-exception})
(spec/def ::msg (spec/keys :req []
                           :opts []))
(spec/def ::error-map
  (spec/keys :req [::error ::msg]))
