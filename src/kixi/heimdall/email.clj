(ns kixi.heimdall.email
  (:require [clojure.java.io :as io]
            [clostache.parser :as mustache]
            [kixi.comms :as kcomms]))

(defn- send-email-command!
  [comms user destination subject body-html body-txt]
  (let [mail {:destination destination
              :source "support@mastodonc.com"
              :message {:subject subject
                        :body {:text body-txt
                               :html body-html}}}]
    (kcomms/send-command! comms :kixi.mailer/send-mail
                          "1.0.0"
                          user
                          mail)))

(defmulti send-email!
  (fn [email-type comms opts] email-type))

(defmethod send-email!
  :password-reset-request
  [_ comms opts]
  (let [body-html (mustache/render-resource "emails/password-reset-request.html" opts)
        body-txt (mustache/render-resource "emails/password-reset-request.txt" opts)
        subject "Witan For Cities - Password Reset Request"
        destination {:to-addresses [(get-in opts [:user :username])]}]
    (send-email-command! comms (:user opts) destination subject body-html body-txt)))
