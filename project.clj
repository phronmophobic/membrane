(defproject com.phronemophobic/membrane "0.9.6-beta-SNAPSHOT"
  :description "A platform agnostic library for creating user interfaces"
  :url "http://example.com/FIXME"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.520"]


                 [net.n01se/clojure-jna "1.0.0"
                  :exclusions [net.java.dev.jna/jna]]
                 [net.java.dev.jna/jna "5.2.0"]
                 [org.clojure/core.async "0.4.490"]

                 ;; [figwheel-sidecar "0.5.0-2"]
                 [figwheel-sidecar "0.5.18" :exclusions [org.clojure/tools.nrepl]]

                 [com.rpl/specter "1.1.1"]
                 [org.apache.commons/commons-text "1.8"]

                 ;; these two go together
                 ;; built and installed locally!
                 ;; [com.oracle/appbundler "1.0ea-local"]
                 ;; [org.apache.ant/ant "1.10.5"]
]

  :aot [
        ]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.18"]
            [lein-codox "0.10.7"]
            ]

  :source-paths ["src"]
  :java-source-paths ["src-java"]
  ;; :java-cmd "/Library/Java/JavaVirtualMachines/jdk1.8.0_131.jdk/Contents/Home/bin/java"
    ;; :java-cmd "/Library/Java/JavaVirtualMachines/jdk1.8.0_60.jdk/Contents/Home/bin/java"
  :jvm-opts [;;"-Dapple.awt.UIElement=false"
             ;; "-Djna.debug_load=true"
             ;; "-Djna.debug_load.jna=true"
             ;; "-Xmx14g"
             ]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :profiles
  {:dev {:dependencies
         [
          [cider/piggieback "0.4.0"]
          [re-frame "0.11.0"]
          [org.clojure/test.check "0.9.0"]
          ]
         :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
         ;;:source-paths ["cljs_src"]
         }}

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  :cljsbuild {:builds
              [{:id "webgltest"
                :source-paths ["src"]
                :figwheel {:on-jsload "membrane.webgltest/on-js-reload"}
                :compiler {:main membrane.webgltest
                           :asset-path "js/compiled/out.webgltest"
                           :output-to "resources/public/js/compiled/webgltest.js"
                           :output-dir "resources/public/js/compiled/out.webgltest"
                           :source-map-timestamp true
                           ;;:optimizations :whitespace
                           }}]}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             })
