(ns membrane.example.counter
  (:require #?(:clj [membrane.skia :as skia])
            [membrane.ui :as ui
             :refer [vertical-layout
                     translate
                     horizontal-layout
                     button
                     label
                     with-color
                     bounds
                     spacer
                     on]]
            [membrane.component :as component
             :refer [defui run-ui run-ui-sync defeffect]]
            [membrane.basic-components :as basic])
  #?(:clj (:gen-class)))


;; Display a "more!" button next to label showing num
;; clicking on "more!" will dispatch a ::counter-increment effect
(defui counter [& {:keys [num]}]
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
  (run-ui #'counter {:num 10}))

;; Display an "Add Counter" button
;; on top of a stack of counters
;;
;; clicking on the "Add Counter" button will
;; add a new counter to the bottom of the stack
;; 
;; clicking on the counters' "more!" buttons will
;; update their respective numbers
(defui counter-counter [& {:keys [nums]}]
  (apply
   vertical-layout
   (on :mouse-down (fn [[mx my]]
                     [[::add-counter $nums]])
       (ui/button "Add Counter"))
   (for [num nums]
     (counter :num num))))

(defeffect ::add-counter [$nums]
  (dispatch! :update $nums conj 0))

(comment
  ;; pop up a window showing our counter-counter
  ;; with nums initially set to [0 1 2]
  (run-ui #'counter-counter {:nums [0 1 2]}))

#?(:clj
   (defn -main [ & args]
     (run-ui-sync #'counter-counter {:nums [0 1 2]})))



