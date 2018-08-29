(ns gcp-bot.routes
  (:require
    [bidi.bidi :as bidi]
    [hiccups.runtime]
    [macchiato.env :as config]
    [cljs.repl :refer [dir]]
    [macchiato.util.response :as r]
    ["@google-cloud/compute" :as Compute]
    [promesa.core :as p]
    [promesa.async-cljs :refer-macros [async] :include-macros true]
    [shelljs :as shelljs]
    [macchiato.fs :as fs]
    [macchiato.middleware.restful-format :as rf]
    [cljs-http.client :as http]
    ["@slack/client" :as slack-client]
    [clojure.string :as string]
    [notp :as notp ]
    ["thirty-two" :as base32]
)
  (:require-macros
    [hiccups.core :refer [html]]
    ;;[clojure.core.strint :as strint]
    ))

(println "THIS IS MY USER CONFIG :: ")
(println (:gcp-auth-key (config/env) "PLEASE CONFIGURE THE 'GCP_AUTH_KEY' ENV VAR"))
;; (println (:gcp-auth-key (config/env) (config/env)))

(def secret-key (:gcp-auth-key (config/env)))

;; validate token
(defn validate-token [user-token]
  (let [
        token-is-valid?  (.totp.verify notp user-token secret-key)
        ]
    (if (nil? token-is-valid?) false true)
    )
  )

