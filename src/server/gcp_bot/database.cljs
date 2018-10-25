(ns gcp-bot.database)


(def DataDB (atom {
                   :creds {
                           :projectId   "cf-sandbox-sjolicoeur"
                           :keyFilename "creds.json"}
                   }))

(def  version "v70")