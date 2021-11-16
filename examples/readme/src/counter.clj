(ns counter
  (:require [membrane.skia :as skia]
            [membrane.ui :as ui
             :refer [horizontal-layout
                     button
                     label
                     spacer
                     on]]))


(defonce counter-state (atom 0))

;; Display a "more!" button next to label showing num
;; clicking on "more!" will increment the counter
(defn counter [num]
  (horizontal-layout
   (on :mouse-down (fn [[mouse-x mouse-y]]
                     (swap! counter-state inc)
                     nil)
       (button "more!"))
   (spacer 5 0)
   (label num (ui/font nil 19))))

(comment
  ;; pop up a window that shows our counter
  (skia/run #(counter @counter-state))
  ,)



(ns counter
  (:require [membrane.skia :as skia]
            [membrane.ui :as ui
             :refer [horizontal-layout
                     vertical-layout
                     button
                     label
                     on]]
            [membrane.component :as component
             :refer [defui make-app defeffect]])
  (:gen-class))


;; Display a "more!" button next to label showing num
;; clicking on "more!" will dispatch a ::counter-increment effect
(defui counter [{:keys [num]}]
  (horizontal-layout
   (on :mouse-down (fn [[mouse-x mouse-y]]
                     [[::counter-increment $num]])
       (ui/button "more!"))
   (ui/label num)))

(defeffect ::counter-increment [$num]
  (dispatch! :update $num inc))

(comment
  ;; pop up a window showing our counter with
  ;; num initially set to 10
  (skia/run (make-app #'counter {:num 10}))
  ,)


(defui counter-counter [{:keys [nums]}]
  (apply
   vertical-layout
   (on :mouse-down (fn [[mx my]]
                     [[::add-counter $nums]])
       (ui/button "Add Counter"))
   (for [num nums]
     (counter {:num num}))))

(defeffect ::add-counter [$nums]
  (dispatch! :update $nums conj 0))

(comment
  ;; pop up a window showing our counter-counter
  ;; with nums initially set to [0 1 2]
  (skia/run (make-app #'counter-counter {:nums [0 1 2]}))
  ,)
