(defproject com.phronemophobic/membrane "0.11.111-beta"
  :description "A platform agnostic library for creating user interfaces"
  :url "https://github.com/phronmophobic/membrane"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :dependencies [[org.clojure/clojure "1.10.3"]

                 [net.n01se/clojure-jna "1.0.0"
                  :exclusions [net.java.dev.jna/jna]]
                 [net.java.dev.jna/jna "5.10.0"]
                 [org.clojure/core.async "1.4.627"]

                 [com.rpl/specter "1.1.3"]
                 [org.apache.commons/commons-text "1.9"]

                 [org.clojure/core.cache "1.0.225"]
                 [com.phronemophobic/cljs-cache "0.1.8"]
                 ;; these two go together
                 ;; built and installed locally!
                 ;; [com.oracle/appbundler "1.0ea-local"]
                 ;; [org.apache.ant/ant "1.10.5"]

                 ;; For babaskha pod work
                 [nrepl/bencode "1.1.0"]
                 ]
  :aot [
        ]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.18"]
            [lein-codox "0.10.7"]
            ]

  :source-paths ["src"]
  :java-source-paths ["src-java"]
  :javac-options     ["-target" "1.8" "-source" "1.8"]
  ;; :java-cmd "/Library/Java/JavaVirtualMachines/jdk1.8.0_131.jdk/Contents/Home/bin/java"
    ;; :java-cmd "/Library/Java/JavaVirtualMachines/jdk1.8.0_60.jdk/Contents/Home/bin/java"
  :jvm-opts [;;"-Dapple.awt.UIElement=false"
             ;; "-Djna.debug_load=true"
             ;; "-Djna.debug_load.jna=true"
             ;; "-Xmx14g"

             ;; for testing graalvm
             ;; "-Dclojure.compiler.direct-linking=true"
             ;; "-XstartOnFirstThread"
             ;; useful for clj-async-profiler
             ;; "-Djdk.attach.allowAttachSelf"
             ;; "-XX:+UnlockDiagnosticVMOptions"
             ;; "-XX:+DebugNonSafepoints"
             ; this prevents JVM from doing optimizations which can remove stack traces from NPE and other exceptions
             "-XX:-OmitStackTraceInFastThrow"
             ]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"
                 "space-maven" "https://packages.jetbrains.team/maven/p/skija/maven"}
  :profiles
  {
   :clojure-1.10.3 {:dependencies [[org.clojure/clojure "1.10.3"]]}
   :dev {:dependencies
         [
          [cider/piggieback "0.4.0"]

          [org.clojure/test.check "0.9.0"]
          [criterium "0.4.5"]
          [com.clojure-goes-fast/clj-async-profiler "0.4.1"]
          [figwheel-sidecar "0.5.18" :exclusions [org.clojure/tools.nrepl]]
          [org.clojure/data.json "1.0.0"]
          ]
         :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
         ;;:source-paths ["cljs_src"]
         }
   :provided {:dependencies [[org.clojure/clojurescript "1.10.764"]
                             [com.phronemophobic/vdom "0.2.2"]
                             [com.googlecode.lanterna/lanterna "3.1.1"]
                             [spec-provider "0.4.14"]
                             [mogenslund/liquid "2.0.3"]
                             [re-frame "1.2.0"]

                             [cljfx "1.7.16"]

                             [com.fulcrologic/fulcro "3.5.8"]

                             ;; skia
                             [com.phronemophobic.membrane/skialib-macosx-x86-64 "0.9.31.0-beta"]
                             [com.phronemophobic.membrane/skialib-linux-x86-64 "0.9.31.0-beta"]
                             [com.phronemophobic.membrane/skialib-macosx-aarch64 "0.9.31.0-beta"]

                             ;; skija
                             [org.jetbrains.skija/skija-macos-arm64 "0.93.4"]
                             [org.jetbrains.skija/skija-macos-x64 "0.93.4"]
                             [org.jetbrains.skija/skija-linux "0.93.1"]
                             [org.lwjgl/lwjgl "3.3.0"]
                             [org.lwjgl/lwjgl "3.3.0" :classifier "natives-macos"]
                             [org.lwjgl/lwjgl "3.3.0" :classifier "natives-macos-arm64"]
                             [org.lwjgl/lwjgl "3.3.0" :classifier "natives-linux"]
                             [org.lwjgl/lwjgl-glfw "3.3.0"]
                             [org.lwjgl/lwjgl-glfw "3.3.0" :classifier "natives-macos"]
                             [org.lwjgl/lwjgl-glfw "3.3.0" :classifier "natives-macos-arm64"]
                             [org.lwjgl/lwjgl-glfw "3.3.0" :classifier "natives-linux"]
                             [org.lwjgl/lwjgl-opengl "3.3.0"]
                             [org.lwjgl/lwjgl-opengl "3.3.0" :classifier "natives-macos"]
                             [org.lwjgl/lwjgl-opengl "3.3.0" :classifier "natives-macos-arm64"]
                             [org.lwjgl/lwjgl-opengl "3.3.0" :classifier "natives-linux"]
                             ]}
   :uberjar {:dependencies [[com.github.clj-easy/graal-build-time "0.1.0"]]
             :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                        "-Dclojure.spec.skip-macros=true"]
             :main pod.tddpirate.membrane
             :aot [pod.tddpirate.membrane]}
   }

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
                           }}
               {:id "vdomtest"
                :source-paths ["src"]
                :figwheel {:on-jsload "membrane.vdomtest/on-js-reload"}
                :compiler {:main membrane.vdom
                           :asset-path "js/compiled/out.vdomtest"
                           :output-to "resources/public/js/compiled/vdomtest.js"
                           :output-dir "resources/public/js/compiled/out.vdomtest"
                           :source-map-timestamp true
                           :aot-cache true
                           :optimizations :simple
                           :infer-externs true
                           }}
               {:id "buildertest"
                :source-paths ["src"]
                :figwheel {:on-jsload "membrane.buildertest/on-js-reload"}
                :compiler {:main membrane.builder
                           :asset-path "js/compiled/out.buildertest"
                           :output-to "resources/public/js/compiled/buildertest.js"
                           :output-dir "resources/public/js/compiled/out.buildertest"
                           :aot-cache true
                           :source-map-timestamp true
                           ;; :optimizations :simple
                           }}
               {:id "autouitest"
                :source-paths ["src"]
                :figwheel {:on-jsload "membrane.autouitest/on-js-reload"}
                :compiler {:main membrane.autoui
                           :asset-path "js/compiled/out.autouitest"
                           :output-to "resources/public/js/compiled/autouitest.js"
                           :output-dir "resources/public/js/compiled/out.autouitest"
                           :aot-cache true
                           :source-map-timestamp true
                           ;; :optimizations :simple
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
