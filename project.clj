(defproject gcp_bot "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :dependencies [[bidi "2.1.3"]
                 [com.cemerick/piggieback "0.2.2"]
                 [com.taoensso/timbre "4.10.0"]
                 [macchiato/hiccups "0.4.1"]
                 [macchiato/core "0.2.14"]
                 [macchiato/env "0.0.6"]
                 [mount "0.1.12"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [org.clojure/core.async "0.4.474"]
                 [funcool/promesa "1.9.0"] 
                 ;; [cljs-http "0.1.45"] ;; doesn't work with node
                 [cljs-http-node "0.1.19-SNAPSHOT"]
                 ;; needed for JDK 9 compatibility
                 [javax.xml.bind/jaxb-api "2.3.0"]]
  :min-lein-version "2.0.0"
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-doo "0.1.7"]
            [macchiato/lein-npm "0.6.4"]
            [lein-figwheel "0.5.16"]
            [lein-cljsbuild "1.1.5"]]
  :npm {:dependencies [[source-map-support "0.4.6"]]
        :write-package-json true}
  :source-paths ["src/server" "target/classes"]
  :target-path "target"
  :profiles
  {:server {:clean-targets ["target"]}
   :dev
   [:server
    {:npm {:package {:main "target/out/gcp_bot.js"
                     :scripts {:start "node target/out/gcp_bot.js"}}}
     :dependencies [[figwheel-sidecar "0.5.16"]]
     :cljsbuild
     {:builds {:dev
               {:source-paths ["env/dev" "src/server"]
                :figwheel     true
                :compiler     {:main          gcp-bot.app
                               :output-to     "target/out/gcp_bot.js"
                               :output-dir    "target/out"
                               :target        :nodejs
                               :optimizations :none
                               :pretty-print  true
                               :source-map    true
                               :source-map-timestamp false
                               :install-deps  true
                               :npm-deps {
                                           "@google-cloud/compute" "0.10.0"
                                           "shelljs" "0.8.2"
                                           "@slack/client" "4.3.1"
                                           "notp" "2.0.3"
                                          "thirty-two" "1.0.2"
                                           }
}}}}
     :figwheel
     {:http-server-root "public"
      :nrepl-port 7000
      :reload-clj-files {:clj true :cljc true}
      :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
     :source-paths ["env/dev"]
     :repl-options {:init-ns user}}]
   :test
   {:cljsbuild
    {:builds
     {:test
      {:source-paths ["env/test" "src/server" "test"]
       :compiler     {:main gcp-bot.app
                      :output-to     "target/test/gcp_bot.js"
                      :target        :nodejs
                      :optimizations :none
                      :pretty-print  true
                      :source-map    true
                      :install-deps  true
                      :npm-deps {
                                  "@google-cloud/compute" "0.10.0"
                                  "shelljs" "0.8.2"
                                 "@slack/client" "4.3.1"
                                 "notp" "2.0.3"
                                 "thirty-two" "1.0.2"
                                  }}}}}
    :doo {:build "test"}
    :dependencies [[pjstadig/humane-test-output "0.8.3"]]}
   :release
   {:npm {:package {:main "target/release/gcp_bot.js"
                    :scripts {:start "node target/release/gcp_bot.js"}}}
    :cljsbuild
    {:builds
     {:release
      {:source-paths ["env/prod" "src/server"]
       :compiler     {:main          gcp-bot.app
                      :output-to     "target/release/gcp_bot.js"
                      :language-in   :ecmascript5
                      :target        :nodejs
                       :optimizations :simple
;                      :optimizations :none
                      :pretty-print  false
                      :install-deps  true
                      :npm-deps {
                                  "@google-cloud/compute" "0.10.0"
                                  "shelljs" "0.8.2"
                                 "@slack/client" "4.3.1"
                                 "notp" "2.0.3"
                                 "thirty-two" "1.0.2"
                                  }}}
      }}}}
  :aliases
  {"build" ["do"
            ["clean"]
            ["npm" "install"]
            ["figwheel" "dev"]]
   "package" ["do"
              ["clean"]
              ["npm" "install"]
              ["with-profile" "release" "npm" "init" "-y"]
              ["with-profile" "release" "cljsbuild" "once" "release"]
;              ["cp" "Manifest" "target/release"]
;              ["cp" "CF-sandbox-sjolicoeur-74991780beb3.json" "target/release"]
]
   "test" ["do"
           ["npm" "install"]
           ["with-profile" "test" "doo" "node"]]})
