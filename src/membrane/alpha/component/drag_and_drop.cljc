(ns membrane.alpha.component.drag-and-drop
  (:refer-clojure :exclude [drop abs])
  (:require [membrane.ui :as ui]
            [membrane.component
             :refer [defui]]))


(defprotocol IDrop
  :extend-via-metadata true
  (-drop [elem pos obj]))

(defprotocol IDropMove
  :extend-via-metadata true  
  (-drop-move [elem pos obj]))

(defn drop
  "Returns the effects of a drop event on elem. Will only call -drop on mouse events within an elements bounds.

  Requires that the a drag-and-drop component is wrapping the necessary elements in the stack."
  ([elem pos obj]
   (when-let [local-pos (ui/within-bounds? elem pos)]
     (-drop elem local-pos obj))))

(defn drop-move
  "Returns the effects of a drop event on elem. Will only call -drop-move on mouse events within an elements bounds.

  Requires that the a drag-and-drop component is wrapping the necessary elements in the stack."
  ([elem pos obj]
   (when-let [local-pos (ui/within-bounds? elem pos)]
     (-drop-move elem local-pos obj))))

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
    (when on-drop
      (on-drop pos obj))))

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
    (when on-drop-move
      (on-drop-move pos obj))))

(defn on-drop-move [handler body]
  (OnDropMove. handler body))

(extend-type #?(:clj Object
                :cljs default)

  IDrop
  (-drop [elem local-pos obj]
    (let [intents
          ;; use seq to make sure we don't stop for empty sequences
          (some #(when-let [local-pos (ui/within-bounds? % local-pos)]
                   (seq (-drop % local-pos obj)))
                (reverse (ui/children elem)))]
      (ui/-bubble elem intents)))

  IDropMove
  (-drop-move [elem local-pos obj]
    (let [intents
          ;; use seq to make sure we don't stop for empty sequences
          (some #(when-let [local-pos (ui/within-bounds? % local-pos)]
                   (seq (-drop-move % local-pos obj)))
                (reverse (ui/children elem)))]
      (ui/-bubble elem intents))))


(comment
  (require '[membrane.skia :as skia])
  ,)


(defn ^:private abs [n]
  (Math/abs n))


(defn drag-start? [intent]
  (when (= ::drag-start (first intent))
    intent))

(defui drag-and-drop [{:keys [body
                              pending-intents
                              pending-drop-object
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
                    [:set $pending-drop-object nil]]
                   (handler mpos)))
                :mouse-move
                (fn [handler [mx my]]
                  (when (> (+ (abs (- mx (first drag-start)))
                              (abs (- my (second drag-start))))
                           4)
                    [[:set $drag-start nil]
                     [:set $pending-intents nil]
                     [:set $pending-drop-object nil]
                     [:set $drop-object pending-drop-object]]))
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
                      [[:set $drag-start mpos]
                       [:set $pending-intents intents]
                       [:set $pending-drop-object (nth drag-start-intent 1)]]
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
