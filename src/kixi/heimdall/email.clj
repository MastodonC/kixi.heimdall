(ns kixi.heimdall.email
  (:require [clojure.java.io :as io]
            [clostache.parser :as mustache]
            [kixi.comms :as kcomms]))

(defn- send-email-command!
  [comms user destination subject body-html body-txt]
  (let [mail {:destination destination
              :message {:subject subject
                        :body {:text body-txt
                               :html body-html}}}]
    (kcomms/send-command! comms :kixi.mailer/send-mail
                          "1.0.0"
                          user
                          mail
                          {:kixi.comms.command/partition-key (:kixi.user/id user)})))

(defn- namespace-user-kws
  [user]
  (zipmap (map #(keyword "kixi.user" (name %)) (keys user))
          (vals user)))

(defmulti send-email!
  (fn [email-type comms opts] email-type))

(defmethod send-email!
  :password-reset-request
  [_ comms opts]
  (let [body-html (mustache/render-resource "emails/password-reset-request.html" opts)
        body-txt (mustache/render-resource "emails/password-reset-request.txt" opts)
        subject "Witan For Cities - Password Reset Request"
        destination {:to-addresses [(get-in opts [:user :username])]}]
    (send-email-command! comms (namespace-user-kws (:user opts)) destination subject body-html body-txt)))

(defmethod send-email!
  :user-invite
  [_ comms opts]
  (let [body-html (mustache/render-resource "emails/invite.html" opts)
        body-txt (mustache/render-resource "emails/invite.txt" opts)
        subject "Witan For Cities - You have been invited!"
        destination {:to-addresses [(get opts :username)]}]
    (send-email-command! comms (namespace-user-kws (:user opts)) destination subject body-html body-txt)))
