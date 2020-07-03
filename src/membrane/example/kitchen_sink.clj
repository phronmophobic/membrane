(ns membrane.example.kitchen-sink
  (:require [membrane.skia :as skia]
            [membrane.ui :as ui
             :refer [vertical-layout
                     translate
                     horizontal-layout
                     label
                     with-color
                     bounds
                     spacer
                     on]]
            [clojure.core.async :as async]
            [membrane.component :as component
             :refer [defui defeffect]]
            [membrane.basic-components :as basic])
  (:gen-class))


(def examples (atom {}))
(defmacro defexample
  ([category title elem]
   `(defexample ~category ~title nil ~elem))
  ([category title doc elem]
   `(let [title# ~title
          category# ~category]
      (swap! ~'examples assoc
             [category# title#]
             {:title title#
              :category category#
              :doc ~doc
              :fn (fn []
                    ~elem)
              :code (quote ~elem)}))))

(defmacro defcomponent-example
  ([category title elem initial-state]
   `(defcomponent-example ~category ~title nil ~elem ~initial-state))
  ([category title doc initial-state elem]
   (let [component-sym (gensym "example-component-")]
     `(let [title# ~title
            category# ~category]
        (defui ~component-sym [ & {:keys ~(mapv (fn [k]
                                                  (-> k
                                                      name
                                                      symbol))
                                                (keys initial-state))}]
          ~elem)
        (swap! ~'examples assoc
               [category# title#]
               {:title title#
                :category category#
                :doc ~doc
                :fn (var ~component-sym)
                :component? true
                :initial-state ~initial-state
                :code (quote ~elem)
                })))
   
   )
  )


(defcomponent-example
  "Components"
  "textarea"
  {:text "sup"}
  (basic/textarea :text text :font (assoc ui/default-font :size 24)))



(defn random-lines []
  (let [n 100
        maxx 400
        maxy 400]
    (ui/with-style :membrane.ui/style-stroke
      (vec
       (for [i (range n)]
         (ui/with-stroke-width (inc (rand-int 10))
           (ui/with-color [(rand) (rand) (rand)]
             (ui/path [(rand-int maxx) (rand-int maxy)]
                      [(rand-int maxx) (rand-int maxy)]))))))))


(defexample
  "Image"
  "Image"
  (ui/image (clojure.java.io/resource "lines.png")))



(defexample
  "With*"
  "Image with scaling"
  (vertical-layout
   (ui/scale 0.2 0.2
     (ui/image (clojure.java.io/resource "lines.png")))
   (ui/scale 0.2 1.0
     (ui/image (clojure.java.io/resource "lines.png")))
   (ui/scale 1.0 0.2
     (ui/image (clojure.java.io/resource "lines.png")))
   (ui/scale 2.0 2.0
     (ui/image (clojure.java.io/resource "lines.png")))
    
   ))

(defexample
  "With*"
  "with-stroke"
  (ui/with-stroke-width 10
    (apply
     vertical-layout
     (interpose (spacer 0 10)
                (for [style [:membrane.ui/style-fill
                             :membrane.ui/style-stroke
                             :membrane.ui/style-stroke-and-fill]]
                  (vertical-layout
                   (ui/label style)
                   (spacer 0 3)
                   (ui/with-style style
                     (ui/rounded-rectangle 100 50 10 ))))
                ))))

(defexample
  "Layout"
  "Vertical layout with spacing"
  (vertical-layout
   (ui/checkbox true)
   (spacer 0 10)
   (ui/checkbox false)))

(defexample
  "Scissor View"
  "scissor-view"
  (ui/scissor-view [10 10]
                    [300 300]
                    (ui/image (clojure.java.io/resource "lines.png")))
  )



(defcomponent-example
  "Components"
  "button"
  {:hover? false}
  (basic/button :hover? hover? :text "hello"))




(defcomponent-example
  "Components"
  "textarea-light"
  {:text "hello"}
  (basic/textarea-light :text text :font (assoc ui/default-font :size 24)))



(defui scroll-example [& {:keys []}]
  (basic/scrollview :scroll-bounds [200 150]
                    :body (ui/image (clojure.java.io/resource "lines.png"))))


(defcomponent-example
  "Components"
  "scrollview"
  {}
  (basic/scrollview :scroll-bounds [200 150]
                    :body (ui/image (clojure.java.io/resource "lines.png"))))


(defcomponent-example
  "Components"
  "checkbox"
  {:checked? false}
  (basic/checkbox :checked? checked?))





(defexample
  "Labels"
  "label using default font"
  (ui/label "Hello")) 


(defexample
  "Labels"
  "label with specified font"
  "font will check the default System font folder
on Mac osx, check /System/Library/Fonts/ for available fonts"
  (ui/label "Hello" (ui/font "Menlo.ttc" 22)))

(defexample
  "Labels"
  "use the default font, but change the size"
  (ui/label "Hello" (ui/font nil 22)))


(defexample
  "Labels"
  "label with specified font color"
  "colors are vectors of [red green blue] or [red green blue alpha]
with values from 0 - 1 inclusive"
  (ui/with-color [1 0 0]
    (ui/label "Hello")))
  

(defexample
  "Rectangles"
  "rectangle"
  (ui/rectangle 30 50))

(defexample
  "Rectangles"
  "stroked rectangle"
  (ui/with-style :membrane.ui/style-stroke
    (ui/rectangle 30 50)))

(defexample
  "Rectangles"
  "rectangle with specified color"
  (let [red [1 0 0]]
    (ui/with-color red
      (ui/rectangle 30 50))))



(defexample
  "Rounded Rectangles"
  "rounded rectangle"
  (ui/rounded-rectangle 30 50 5))

(defexample
  "Rounded Rectangles"
  "stroked rounded rectangle"
  (ui/with-style :membrane.ui/style-stroke
    (ui/rounded-rectangle 30 50 5)))

(defexample
  "Rounded Rectangles"
  "rounded rectangle with specified color"
  (let [red [1 0 0]]
    (ui/with-color red
      (ui/rounded-rectangle 30 50 5))))

(defui example-component [& {:keys [ui-var state]}]
  (let [arglist (-> ui-var
                    meta
                    :arglists
                    first)
        m (second arglist)
        arg-names (disj (set (:keys m))
                        'extra)
        defaults (:or m)
        args (apply concat
                    (for [nm arg-names
                          :let [kw (keyword nm)
                                $kw (keyword (str "$" (name kw)))]]
                      [kw
                       (get state kw
                            (get defaults nm))

                       $kw
                       (into
                        [$state (list 'keypath kw)]
                        (when (contains? defaults nm)
                          [(list 'nil->val (get defaults nm))]))]))
        
        elem-extra (get state :extra)]

    (apply @ui-var
           :extra elem-extra
           :$extra $elem-extra
           :context context
           :$context $context
           args)))


(let [counter (atom 0)]
  (defeffect :fade-copy-text [$show-copy-text]
    (let [num (swap! counter inc)]
      (dispatch! :set $show-copy-text num)
      (future
        (java.lang.Thread/sleep 2000)
        (dispatch! :update $show-copy-text
                   (fn [old-val]
                     (if (= old-val num)
                       nil
                       old-val)))))))


(defui show-examples [& {:keys [examples selected example-state show-copy-text]}]
  
  (translate 20 20
             (vertical-layout
              (let [options (->> examples
                                 keys
                                 (sort-by first)
                                 (map (fn [[category title :as key]]
                                        [key (str category ": " title)])))]
                (horizontal-layout
                 (translate 0 7
                            (ui/label "Select an example: "))
                 (spacer 5 0)
                 (on
                  :membrane.basic-components/select
                  (fn [$selected value]
                    [[:membrane.basic-components/select $selected value]
                     (let [example (examples value)]
                       [:set $example-state (:initial-state example)])])
                  (basic/dropdown :options options
                                  :selected selected))))
              (spacer 0 10)
              (when selected
                (let [example (get examples selected)]
                  (vertical-layout
                   (ui/label (:code example))
                   (spacer 0 10)
                   (horizontal-layout
                    (basic/button :text "Copy to clipboard"
                                  :on-click (fn []
                                              [[:clipboard-copy (pr-str (:code example))]
                                               [:fade-copy-text $show-copy-text]]))
                    (when show-copy-text
                      (translate 10 3
                                 (ui/label "copied!"))))
                   (spacer 0 10)
                   (let [elem (if (:component? example)
                                (example-component :ui-var (:fn example) :state example-state)
                                ((:fn example)))
                         body (translate 10 10
                                         elem)
                         [elem-width elem-height] (bounds elem)]
                     [(ui/with-style :membrane.ui/style-stroke
                        (ui/rectangle (max 500 (+ 20 elem-width)) (max 500 (+ 20 elem-height))))
                      body]))))))
  )

(defn run-examples []
  (async/go
    (let [atm (skia/run (component/make-app #'show-examples {:examples @examples}))]

      (add-watch examples :run-examples
                 (fn [k r o new-examples]
                   [swap! atm assoc :examples new-examples])))))

(defn -main [ & args]
  (skia/run-sync (component/make-app #'show-examples {:examples @examples})))
