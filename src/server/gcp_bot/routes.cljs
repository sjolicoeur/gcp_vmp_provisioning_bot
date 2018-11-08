(ns gcp-bot.routes
  (:require
    [bidi.bidi :as bidi]
    [macchiato.env :as config]
    [cljs.repl :refer [dir]]
    [shelljs :as shelljs]
    [macchiato.middleware.restful-format :as rf]
    [gcp-bot.handlers :as handlers]
))

(println "THIS IS MY USER CONFIG :: ")
(println (:gcp-auth-key (config/env) "PLEASE CONFIGURE THE 'GCP_AUTH_KEY' ENV VAR"))

(def https (js/require "https"))


(def routes
  ["/" {"index.html" handlers/home
        "list"  handlers/show-vm-list
        "shoutouts"  handlers/show-shoutouts
        "slack/events" (rf/wrap-restful-format handlers/parse-events) ;; needed by the slack api to send us data
}]
)

(defn router [req res raise]
  (if-let [{:keys [handler route-params]} (bidi/match-route* routes (:uri req) req)]
    (handler (assoc req :route-params route-params) res raise)
    (handlers/not-found req res raise)))
