(ns gcp-bot.google-sheets
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [googleapis]
            [macchiato.fs :as fs]
            [promesa.core :as p]
            [promesa.async-cljs :refer-macros [async]]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]
            [macchiato.util.response :as r]
            [gcp-bot.secrets :refer [google-api-key]]))

(def scopes ["https://www.googleapis.com/auth/spreadsheets.readonly"])
(def token-path "google-api-token.json")
(def google googleapis/google)
(def sheets (.sheets google (clj->js {:version "v4"
                         :auth google-api-key})))

(def sheet-id "1bI5PrMVTT4VbqpYxV2nTwo0H6C4RIGSvTh9ucEjjQHQ") ;; shoutouts sheet
; (def sheet-id "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms") ;; google's demo sheet
(def result (atom {}))

(defn sheet->string [rows]
  (->> rows
      (map (fn [row] (clojure.string/join "\t" row)))
      (clojure.string/join "\n")))

(defn get-document [id res]
  (go
    (p/alet [channel (chan)
             document (p/await (.spreadsheets.get sheets (clj->js {:spreadsheetId sheet-id})))
             body (js->clj document)
             sheet-title (get-in body ["data" "sheets" 0 "properties" "title"])
             sheet (p/await (.spreadsheets.values.get sheets (clj->js {:spreadsheetId sheet-id :range sheet-title})
                                                      (fn [err shit]
                                                        (if err
                                                          (go (>! channel err))
                                                          (let [transformed-shit (js->clj shit.data.values)
                                                                string (sheet->string transformed-shit)]
                                                            (go (>! channel string)))))))]
                            (go
                              (-> (<! channel)
                                  r/ok
                                  (r/content-type "text/text")
                                  res)))))
