(ns membrane.re-frame
  (:require [reagent.core :as reagent]
            [re-frame.interop :refer [ratom]]
            [membrane.skia :as skia]
            [membrane.basic-components :as basic]
            [membrane.component :as component]
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
            [re-frame.interceptor :as ri]
            [re-frame.core :as rf :refer [reg-event-db reg-event-fx inject-cofx path after reg-sub subscribe]]))





(defn simple-component []
  (ui/label (str "The number is ")))


(defn render [root]
  (membrane.ui/on-scroll
   (fn [offset]
     (let [steps (membrane.ui/scroll root offset)]
       (run! rf/dispatch-sync steps)))
   (membrane.ui/on-mouse-move-global
    (fn [pos]
      (let [steps (membrane.ui/mouse-move-global root pos)]
        (run! rf/dispatch-sync steps)))
    (membrane.ui/on-mouse-move
     (fn [pos]
       (let [steps (membrane.ui/mouse-move root pos)]
         (run! rf/dispatch-sync steps)))
     (membrane.ui/on-mouse-event
      (fn [pos button mouse-down? mods]
        (let [steps (membrane.ui/mouse-event root pos button mouse-down? mods)]
          (if (seq steps)
            (run! rf/dispatch-sync steps)
            #_(when mouse-down?
              (handle-step [:set [:context :focus] nil] emit!)))))
      (membrane.ui/on-key-press
       (fn [s]
         (let [steps (membrane.ui/key-press root s)]
           (run! rf/dispatch-sync steps)
           steps)
         )
       (membrane.ui/on-key-event
        (fn [key scancode action mods]
          (let [steps (membrane.ui/key-event root key scancode action mods)]
            (run! rf/dispatch-sync steps)
            steps)
          )
        (membrane.ui/on-clipboard-cut
         (fn []
           (let [steps (membrane.ui/clipboard-cut root)]
             (run! rf/dispatch-sync steps)
             steps))
         (membrane.ui/on-clipboard-copy
          (fn []
            (let [steps (membrane.ui/clipboard-copy root)]
              (run! rf/dispatch-sync steps)
              steps))
          (membrane.ui/on-clipboard-paste
           (fn [s]
             (let [steps (membrane.ui/clipboard-paste root s)]
               (run! rf/dispatch-sync steps)
               steps))
           root))))))))))


(defn run [root]
  (skia/run #(render (root))))

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


(defn counter []
  (let [cnt @(rf/subscribe [:count])
        
        text @(rf/subscribe [:text])
        text2 @(rf/subscribe [:text2])
        ]
    (vertical-layout
     (ui/label (str "The count is " cnt))
     (ui/label text)
     (on :change
         (fn [s]
           [[:set-text s]]
           )
         (get-text-box :foo text))

     (on :change
         (fn [s]
           [[:set-text2 s]]
           )
         (get-text-box :foo2 text2))
     (ui/button "Increment"
                (fn []
                  [[:inc-count]])))
    ))
;; (reg-sub
;;   :text
;;   (fn [db _]
;;     (:text db)))

;; (reg-sub
;;   :text2
;;   (fn [db _]
;;     (:text2 db)))

;; (reg-sub
;;  :count
;;  (fn [db _]
;;    (:count db)))

;; (rf/reg-event-db
;;  :inc-count
;;  [(path :count)]
;;  (fn [cnt _]
;;    ((fnil inc 0) cnt)))

;; (rf/reg-event-db
;;  :set-text
;;  [(path :text)]
;;  (fn [text [_ new-text]]
;;    new-text))

;; (rf/reg-event-db
;;  :set-text2
;;  [(path :text2)]
;;  (fn [text [_ new-text]]
;;    new-text))


(def default-db {:count 0
                 :text "hi"
                 :text2 "hi"})

;; (reg-event-fx
;;   :init-db
;;   (fn [{:keys [db :as m]} _]
;;     (println m)
;;     {:db default-db}))

;; (rf/dispatch [:init-db])
