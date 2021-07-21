(ns membrane.example.todo
  #?(:cljs
     (:require-macros [membrane.component
                       :refer [defui defeffect]]))
  (:require [membrane.ui :as ui
             :refer [vertical-layout
                     translate
                     horizontal-layout
                     label
                     with-color
                     bounds
                     spacer
                     on]]
            [membrane.component :as component
             :refer [#?(:clj defui)
                     #?(:clj defeffect)]]
            [membrane.basic-components :as basic])
  #?(:clj (:gen-class)))

;; Draw a red X that we'll use to display a delete button
;; No interactivity, so `defui` not needed
(defn delete-X []
  (ui/with-style :membrane.ui/style-stroke
    (ui/with-color
      [1 0 0]
      (ui/with-stroke-width
        3
        [(ui/path [0 0]
                  [10 10])
         (ui/path [10 0]
                  [0 10])]))))

(comment
  (defn run-ui [ui-var initial-state]
    (skia/run (component/make-app ui-var initial-state)))

  (require '[membrane.skia :as skia])
  (skia/run #(delete-X))
  ,)

;; Display a single todo item
(defui todo-item [{:keys [todo]}]
  (horizontal-layout
   (translate 5 5
              (on
               :mouse-down
               (fn [[mx my]]
                 [[:delete $todo]])
               (delete-X)))
   (translate 10 4
              (basic/checkbox {:checked? (:complete? todo)}))
   (spacer 10 0)
   (basic/textarea {:text (:description todo)})))

(comment
  (run-ui #'todo-item {:todo
                       {:complete? false
                        :description "fix me"}}))


;; Display a list of `todo-item`s stacked vertically
;; Add 5px of spacing between `todo-item`s
(defui todo-list [{:keys [todos]}]
  (apply
   vertical-layout
   (interpose
    (spacer 0 5)
    (for [todo todos]
      (todo-item {:todo todo})))))

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
(defui toggle [{:keys [options selected]}]
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
         (ui/with-color [0.8 0.8 0.8]
           (ui/label (name option)))))))))

(comment
  (run-ui #'toggle
          {:options [:all :active :complete?]
           :selected nil}))

(defui todo-app [{:keys [todos next-todo-text selected-filter]
                  :or {selected-filter :all}}]
  (vertical-layout
   (horizontal-layout
    (basic/button {:text "Add Todo"
                   :on-click (fn []
                               [[::add-todo $todos next-todo-text]
                                [:set $next-todo-text ""]])})
    (translate 10 10
               (ui/wrap-on
                :key-press
                (fn [default-handler s]
                  (let [effects (default-handler s)]
                    (if (and (seq effects)
                             (= s :enter))
                      [[::add-todo $todos next-todo-text]
                       [:set $next-todo-text ""]]
                      effects)))
                (basic/textarea {:text next-todo-text}))))
   (spacer 0 10)
   (toggle {:selected selected-filter :options [:all :active :complete?]})
   (spacer 0 10)
   (let [filter-fn (get filter-fns selected-filter :all)
         visible-todos (filter filter-fn todos)]
     (todo-list {:todos visible-todos}))))


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
  (skia/run (component/make-app #'todo-app todo-state)))


(comment
  (def todo-state
    (skia/run (component/make-app #'todo-app
                                  {:todos
                                   [{:complete? false
                                     :description "first"}
                                    {:complete? false
                                     :description "second"}
                                    {:complete? true
                                     :description "third"}]
                                   :next-todo-text ""}))))


#?
(:clj
 (defn -main [& args]
   ((requiring-resolve 'membrane.skia/run-sync)
    (component/make-app #'todo-app
                        {:todos
                         [{:complete? false
                           :description "first"}
                          {:complete? false
                           :description "second"}
                          {:complete? true
                           :description "third"}]
                         :next-todo-text ""}))
   ))



#?
(:clj
 (defn save-image [{:keys [path]
                    :or {path "todo.png"}}]
   ((requiring-resolve 'membrane.skia/draw-to-image!)
    path
    (todo-app
     {:selected-filter :all
      :todos
      [{:complete? false
        :description "first"}
       {:complete? false
        :description "second"}
       {:complete? true
        :description "third"}]
      :next-todo-text ""}))
   ))
