(ns gcp-bot.bot-commands
(:require
    [gcp-bot.database :refer [DataDB]]
    [notp :as notp]
    [promesa.core :as p]
    [macchiato.fs :as fs]
    [clojure.string :as string]
    [gcp-bot.secrets :as secrets]
    [gcp-bot.gcp-helpers :as gcp-helpers]
    [gcp-bot.slack-helpers :as slack-helpers]
    [macchiato.util.response :as r] ;; is this a smell to move the func that use this to handlers??
))



(defn validate-token [user-token]
  (let [
        token-is-valid?  (.totp.verify notp user-token secrets/secret-key)
        ]
    (if (nil? token-is-valid?) false true)
;    (not (nil? token-is-valid?))
    )
  )

(defmulti execute-action (fn [action action-args room-id] action))

(defmethod execute-action "echo" [action action-args room-id]
  (println (str "Pong: " action-args)))

(defmethod execute-action "provision" [action action-args room-id]
  (let [
        [qty-str group-name usr-auth-token & extra-args] action-args
        qty (int qty-str)
        token-is-valid? (validate-token usr-auth-token)
        ]
    (println (str "Gonna provision " qty " vm under the group: " group-name " extra args? " extra-args) )
    (if (false? token-is-valid?)
      (slack-helpers/print-to-chat-room "invalid auth will not provision" room-id)
      (p/alet [

             vms (gcp-helpers/create-vms group-name qty)
             ]
            (println "created vms:: " vms)
            ))
    ))
(defmethod execute-action "auth" [action action-args room-id]
  (p/alet [
        token-is-valid? (validate-token (first action-args))
        res (str "the token value is: " token-is-valid?)
        saved-room-id (get-in @DataDB [:room-id])
        ]
    (println res room-id saved-room-id)
    (slack-helpers/print-to-chat-room res room-id)
  )
)
(defmethod execute-action :default [action action-args room-id]
  (println "I do not understand")
)



(defmulti process-msg  (fn [body] (get-in body ["type"]) ))
(defmethod process-msg "url_verification" [msg]
  (-> (get-in msg ["challenge"])
     (r/ok)
     (r/content-type "text/plain")
  )
)
;; event_callback
(defmethod process-msg "event_callback" [msg]
  (let [
         text (get-in msg ["event" "text"])
         room-id (get-in msg ["event" "channel"])
         bot_id (get-in msg ["event" "bot-id"])
         subtype-msg (get-in msg ["event" "subtype"])
        ;; parse text here to do dispatch by action

        [action & action-args] (string/split text #" ")
        ]
    (swap! DataDB assoc-in [:room-id] room-id)
    (println "User asking for action : " action " with args: " action-args )
    (execute-action action action-args room-id)
  #_(if (not= subtype-msg "bot_message")
  (slack-helpers/send-response-msg room-id text))
  (-> "Yay" ;; (get-in msg ["challenge"])
     (r/ok)
     (r/content-type "text/plain")
  )
))