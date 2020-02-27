(ns membrane.example.todo
  (:require [membrane.skia :as skia]
            [membrane.ui :as ui
             :refer [vertical-layout
                     translate
                     horizontal-layout
                     button
                     label
                     use-color
                     bounds
                     spacer
                     on]]
            [membrane.component :as component
             :refer [defui run-ui run-ui-sync defeffect]]
            [membrane.basic-components :as basic]))







(defn delete-X []
  (ui/use-color
   [1 0 0]
   (ui/use-stroke-width
    3
    [(ui/path [0 0]
              [10 10])
     (ui/path [10 0]
              [0 10])])))

(comment
  (skia/run #(delete-X)))

(defui todo-item [ & {:keys [todo]}]
  (horizontal-layout
   (translate 5 5
              (on
               :mouse-down
               (fn [[mx my]]
                 [[:delete $todo]])
               (delete-X)))
   (translate 10 4
              (basic/checkbox :checked? (:complete? todo)))
   (spacer 10 0)
   (basic/textarea-focusable :text (:description todo))))

(comment
  (run-ui #'todo-item {:todo
                       {:complete? false
                        :description "fix me"}}))



(defui todo-list [ & {:keys [todos]}]
  (apply
   vertical-layout
   (interpose
    (spacer 0 5)
    (for [todo todos]
      (todo-item :todo todo)))))

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

(defui toggle [& {:keys [options selected]}]
  (apply
   horizontal-layout
   (interpose
    (spacer 5 0)
    (for [option options]
      (if (= option selected)
        (ui/label (name option))
        (on
         :mouse-down
         (fn [[mx my]]
           [[:set $selected option]])
         (ui/use-color [0.8 0.8 0.8]
                       (ui/label (name option)))))))))

(comment
  (run-ui #'toggle
          {:options [:all :active :complete?]
           :selected nil}))

(defui todo-app [ & {:keys [todos next-todo-text selected-filter]
                     :or {selected-filter :all}}]
  (vertical-layout
   (horizontal-layout
    (ui/button "Add Todo"
               (fn []
                 [[::add-todo $todos next-todo-text]
                  [:set $next-todo-text ""]]))
    (translate 10 10
               (ui/wrap-on
                :keypress
                (fn [default-handler s]
                  (let [effects (default-handler s)]
                    (if (and (seq effects)
                             (= s :enter))
                      [[::add-todo $todos next-todo-text]
                       [:set $next-todo-text ""]]
                      effects)))
                (basic/textarea-focusable :text next-todo-text))))
   (spacer 0 10)
   (toggle :selected selected-filter :options [:all :active :complete?])
   (spacer 0 10)
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


(defn -main [& args]
  (run-ui-sync #'todo-app {:todos
                           [{:complete? false
                             :description "first"}
                            {:complete? false
                             :description "second"}
                            {:complete? true
                             :description "third"}]
                           :next-todo-text ""})
  )



