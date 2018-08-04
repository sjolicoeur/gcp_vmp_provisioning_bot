(ns gcp-bot.routes
  (:require
    [bidi.bidi :as bidi]
    [hiccups.runtime]
    [cljs.repl :refer [dir]]
    [macchiato.util.response :as r]
    ["@google-cloud/compute" :as Compute]
    [promesa.core :as p]
    [promesa.async-cljs :refer-macros [async] :include-macros true]
    [shelljs :as shelljs]
    [macchiato.fs :as fs]
)
  (:require-macros
    [hiccups.core :refer [html]]
    ;;[clojure.core.strint :as strint]
    ))


(defn create-ssh-key [name]
  (let [
         private-key-name (str "id_rsa_" name)
         public-key-name (str private-key-name ".pub")
         ;;cmd-code (str "ssh-keygen -t rsa -b 4096 -C 'pivotal@pivotal.io' -f " private-key-name " -N ''" )
         cmd-code (str "ssh-keygen -o -a 100 -t ed25519 -C 'pivotal@pivotal.io' -f " private-key-name " -N ''" )
         ]
    (println cmd-code)
    (.exec shelljs  cmd-code)
    (let [
           private-key (fs/slurp private-key-name)
           public-key (fs/slurp public-key-name)
           output {:private-key private-key :public-key public-key}
           ]
      (println output)
      (.exec shelljs  (str "rm " private-key-name))
      (.exec shelljs  (str "rm " public-key-name))
      output
      )
  ))

(def  version "v52")


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
        compute (Compute (clj->js {
                                    :projectId "cf-sandbox-sjolicoeur"
                                    :keyFilename "/Users/sjolicoeur/Dev/gcp_bot/CF-sandbox-sjolicoeur-74991780beb3.json"}))
        zone (.zone compute "us-central1-a")
        vm-operations (.getVMs zone)
       ]
    (async
     (-> (.getVMs zone)
         js/Promise.resolve
         (p/then
          (fn [data]
            (println "-promise-data>" data (js->clj data))
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
                                              (println "processing -> " data)
                                              (get-ip data))
                                            firstr)]

                          (map (fn [data] (first data)) clj-resolved)
                          ips))))))))
))

;; TODO: put the script in a separte file and read it in
;; TODO: connect to slack
#_(defn create-ssh-key [name]
  ;; ssh-keygen -t rsa -b 4096 -C "pivotal@pivotal.io" -f id_resa -N ''

  )
(defn vm-created-callback [err vm operation]
  ;;operation lets you check the status of long-running tasks.
  (.on operation "error" (fn [err] (println err)))
  (.on operation "running" (fn [metadata] (println "GOT ME SOME METADATA") (println metadata)))
  (.on operation "complete"
       (fn [metadata]
         ;; Virtual machine created!
         (println " ----> VM CREATED")
         (println (aget vm "name") (aget vm "id") (aget metadata "targetId"))
         (println
           (.getMetadata vm
                         (fn [err data]
                           (let [cdata   (js->clj data)
                                 configs (first (get-in cdata ["networkInterfaces"]))
                                 access  (first (get-in configs ["accessConfigs"]))]
                             (println "public ip for this machine is" (get-in access ["natIP"])))))))))

(defn create-vm [name qty]
 (let [
   compute (Compute (clj->js {:projectId "cf-sandbox-sjolicoeur" :keyFilename "/Users/sjolicoeur/Dev/gcp_bot/CF-sandbox-sjolicoeur-74991780beb3.json"}))
   zone (.zone compute "us-central1-a")
   ssh-keys (create-ssh-key name)
   VM (.createVM zone name (clj->js {
                                      :os "ubuntu"
                                      :http true
  :metadata {
    :items [
      {
        :key "startup-script",
        :value "#!/bin/bash
# Install git
echo \"performing apt update\"
apt-get update
echo \"performing install of coreutils and git\"
apt-get --assume-yes install git  coreutils
echo \"performing install of ag and stress\"
apt-get --assume-yes install  stress  silversearcher-ag
"
      }
      {
        :key   "ssh-keys"
        :value (str "pivotal:" (:public-key ssh-keys))
        }
    ]
  }
:networkInterfaces [ {:network "global/networks/default" } ]
 }) vm-created-callback)]))

(defn home [req res raise]
  (let [name (str "ubuntu-http-" version)
        vm   (create-vm name 1)]
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
 (println "\n\n\n\n\n=========\n" existing-vms)
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
        "list"  show-vm-list}]

)

(defn router [req res raise]
  (if-let [{:keys [handler route-params]} (bidi/match-route* routes (:uri req) req)]
    (handler (assoc req :route-params route-params) res raise)
    (not-found req res raise)))
