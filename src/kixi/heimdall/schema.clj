(ns kixi.heimdall.schema
  (:require [clojure.spec.alpha :as spec]
                        [clojure.spec.gen.alpha :as gen]
            [kixi.heimdall.util :as util]
            [kixi.spec.conformers :as ks]))

(defn email?
  [s]
  (when-not (clojure.string/blank? s)
    (re-find (re-pattern (str "^" ks/email-re-str "$")) s)))

(defn password?
  [s]
  (when-not (clojure.string/blank? s)
    (re-find #"(?=.*\d.*)(?=.*[a-z].*)(?=.*[A-Z].*).{8,}" s)))

(spec/def ::id ks/uuid?)
(spec/def ::username ks/email?)
(spec/def ::created (ks/var-timestamp? :date-time))
(spec/def ::exp integer?)
(spec/def ::pre-signup boolean?)

(spec/def ::user-id ks/uuid?)
(spec/def ::group-id ks/uuid?)
(spec/def ::group-name string?)
(spec/def ::group-type #{"user" "group"})
(spec/def ::group-params
  (spec/keys :req-un [::group-name ::group-id ::user-id ::created ::group-type]))

(spec/def ::groups (spec/coll-of ks/uuid?))
(spec/def ::user-groups (spec/keys :req-un [::groups] :opts []))
(spec/def ::user
  (spec/keys :req-un [::id ::username]
             :opt-un [::name ::created ::user-groups ::pre-signup]))

(spec/def ::stored-user
  (spec/keys :req-un [::id ::username ::name ::created ::group-id ::pre-signup]))


(spec/def ::password password?)
(spec/def ::login
  (spec/keys :req-un [::username ::password]))

(spec/def ::user-invite
  (spec/keys :req-un [::username ::name]))

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
