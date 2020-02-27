(ns membrane.customappmain
  (:require membrane.uibuilder
            clojure.main)
  (:gen-class))


(def main-class-loader @clojure.lang.Compiler/LOADER)
(defn -main [& args]
  (clojure.main/with-bindings
    (let [project-path (java.lang.System/getProperty "project.path")]
      (membrane.uibuilder/run-project (read-string (slurp project-path))))))
