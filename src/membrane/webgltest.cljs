(ns membrane.webgltest
  (:require-macros [membrane.webgl-macros
                    :refer [add-image!]])
  (:require
   [membrane.component :refer [defui]]
   membrane.audio
   [membrane.webgl :as webgl]
   [com.rpl.specter :as spec
    :refer [ATOM ALL FIRST LAST MAP-VALS META]]
   membrane.basic-components
   [membrane.ui :as ui
    :refer [vertical-layout
            label
            width
            height
            translate
            origin-x
            origin-y]]
   [membrane.example.todo :as todo]))

(def canvas (.getElementById js/document "canvas"))

(defui test-ui [& {:keys [a b]}]
  (let [l (label a)
        [w h] (ui/text-bounds ui/default-font a)
        border (ui/rectangle w h)]
    [(translate 20 20
                (membrane.basic-components/textarea :text a))]
    #_(translate 20 20
                 )
    #_(ui/with-color [255 0 0]
      (ui/rectangle 100 100))
    #_(ui/rectangle 10 10)
    #_(vertical-layout
     (label "hi there")
     (ui/on
      :mouse-move
      (fn [[x y]]
        [[:set $b (str [x y]
                       (ui/index-for-position ui/default-font a x y))]]
        )
      (label a))
     (label b))

    
    )
  )

(def enlarge-bottom-button (.getElementById js/document "enlarge-canvas-bottom"))

(defonce enlargeBottomEventHandler
  (doto enlarge-bottom-button
    (.addEventListener "mousedown"
                       (fn []
                         (doto canvas
                           (.setAttribute "height" (+ (int (.-height canvas)) 200)))))))

(def enlarge-right-button (.getElementById js/document "enlarge-canvas-right"))

(defonce enlargeRightEventHandler
  (doto enlarge-right-button
    (.addEventListener "mousedown"
                       (fn []
                         (doto canvas
                           (.setAttribute "width" (+ (int (.-width canvas)) 200)))))))

;; (defonce start-app (membrane.component/run-ui #'test-ui (atom {:a "there"})))
(defonce start-todo-app (membrane.component/run-ui #'todo/todo-app todo/todo-state nil {:canvas canvas}))

(let [new-canvas (webgl/create-canvas 300 400)]
  (.appendChild (.-body js/document) new-canvas)
  (defonce start-other-app (membrane.component/run-ui #'todo/todo-app @todo/todo-state nil {:canvas new-canvas})))





