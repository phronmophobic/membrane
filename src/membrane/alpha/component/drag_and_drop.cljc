(ns membrane.alpha.component.drag-and-drop
  (:refer-clojure :exclude [drop abs])
  (:require [membrane.ui :as ui]
            [membrane.component
             :refer [defui defeffect]]))


(defprotocol IDrop
  :extend-via-metadata true
  (-drop [elem pos obj]))

(defprotocol IDropMove
  :extend-via-metadata true  
  (-drop-move [elem pos obj]))

(defn drop
  "Returns the effects of a drop event on elem.

  Requires that the a drag-and-drop component is wrapping the necessary elements in the stack."
  ([elem pos obj]
   (-drop elem pos obj)))

(defn drop-move
  "Returns the effects of a drop event on elem.

  Requires that the a drag-and-drop component is wrapping the necessary elements in the stack."
  ([elem pos obj]
   (-drop-move elem pos obj)))

(defrecord OnDrop [on-drop elem]
  ui/IOrigin
  (-origin [_]
    [0 0])

  ui/IBounds
  (-bounds [this]
    (ui/child-bounds elem))

  ui/IChildren
  (-children [this]
    [elem])

  IDrop
  (-drop [this pos obj]
    (when-let [local-pos (ui/within-bounds? elem pos)]
      (when on-drop
        (on-drop local-pos obj)))))

(defn on-drop [handler body]
  (OnDrop. handler body))


(defrecord OnDropMove [on-drop-move elem]
  ui/IOrigin
  (-origin [_]
    [0 0])

  ui/IBounds
  (-bounds [this]
    (ui/child-bounds elem))

  ui/IChildren
  (-children [this]
    [elem])

  IDropMove
  (-drop-move [this pos obj]
    (when-let [local-pos (ui/within-bounds? elem pos)]
      (when on-drop-move
        (on-drop-move local-pos obj)))))

(defn on-drop-move [handler body]
  (OnDropMove. handler body))

(defprotocol IDragMouseOffset
  (-drag-mouse-offset [this mpos]))

(extend-protocol IDragMouseOffset

  #?(:clj Object
       :cljs default)
  (-drag-mouse-offset [this mpos]
    mpos)

  membrane.ui.Padding
  (-drag-mouse-offset [this [mx my]]
    [(- mx (:left this))
     (- my (:top this))])

  membrane.ui.Scale
  (-drag-mouse-offset [this mpos]
    (let [scalars (:scalars this)]
      [(/ (nth mpos 0)
          (nth scalars 0))
       (/ (nth mpos 1)
          (nth scalars 1))]))

  membrane.ui.ScrollView
  (-drag-mouse-offset [this [mx my]]
    (let [offset (:offset this)]
      [(- mx (nth offset 0))
       (- my (nth offset 1))])))


(extend-type #?(:clj Object
                :cljs default)

  IDrop
  (-drop [elem local-pos obj]
    (let [intents
          ;; use seq to make sure we don't stop for empty sequences
          (some #(when-let [local-pos (ui/within-bounds? % local-pos)]
                   (seq (-drop % (-drag-mouse-offset elem local-pos) obj)))
                (reverse (ui/children elem)))]
      (ui/-bubble elem intents)))

  IDropMove
  (-drop-move [elem local-pos obj]
    (let [intents
          ;; use seq to make sure we don't stop for empty sequences
          (some #(when-let [local-pos (ui/within-bounds? % local-pos)]
                   (seq (-drop-move % (-drag-mouse-offset elem local-pos) obj)))
                (reverse (ui/children elem)))]
      (ui/-bubble elem intents))))

(comment
  (require '[membrane.skia :as skia])
  ,)


(defn ^:private abs [n]
  (Math/abs n))

(defeffect ::drag-start [drop-object]
  ;; do nothing
  )

(defn drag-start? [intent]
  (when (= ::drag-start (first intent))
    intent))

;; drag start event
;; :drop-object
;; 
;; :pending

(defui drag-and-drop [{:keys [body
                              pending-intents
                              pending-drop-object
                              pending-init-intents
                              drag-start
                              ^:membrane.component/contextual
                              drop-object]}]
  (let [body (cond
               drag-start
               (ui/wrap-on
                :mouse-up
                (fn [handler mpos]
                  (concat
                   (eduction
                    (remove drag-start?)
                    pending-intents)
                   [[:set $drag-start nil]
                    [:set $pending-intents nil]
                    [:set $pending-init-intents nil]
                    [:set $pending-drop-object nil]]
                   (handler mpos)))
                :mouse-move
                (fn [handler [mx my]]
                  (when (> (+ (abs (- mx (first drag-start)))
                              (abs (- my (second drag-start))))
                           4)
                    (into [[:set $drag-start nil]
                           [:set $pending-intents nil]
                           [:set $pending-drop-object nil]
                           [:set $pending-init-intents nil]
                           [:set $drop-object pending-drop-object]]
                          pending-init-intents)))
                body)

               drop-object
               (ui/on
                :mouse-move
                (fn [mpos]
                  (drop-move body mpos drop-object))
                :mouse-up
                (fn [mpos]
                  (cons [:set $drop-object nil]
                        (drop body mpos drop-object)))
                body)

               :else
               (ui/wrap-on
                :mouse-down
                (fn [handler mpos]
                  (let [intents (handler mpos)
                        drag-start-intent (some drag-start?
                                                intents)]
                    (if drag-start-intent
                      (let [m (nth drag-start-intent 1)]
                        [[:set $drag-start mpos]
                         [:set $pending-intents intents]
                         [:set $pending-init-intents (::init m)]
                         [:set $pending-drop-object (::obj m)]])
                      ;; else
                      intents)))
                body))]
    body))

(defui on-drag-hover
  "Component for adding a hover? state."
  [{:keys [hover? body]}]
  (if hover?
    (ui/wrap-on
     :mouse-move-global
     (fn [handler [x y :as pos]]
       (let [[w h] (ui/bounds body)
             child-intents (handler pos)]
         (if (or (neg? x)
                 (> x w)
                 (neg? y)
                 (> y h))
           (conj child-intents
                 [:set $hover? false])
           child-intents)))
     body)
    (on-drop-move
     (fn [_ _]
       [[:set $hover? true]])
     body)))
