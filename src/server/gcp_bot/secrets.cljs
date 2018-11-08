(ns gcp-bot.secrets
  (:require
      ["thirty-two" :as base32]
      [macchiato.env :as config]
      [clojure.string :as string]
))

(def secret-key (:gcp-auth-key (config/env)))
(def google-api-key (:google-api-key (config/env)))

(defn generate-topt-url []
  (let [
        encoded (.encode base32 secret-key)
        encoded-for-google   (string/replace (.toString encoded) #"=" "")
        uri (str "otpauth://totp/provisioning-bot?secret=" encoded-for-google)
        ]
    (println "this is the google string:: " uri)))


(generate-topt-url)

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
      )))
