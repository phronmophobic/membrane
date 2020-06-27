(ns membrane.example.terminal-todo
  (:require [membrane.ui :as ui
             :refer
             [horizontal-layout
              vertical-layout
              on]]
            ;; need effects
            [membrane.lanterna
             :refer [textarea checkbox label button]]
            [membrane.basic-components :as basic]
            [membrane.component :as component
             :refer [defui run-ui run-ui-sync defeffect]]))

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

(comment
  (run-ui #'todo-item {:todo
                       {:complete? false
                        :description "fix me"}}))


(defui todo-list [ & {:keys [todos]}]
  (apply
   vertical-layout
   (for [todo todos]
     (todo-item :todo todo))))



(comment
  (run-ui #'todo-list {:todos
                       [{:complete? false
                         :description "first"}
                        {:complete? false
                         :description "second"}
                        {:complete? true
                         :description "third"}]}))



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

(comment
  (run-ui #'toggle
          {:options [:all :active :complete?]
           :selected nil}))

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

(comment
  (run-ui #'todo-app todo-state
                        ))

(comment
  (def todo-state
    (run-ui #'todo-app {:todos
                        [{:complete? false
                          :description "first"}
                         {:complete? false
                          :description "second"}
                         {:complete? true
                          :description "third"}]
                        :next-todo-text ""})))



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
  (component/run-ui-sync #'todo-app
                         todo-state
                         (let [default (component/default-handler todo-state)]
                           (fn [& effect]
                             (apply default effect))))
  ;; (.close System/in)
  ;; (shutdown-agents)
  )
