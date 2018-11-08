(ns gcp-bot.handlers
(:require
    [gcp-bot.database :refer [version]]
    [gcp-bot.google-sheets :as google-sheets]
    [hiccups.runtime]
    [promesa.core :as p]
    [gcp-bot.bot-commands :as bot_cmds]
    [gcp-bot.gcp-helpers :as gcp-helpers]
    [macchiato.util.response :as r]
)
(:require-macros
    [hiccups.core :refer [html]]
    ;;[clojure.core.strint :as strint]
    )
)


(defn home [req res raise]
  (let [name (str "ubuntu-http-" version)
        vm   (gcp-helpers/create-vms name 10)]
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

(defn  parse-events [req res raise]
 (p/alet [

      ]
 (let [
        body (js->clj (:body req))
        msg-type (get-in body ["type"])
      ]

  (-> (bot_cmds/process-msg body)
      res
      )))
)

(defn not-found [req res raise]
  (-> (html
        [:html
         [:body
          [:h2 (:uri req) " was not found"]]])
      (r/not-found)
      (r/content-type "text/html")
      (res)))


(defn show-vm-list [req res raise]
 (p/alet [
        existing-vms (p/await (gcp-helpers/listVms))
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

(defn show-shoutouts [req res raise]
  (-> (google-sheets/get-document "1bI5PrMVTT4VbqpYxV2nTwo0H6C4RIGSvTh9ucEjjQHQ" res)))
