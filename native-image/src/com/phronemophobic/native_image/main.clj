(ns com.phronemophobic.native-image.main
  (:require [membrane.example.todo :as todo]
            [membrane.component :as component]
            [membrane.skia :as skia])
  (:gen-class))

(def app (component/make-app #'todo/todo-app
                             {:todos
                              [{:complete? false
                                :description "first"}
                               {:complete? false
                                :description "second"}
                               {:complete? true
                                :description "third"}]
                              :next-todo-text ""}))


(defn -main [& args]
  (skia/run-sync app))
