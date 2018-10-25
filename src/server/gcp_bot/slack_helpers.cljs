(ns gcp-bot.slack-helpers
  (:require
    [clojure.string :as string]
    [macchiato.fs :as fs]
    [promesa.core :as p]
    [gcp-bot.database :refer [DataDB]]
    ["@slack/client" :as slack-client]
))

(def slack-token  (string/replace (fs/slurp "slack_token.dat") #"\n" ""))

(defn print-to-chat-room [message room-id]
  (p/alet [
        token  slack-token
           web  (slack-client/WebClient. token)
           payload (clj->js {:channel room-id :text message :mrkdwn true})
        ]
    (.chat.postMessage web payload)))

(defn print-to-chat [message]
  (let [
     token  slack-token
      web  (slack-client/WebClient. token)
      room-id (get-in @DataDB [:room-id])
   ]
  (.chat.postMessage web (clj->js {:channel room-id :text message :mrkdwn true}))))


(defn send-response-msg [room-id txt]
  ;; Content-type: application/json
  ;; Authorization: Bearer YOUR_BOTS_TOKEN
 (let [
        token slack-token
        web  (slack-client/WebClient. token)
    ]

     ;; provision qty name
     (let [
            txt-parts (string/split txt #" ")
            [action qty-str group-name] txt-parts
            qty (int qty-str)
            ;; is-provisioning? (= "provision" (first txt-parts))
             is-provisioning? (= "provision" action)
          ]
     (.chat.postMessage web (clj->js {:channel room-id :text (str txt " :: " is-provisioning? " -> " txt-parts)}))
     (.chat.postMessage web (clj->js {:channel room-id :text (str "Provisionning group: " group-name " qty vms " qty )}))
     )))
