(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def lib 'com.phronemophobic/membrane)
(def version "0.14.4-beta")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def src-pom "./pom-template.xml")

(defn clean [_]
  (b/delete {:path "target"}))

(defn compile [_]
  (b/javac {:src-dirs ["src-java"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["-source" "8" "-target" "8"]}))

(defn jar [opts]
  (compile opts)
  (b/write-pom {:class-dir class-dir
                :src-pom src-pom
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy [opts]
  (jar opts)
  (try ((requiring-resolve 'deps-deploy.deps-deploy/deploy)
        (merge {:installer :remote
                :artifact jar-file
                :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
               opts))
       (catch Exception e
         (if-not (str/includes? (ex-message e) "redeploying non-snapshots is not allowed")
           (throw e)
           (println "This release was already deployed."))))
  opts)


;; Skialib
(def skialib-class-dir "target-skialib/classes")
(def skialib-jar-file (.getCanonicalPath
                       (io/file "target-skialib" "skialib.jar")))

(defn deploy-skialib [{:keys [platform
                              arch]}]
  (let [resource-prefix (case platform
                          "macos" "darwin"
                          ;; else
                          platform)
        resource-suffix (case arch
                          "arm64" "aarch64"
                          "x86_64" "x86-64")

        shared-suffix (case platform
                        "macos" "dylib"
                        "linux" "so")
        shared-lib-path (.getCanonicalPath
                         (io/file "csource"
                                  (str "libmembraneskia-" arch "." shared-suffix)))

        coord (symbol "com.phronemophobic.membrane"
                      (str "skialib-" platform "-" resource-suffix))

        glfw-platform (case platform
                        "macos" "macosx"
                        platform)
        glfw-dep (symbol "com.phronemophobic"
                         (str "glfw-" glfw-platform "-" resource-suffix))
        skia-basis (b/create-basis {:project
                                    {:deps
                                     {glfw-dep {:mvn/version "3.3.8"}}}})]
    (b/write-pom {:class-dir skialib-class-dir
                  :src-pom "bogus-src-pom"
                  :pom-data
                  [[:licenses
                    [:license
                     [:name "BSD 3-Clause \"New\" or \"Revised\" License"]
                     [:url "https://github.com/google/skia/blob/c61843470d89de81c571d87ed2c810911edeb1a3/LICENSE"]]
                    [:license
                     [:name "Apache License, Version 2.0"]
                     [:url "http://www.apache.org/licenses/LICENSE-2.0"]]]]
                  :lib coord
                  :version "0.15-beta-SNAPSHOT"
                  :basis skia-basis})
    (b/copy-file {:src shared-lib-path
                  :target (.getCanonicalPath
                           (io/file skialib-class-dir
                                    (str resource-prefix "-" resource-suffix)
                                    (str "libmembraneskialib." shared-suffix)))})
    (b/jar {:class-dir skialib-class-dir
            :jar-file skialib-jar-file})
    (try ((requiring-resolve 'deps-deploy.deps-deploy/deploy)
          {:installer :remote
           :artifact skialib-jar-file
           :pom-file (b/pom-path {:lib coord :class-dir skialib-class-dir})})
         (catch Exception e
           (if-not (str/includes? (ex-message e) "redeploying non-snapshots is not allowed")
             (throw e)
             (println "This release was already deployed."))))))


