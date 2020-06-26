(defproject com.phronemophobic/membrane "0.9.8-beta-graalvm"
  :description "A platform agnostic library for creating user interfaces"
  :url "http://example.com/FIXME"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :dependencies [[org.clojure/clojure "1.10.2-alpha1"]

                 [org.clojure/core.async "0.4.490"]

                 [com.rpl/specter "1.1.1"]
                 [org.apache.commons/commons-text "1.8"]

                 [com.googlecode.lanterna/lanterna "3.0.2"]
                 ;; these two go together
                 ;; built and installed locally!
                 ;; [com.oracle/appbundler "1.0ea-local"]
                 ;; [org.apache.ant/ant "1.10.5"]

                 ;; [com.phronemophobic/vdom "0.2.2"]

                 ;; [com.cognitect/transit-cljs "0.8.264"]
]

  :aot [;; membrane.lanterna
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

             ;; useful for clj-async-profiler
             ;; "-Djdk.attach.allowAttachSelf"
             ;; "-XX:+UnlockDiagnosticVMOptions"
             ;; "-XX:+DebugNonSafepoints"
             ]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :profiles
  {:dev {:dependencies
         [
          ;; [cider/piggieback "0.4.0"]
          ;; [re-frame "0.11.0"]
          ;; [org.clojure/test.check "0.9.0"]
          ;; [criterium "0.4.5"]
          ;; [com.clojure-goes-fast/clj-async-profiler "0.4.1"]

          ;; [org.clojure/data.json "1.0.0"]
          ;; [spec-provider "0.4.14"]
          ]
         ;; :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
         ;;:source-paths ["cljs_src"]
         }}
  ;; :main membrane.lanterna

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]])
