(ns membrane.example.terminal-todo
  (:require [membrane.ui :as ui
             :refer
             [horizontal-layout
              vertical-layout
              on]]
            ;; need effects
            [membrane.lanterna
             :refer [textarea checkbox label button]
             :as lanterna]
            [membrane.basic-components :as basic]
            [membrane.component :as component
             :refer [defui defeffect]]))

;;; todo app
(defui todo-item [ & {:keys [todo]}]
  (horizontal-layout
   (on
    :mouse-down
    (fn [[mx my]]
      [[:delete $todo]])
    (ui/with-color [1 0 0]
      (label "X")))
   (checkbox :checked? (:complete? todo))
   (ui/wrap-on
    :key-press
    (fn [default-handler s]
      (when (not= s :enter)
        (default-handler s)))
    (textarea :text (:description todo)))))

(defui todo-list [ & {:keys [todos]}]
  (apply
   vertical-layout
   (for [todo todos]
     (todo-item :todo todo))))


(def filter-fns
  {:all (constantly true)
   :active (comp not :complete?)
   :complete? :complete?})

;; Create a toggle that allows the user
;; to toggle between options
(defui toggle [& {:keys [options selected]}]
  (apply
   horizontal-layout
   (for [option options]
     (if (= option selected)
       (label (name option))
       (on
        :mouse-down
        (fn [[mx my]]
          [[:set $selected option]])
        (ui/with-color [0.8 0.8 0.8]
          (label (name option))))))))


(defui todo-app [ & {:keys [todos next-todo-text selected-filter]
                     :or {selected-filter :all}}]
  (vertical-layout
   (horizontal-layout
    (button "Add Todo"
            (fn []
              [[::add-todo $todos next-todo-text]
               [:set $next-todo-text ""]]))
    (ui/wrap-on
     :key-press
     (fn [default-handler s]
       (let [effects (default-handler s)]
         (if (and (seq effects)
                  (= s :enter))
           [[::add-todo $todos next-todo-text]
            [:set $next-todo-text ""]]
           effects)))
     (textarea :text next-todo-text)))
   (toggle :selected selected-filter :options [:all :active :complete?])
   (let [filter-fn (get filter-fns selected-filter :all)
         visible-todos (filter filter-fn todos)]
     (todo-list :todos visible-todos))))


(def todo-state (atom {:todos
                       [{:complete? false
                         :description "first"}
                        {:complete? false
                         :description "second"}
                        {:complete? true
                         :description "third"}]
                       :next-todo-text ""}))

(defeffect ::add-todo [$todos next-todo-text]
  (dispatch! :update $todos #(conj % {:description next-todo-text
                                      :complete? false})))




(def todo-state (atom {:todos
                       [{:complete? false
                         :description "first"}
                        {:complete? false
                         :description "second"}
                        {:complete? true
                         :description "third"}]
                       :next-todo-text ""}))

(defn -main [& args]
  ;; (component/run-ui-sync #'term-test {:num 0 :s "hh"})
  (lanterna/run-sync (component/make-app #'todo-app
                                         todo-state))
  ;; (.close System/in)
  ;; (shutdown-agents)
  )
