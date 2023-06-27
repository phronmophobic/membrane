(ns membrane.alpha.component.scroll
  (:require [membrane.ui :as ui]))


(defprotocol IDrop
  :extend-via-metadata true
  (-drop [elem pos obj]))

(defprotocol IDropMove
  :extend-via-metadata true  
  (-drop-move [elem pos obj]))

(defn drop
  "Returns the effects of a mouse move event on elem. Will only call -mouse-move on mouse events within an elements bounds."
  ([elem pos obj]
   (when-let [local-pos (ui/within-bounds? elem pos)]
     (-drop elem local-pos obj))))


(defn drop-move
  "Returns the effects of a mouse move event on elem. Will only call -mouse-move on mouse events within an elements bounds."
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
