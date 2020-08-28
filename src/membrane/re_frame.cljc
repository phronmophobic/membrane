(ns membrane.re-frame
  (:require [re-frame.interop :refer [ratom]]
            [membrane.basic-components :as basic]
            [membrane.component :as component]
            [membrane.ui :as ui]
            [re-frame.core :as rf :refer [reg-event-db reg-event-fx inject-cofx path after reg-sub subscribe]]))

(def dispatch-sync
  #(when %
     (rf/dispatch-sync %)))

(defn re-frame-app [root]
  (membrane.ui/on-scroll
   (fn [offset]
     (let [steps (membrane.ui/scroll root offset)]
       (run! dispatch-sync steps)))
   (membrane.ui/on-mouse-move-global
    (fn [pos]
      (let [steps (membrane.ui/mouse-move-global root pos)]
        (run! dispatch-sync steps)))
    (membrane.ui/on-mouse-move
     (fn [pos]
       (let [steps (membrane.ui/mouse-move root pos)]
         (run! dispatch-sync steps)))
     (membrane.ui/on-mouse-event
      (fn [pos button mouse-down? mods]
        (let [steps (membrane.ui/mouse-event root pos button mouse-down? mods)]
          (if (seq steps)
            (run! dispatch-sync steps)
            #_(when mouse-down?
                (handle-step [:set [:context :focus] nil] emit!)))))
      (membrane.ui/on-key-press
       (fn [s]
         (let [steps (membrane.ui/key-press root s)]
           (run! dispatch-sync steps)
           steps)
         )
       (membrane.ui/on-key-event
        (fn [key scancode action mods]
          (let [steps (membrane.ui/key-event root key scancode action mods)]
            (run! dispatch-sync steps)
            steps)
          )
        (membrane.ui/on-clipboard-cut
         (fn []
           (let [steps (membrane.ui/clipboard-cut root)]
             (run! dispatch-sync steps)
             steps))
         (membrane.ui/on-clipboard-copy
          (fn []
            (let [steps (membrane.ui/clipboard-copy root)]
              (run! dispatch-sync steps)
              steps))
          (membrane.ui/on-clipboard-paste
           (fn [s]
             (let [steps (membrane.ui/clipboard-paste root s)]
               (run! dispatch-sync steps)
               steps))
           root))))))))))

(def text-boxes (atom {}))
(def text-box-dispatch (component/default-handler text-boxes))
(defn get-text-box [tid text]
  (let [all-data @text-boxes
        data (get all-data tid)
        args (apply concat
                    [:text text]
                    [:focus (get all-data ::focus)]
                    (for [[k v] data
                          :when (not= k :text)]
                      [k v]))
        $args [:$text [(list 'get tid) :text]
               :$font [(list 'get tid) :font]
               :$textarea-state [(list 'get tid) :textarea-state]
               :$extra [(list 'get tid) :extra]
               :$focus [::focus]]]
    (ui/on-bubble
     (fn [effects]
       (text-box-dispatch :set [(list 'get tid) :text] text)
       (run! #(apply text-box-dispatch %) effects)
       (let [new-data (get @text-boxes tid)
             new-text (:text new-data)]
         (when (not= new-text text)
           [[:change new-text]])))
     (apply basic/textarea (concat args $args)))))



(def scrollviews (atom {}))
(def scrollview-dispatch (component/default-handler scrollviews))

(defn set-scroll-offset! [sid [sx sy :as offset]]
  (swap! scrollviews assoc-in [sid :offset] offset))

(defn scroll-to-top! [sid]
  (set-scroll-offset! sid [0 0]))

(defn get-scrollview [sid scroll-bounds body]
  (let [all-data @scrollviews
        data (get all-data sid)
        args [:body body
              :scroll-bounds scroll-bounds
              :offset (get data :offset [0 0])
              :mdownx? (get data :mdownx?)
              :mdowny? (get data :mdowny?)
              :extra (get data :extra)]
        $args [:$offset [(list 'get sid) :offset (list 'nil->val [0 0])]
               :$mdownx? [(list 'get sid) :mdownx?]
               :$mdowny? [(list 'get sid) :mdowny?]
               :$extra [(list 'get sid) :extra]]]
    (ui/on-bubble
     (fn [effects]
       (run! #(apply scrollview-dispatch %) effects)
       nil)
     (apply basic/scrollview (concat args $args)))))

(defn fix-scroll [elem]
  (ui/on-scroll (fn [[sx sy]]
                  (ui/scroll elem [(- sx) (- sy)]))
                elem))

(comment
  (def lorem-ipsum (clojure.string/join
                    "\n"
                    (repeatedly 800
                                (fn []
                                  (clojure.string/join
                                   (repeatedly (rand-int 50)
                                               #(rand-nth "abcdefghijklmnopqrstuvwxyz ")))))))
  (defn test-scrollview []
    [(ui/translate 10 10
                   (fix-scroll
                    (get-scrollview :my-scrollview [300 300]
                                    (ui/label lorem-ipsum))))])

  (require '[membrane.skia :as skia])
  (skia/run #(re-frame-app (test-scrollview)))

  )
