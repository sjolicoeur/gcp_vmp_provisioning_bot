(ns gcp-bot.gcp-helpers
  (:require
    [gcp-bot.database :refer [DataDB version]]
    ["@google-cloud/compute" :as Compute]
    [gcp-bot.slack-helpers :as slack-helpers]
    [macchiato.fs :as fs]
    [promesa.core :as p]
    [gcp-bot.secrets :as secrets]
    [shelljs :as shelljs]
;    [macchiato.util.response :as r] ;; is this a smell to move the func that use this to handlers??
    [promesa.async-cljs :refer-macros [async] :include-macros true]
))

(def startup-script  (fs/slurp "scripts/start_up.sh"))


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
                             (slack-helpers/print-to-chat (str "- VM " (aget vm "name") " `ssh -i id_rsa pivotal@" ip "` "))
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
        ssh-keys (secrets/create-ssh-key name-prefix)
        vms   (map (fn [vm-name] (create-vm vm-name name-prefix ssh-keys)) names)]
   (slack-helpers/print-to-chat (str "# Keys for VM in group " name-prefix ": \n private-key: ```" (:private-key ssh-keys) "``` \n # Publick key: ```" (:public-key ssh-keys) "```" ))

  ;; store creds in db
  (swap! DataDB assoc-in [:vms (keyword name-prefix)] {:creds ssh-keys :ip-list [] })
  vms
))





(defn get-ip  [data]
  (let [cdata     (js->clj data)
        configs   (first (get-in cdata ["networkInterfaces"]))
        access    (first (get-in configs ["accessConfigs"]))
        ip        (get-in access ["natIP"])
        name      (get-in data ["name"])
        machineId (get-in data ["id"])]

    {:ip ip :id machineId :name name}))


(defn listVms []
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

