(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str]))

(def lib 'com.phronemophobic/glfw-linux-x86-64)
(def version "3.3.8")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [opts]
  (b/write-pom {:class-dir class-dir
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
