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
               :$extra [(list 'get tid) :border?]
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


