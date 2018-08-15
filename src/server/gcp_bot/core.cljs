(ns gcp-bot.core
  (:require
    [gcp-bot.config :refer [env]]
    [gcp-bot.middleware :refer [wrap-defaults]]
    [gcp-bot.routes :refer [router]]
    [macchiato.server :as http]
    [macchiato.middleware.session.memory :as mem]
    [mount.core :as mount :refer [defstate]]
    [taoensso.timbre :refer-macros [log trace debug info warn error fatal]]))

(defn server []
  (mount/start)
  (let [host (or (:host @env) "0.0.0.0")
        port (or (some-> @env :port js/parseInt) 3000)]
    (http/start
      {:handler    router ;; (wrap-defaults router)
       :host       host
       :port       port
       :on-success #(info "gcp_bot started on" host ":" port)})))