(defn generate-topt-url []
  (let [
        encoded (.encode base32 secret-key)
        encoded-for-google   (clojure.string/replace (.toString encoded) #"=" "")
        uri (str "otpauth://totp/provisioning-bot?secret=" encoded-for-google)
        ]
    (println "this is the google string:: " uri)
  )
  )

(generate-topt-url)

(def https (js/require "https"))

(def startup-script  (fs/slurp "scripts/start_up.sh"))

(def slack-token  (clojure.string/replace (fs/slurp "slack_token.dat") #"\n" ""))

(def DataDB (atom {
                   :creds {
                           :projectId   "cf-sandbox-sjolicoeur"
                           :keyFilename "creds.json"}
                   }))

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
  (.chat.postMessage web (clj->js {:channel room-id :text message :mrkdwn true})) ))



(defn create-ssh-key [name]
  (let [
         private-key-name (str "id_rsa_" name)
         public-key-name (str private-key-name ".pub")
         cmd-code (str "ssh-keygen -o -a 100 -t ed25519 -C 'pivotal@pivotal.io' -f " private-key-name " -N ''" )
         ]
    (.exec shelljs  cmd-code)
    (let [
           private-key (fs/slurp private-key-name)
           public-key (fs/slurp public-key-name)
           output {:private-key private-key :public-key public-key}
           ]
      (.exec shelljs  (str "rm " private-key-name))
      (.exec shelljs  (str "rm " public-key-name))
      output
      ) ))

  (def  version "v68")


(defn get-ip  [data]
  (let [cdata     (js->clj data)
        configs   (first (get-in cdata ["networkInterfaces"]))
        access    (first (get-in configs ["accessConfigs"]))
        ip        (get-in access ["natIP"])
        name      (get-in data ["name"])
        machineId (get-in data ["id"])]

    {:ip ip :id machineId :name name}))

(defn listVms [callback]
  (let [
        compute (Compute (clj->js (get-in @DataDB [:creds ] )))
        zone (.zone compute "us-central1-a")
        vm-operations (.getVMs zone)
       ]
    (async
     (-> (.getVMs zone)
         js/Promise.resolve
         (p/then
          (fn [data]
            (let [vms     (first data)
                  results (map (fn [vm] (.getMetadata vm)) vms)]

              vms
              results)))
         (p/then
          (fn [res]
            (let [results (p/all res)]
              (p/then results
                      (fn [resolved]
                        (let [clj-resolved (js->clj resolved)
                              firstr       (map (fn [data] (first data)) clj-resolved)
                              ips          (map
                                            (fn [data]
                                              (get-ip data))
                                            firstr)]

                          (map (fn [data] (first data)) clj-resolved)
                          ips))))))))
))

(defn vm-created-callback [group-name err vm operation]
  ;;operation lets you check the status of long-running tasks.
  (.on operation "error" (fn [err] (println err)))
  (.on operation "running" (fn [metadata] (println "GOT ME SOME METADATA") (println metadata)))
  (.on operation "complete"
       (fn [metadata]
         ;; Virtual machine created!
           (.getMetadata vm
                         (fn [err data]
                           (let [cdata   (js->clj data)
                                 configs (first (get-in cdata ["networkInterfaces"]))
                                 access  (first (get-in configs ["accessConfigs"]))
                                 ip (get-in access ["natIP"])
                                 path-ips  [:vms (keyword group-name) :ip-list]
                                 ips (get-in @DataDB path-ips )
                                 new-ips (conj ips ip)
                                ]
                             (print-to-chat (str "- VM " (aget vm "name") " `ssh -i id_rsa pivotal@" ip "` "))
                             (swap! DataDB assoc-in path-ips  new-ips )
))))))


(defn create-vm [name group-name  ssh-keys]
 (let [
   compute (Compute (clj->js (get-in @DataDB [:creds ] )))
   zone (.zone compute "us-central1-a")
   VM (.createVM zone name (clj->js {
                                      :os "ubuntu"
                                      :http true
  :metadata {
    :items [
      {
        :key "startup-script",
        :value startup-script
      }
      {
        :key   "ssh-keys"
        :value (str "pivotal:" (:public-key ssh-keys))
        }
    ]
  }
:networkInterfaces [ {:network "global/networks/default" } ]
                                     }) (fn [err vm operation]  (vm-created-callback group-name err vm operation)))]
   VM
   ))


(defn create-vms [name-prefix qty] ;; pass callback to send out results
 (let [names (for [i (take qty (range))] (str name-prefix "-" i))
        ssh-keys (create-ssh-key name-prefix)
        vms   (map (fn [vm-name] (create-vm vm-name name-prefix ssh-keys)) names)]
   (print-to-chat (str "# Keys for VM in group " name-prefix ": \n private-key: ```" (:private-key ssh-keys) "``` \n # Publick key: ```" (:public-key ssh-keys) "```" ))

  ;; store creds in db
  (swap! DataDB assoc-in [:vms (keyword name-prefix)] {:creds ssh-keys :ip-list [] })
  vms
))


(defn home [req res raise]
  (let [name (str "ubuntu-http-" version)
        vm   (create-vms name 10)]
    (->
      (html
       [:html
        [:head
         [:link {:rel "stylesheet" :href "/css/site.css"}]]
        [:body
         [:h2 (str "Hello World! " version)]
         [:p
          "Your user-agent is: "
          (str (get-in req [:headers "user-agent"]))]]])
      (r/ok)
      (r/content-type "text/html")
      (res))))


(defn  show-vm-list [req res raise]
 (p/alet [
        existing-vms (p/await (listVms "test"))
      ]
  (-> (html
        [:html
         [:head
          [:link {:rel "stylesheet" :href "/css/site.css"}]
          ]
         [:body
          [:h2 (str "listing the vms!! " version)]
          [:p "ips:" [:ul
                        (for [details existing-vms] [:li (str details)])
                     ]
          ]
          [:p
           "Your user-agent is: "
           (str (get-in req [:headers "user-agent"]))]]])
      (r/ok)
      (r/content-type "text/html")
      (res))))

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
      (print-to-chat-room "invalid auth will not provision" room-id)
      (p/alet [

             vms (create-vms group-name qty)
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
    (print-to-chat-room res room-id)
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
  (send-response-msg room-id text))
  (-> "Yay" ;; (get-in msg ["challenge"])
     (r/ok)
     (r/content-type "text/plain")
  )
))

(defn  parse-events [req res raise]
 (p/alet [

        ;;existing-vms (p/await (listVms "test"))
      ]
 (let [
        body (js->clj (:body req))
       ;; parsed-body (js->clj (js/JSON.parse body) :keywordize-keys true )
        msg-type (get-in body ["type"])
      ]

  (-> (process-msg body)
      ;; (r/ok)
      ;; (r/content-type "text/html")
      (res))))
)

(defn not-found [req res raise]
  (-> (html
        [:html
         [:body
          [:h2 (:uri req) " was not found"]]])
      (r/not-found)
      (r/content-type "text/html")
      (res)))

(def routes
  ["/" {"index.html" home
        "list"  show-vm-list
        "slack/events" (rf/wrap-restful-format parse-events)
}]
)

(defn router [req res raise]
  (if-let [{:keys [handler route-params]} (bidi/match-route* routes (:uri req) req)]
    (handler (assoc req :route-params route-params) res raise)
    (not-found req res raise)))
