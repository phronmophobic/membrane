(ns membrane.webgltest
  ;; (:require [cljsjs.opentype])
  (:require-macros [membrane.webgl-macros])
  (:require
   [membrane.component :refer [defui]]
   membrane.audio
   membrane.webgl
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
   [membrane.example.todo :as todo]
   
   )
  )

#_(run-webgl-project "todo-list")
;; opentype.load('fonts/Roboto-Black.ttf');
#_(js/opentype.load "fonts/Menlo-Regular2.ttf"
                  (fn [err font]
                    (if err
                      (do (println "Error: " err)
                          (js/console.log err))
                      (do
                        
                        
                        (js/console.log font)
                        (set! js/window.font font)))))

(defui test-ui [& {:keys [a b]}]
  (let [l (label a)
        [w h] (ui/text-bounds ui/default-font a)
        border (ui/rectangle w h)]
    [(translate 20 20
                (membrane.basic-components/textarea-focusable :text a))]
    #_(translate 20 20
                 )
    #_[(label "hi there")
       (ui/on
        :mouse-move
        (fn [[x y]]
          [[:set $b (str [x y]
                         (ui/index-for-position ui/default-font a x y))]]
          )
        (label a))
       (label b)]

    
    )
  )

(defonce start-app (membrane.component/run-ui #'test-ui (atom {:a "there"})))
;; (defonce start-todo-app (membrane.component/run-ui #'todo/todo-app todo/todo-state))
#_(js/setTimeout (fn []
)
               3000)
