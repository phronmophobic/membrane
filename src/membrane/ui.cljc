(ns membrane.ui
  #?(:cljs (:require-macros [membrane.ui :refer [make-event-handler
                                                 cond-let]]))
  #?(:clj (:import javax.imageio.ImageIO))
  (:refer-clojure :exclude [drop]))

(defrecord Font [name size weight width slant])


(def default-font (Font. nil
                         14
                         nil
                         nil
                         nil))

(defn font
  "Creates a font.

  `name`: Should be the path to a font file on desktop. If nil, use the default font.
  `size`: Size of the font. If nil, use the default font size."
  [name size]
  (Font. (if name
           name
           (:name default-font))
         (if size
           size
           (:size default-font))
         (:weight default-font)
         (:width default-font)
         (:slant default-font)))


(defprotocol IMouseMove
  :extend-via-metadata true
  (-mouse-move [elem pos]))
(defprotocol IMouseMoveGlobal
  :extend-via-metadata true
  (-mouse-move-global [elem pos]))
(defprotocol IMouseEvent
  :extend-via-metadata true
  (-mouse-event [elem pos button mouse-down? mods]))
(defprotocol IMouseEnterGlobal
  :extend-via-metadata true
  (-mouse-enter-global [elem enter?]))
(defprotocol IDrop
  :extend-via-metadata true
  (-drop [elem paths pos]))
(defprotocol IScroll
  :extend-via-metadata true
  (-scroll [elem delta mpos]))
(defprotocol IMouseWheel
  :extend-via-metadata true
  (-mouse-wheel [elem delta]))
(defprotocol IKeyPress
  :extend-via-metadata true
  (-key-press [elem key]))
(defprotocol IKeyType
  :extend-via-metadata true
  (-key-type [elem key]))
(defprotocol IClipboardPaste
  :extend-via-metadata true
  (-clipboard-paste [elem contents]))
(defprotocol IClipboardCopy
  :extend-via-metadata true
  (-clipboard-copy [elem]))
(defprotocol IClipboardCut
  :extend-via-metadata true
  (-clipboard-cut [elem]))
(defprotocol IKeyEvent
  :extend-via-metadata true
  (-key-event [this key scancode action mods]))
(defprotocol IHasKeyEvent
  :extend-via-metadata true
  (has-key-event [this]))
(defprotocol IHasKeyPress
  :extend-via-metadata true
  (has-key-press [this]))
(defprotocol IHasMouseMoveGlobal
  :extend-via-metadata true
  (has-mouse-move-global [this]))

(defprotocol IBubble 
  "Allows an element add, remove, modify intents emitted from its children."
  :extend-via-metadata true
  (-bubble [_ intents]
    "Called when an intent is being emitted by a child element. The parent element can either return the same intents or allow them to continue to bubble."))

(defprotocol IMakeNode
  :extend-via-metadata true
  (make-node [node childs]))

(extend-protocol IMakeNode
  #?(:cljs cljs.core/PersistentVector
     :clj clojure.lang.PersistentVector)
  (make-node [node childs]
    (vec childs)))

(declare children)

(defprotocol IOrigin
  :extend-via-metadata true
  (-origin [elem]
    "Specifies the top left corner of a component's bounds

  The origin is vector or 2 numbers [x, y]"))

(extend-protocol IOrigin
  #?(:cljs cljs.core/PersistentVector
     :clj clojure.lang.PersistentVector)
  (-origin [this]
    [0 0])

  nil
  (-origin [this]
    [0 0]))

(defn origin
  "Specifies the top left corner of a component's bounds\n\n  The origin is vector or 2 numbers [x, y]"
  [elem]
  (-origin elem))

(defn origin-x
  "Convience function for returning the x coordinate of elem's origin"
  [elem]
  (first (origin elem)))

(defn origin-y
  "Convience function for returning the y coordinate of elem's origin"
  [elem]
  (second (origin elem)))



(extend-type nil
  IKeyPress
  (-key-press [this info]
    nil)
  IKeyEvent
  (-key-event [this key scancode action mods]
    nil)
  IMouseMoveGlobal
  (-mouse-move-global [this info]
    nil)
  IMouseEnterGlobal
  (-mouse-enter-global [this info]
    nil)
  IHasKeyEvent
  (has-key-event [this]
    false)
  IHasKeyPress
  (has-key-press [this]
    false)
  IHasMouseMoveGlobal
  (has-mouse-move-global [this]
    false)
  IMouseEvent
  (-mouse-event [elem local-pos button mouse-down? mods]
    nil)
  IMouseMove
  (-mouse-move [elem pos]
    nil)
  IScroll
  (-scroll [elem offset local-pos])
  IDrop
  (-drop [elem paths local-pos]
    nil)
  IBubble
  (-bubble [elem intents]
    nil))



(defn -default-mouse-move-global [elem offset]
  (let [[ox oy] (origin elem)
        [sx sy] offset
        child-offset [(- sx ox)
                      (- sy oy)]]
    (let [intents
          (reduce into
                  []
                  (for [child (children elem)]
                    (-mouse-move-global child child-offset)))]
      (-bubble elem intents))))


(declare bounds)

(defn within-bounds? [elem [x y]]
  (let [[ox oy] (origin elem)
        [width height] (bounds elem)
        local-x (- x ox)
        local-y (- y oy)]
    (when (and
           (< local-x
              width)
           (>= local-x 0)
           (< local-y
              height)
           (>= local-y 0))
      [local-x local-y])))

(extend-type #?(:clj Object
                :cljs default)
  IHasKeyEvent
  (has-key-event [this]
    (some has-key-event (children this)))

  IHasKeyPress
  (has-key-press [this]
    (some has-key-press (children this)))

  IHasMouseMoveGlobal
  (has-mouse-move-global [this]
    (some has-mouse-move-global (children this)))

  IMouseMoveGlobal
  (-mouse-move-global [this offset]
    (-default-mouse-move-global this offset))

  IMouseEnterGlobal
  (-mouse-enter-global [this enter?]
    (let [intents (mapcat #(-mouse-enter-global % enter?) (children this))]
      (-bubble this intents)))

  IBubble
  (-bubble [this intents]
    intents)

  IMouseEvent
  (-mouse-event [elem mpos button mouse-down? mods]
    (when-let [local-pos (within-bounds? elem mpos)]
      (let [intents
            (some #(seq (-mouse-event % local-pos button mouse-down? mods))
                  (reverse (children elem)))]
        (-bubble elem intents))))

  IMouseMove
  (-mouse-move [elem mpos]
    (when-let [local-pos (within-bounds? elem mpos)]
      (let [intents
            (some #(seq (-mouse-move % local-pos))
                  (reverse (children elem)))]
        (-bubble elem intents))))

  IScroll
  (-scroll [elem offset mpos]
    (when-let [local-pos (within-bounds? elem mpos)]
      (let [intents
            (some #(seq (-scroll % offset local-pos))
                  (reverse (children elem)))]
        (-bubble elem intents))))

  IDrop
  (-drop [elem paths mpos]
    (when-let [local-pos (within-bounds? elem mpos)]
      (let [intents
            (some #(seq (-drop % paths local-pos))
                  (reverse (children elem)))]
        (-bubble elem intents))))

  IKeyPress
  (-key-press [this info]
    (let [intents (mapcat #(-key-press % info) (children this))]
      (-bubble this intents)))

  IKeyEvent
  (-key-event [this key scancode action mods]
    (let [intents (mapcat #(-key-event % key scancode action mods) (children this))]
      (-bubble this intents))))


(def SHIFT-MASK 0x0001)
(def CONTROL-MASK 0x0002)
(def ALT-MASK 0x0004)
(def SUPER-MASK 0x0008)
(def CAPS-LOCK-MASK 0x0010)
(def NUM-LOCK-MASK 0x0020)



(defonce default-draw-impls (atom {}))

(swap! default-draw-impls
       assoc nil (fn [draw]
                   (fn [this])))

(swap! default-draw-impls
       assoc #?(:cljs cljs.core/PersistentVector
                :clj clojure.lang.PersistentVector)
       (fn [draw]
         (fn [this]
           (doseq [drawable this]
             (draw drawable)))))

(swap! default-draw-impls
       assoc #?(:clj Object
                :cljs :default)
       (fn [draw]
         (fn [this]
           (draw (children this)))))

(defprotocol IBounds
  :extend-via-metadata true
  (-bounds [elem]
    "Returns a 2 element vector with the [width, height] of an element's bounds with respect to its origin"))

(def
  ^{:arglists '([elem])
    :doc
    "Returns a 2 element vector with the [width, height] of an element's bounds with respect to its origin"}
  bounds (memoize #(-bounds %)))

(defn child-bounds [elem]
  (let [[ox oy] (origin elem)
        [w h] (bounds elem)]
    [(+ ox w)
     (+ oy h)]))

(extend-protocol IBounds
  #?(:cljs cljs.core/PersistentVector
     :clj clojure.lang.PersistentVector)
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     this))

  nil
  (-bounds [this]
    [0 0]))


;; #?
;; (:clj
;;  (do
;;    (defprotocol PWrapped
;;      (-unwrap [this]))

;;    (defn wrap [o]
;;      (reify
;;        Object
;;        (hashCode [_] (System/identityHashCode o))
;;        PWrapped
;;        (-unwrap [_] o)))))


(defn memoize-var
  "Returns a memoized version of a referentially transparent function. The
  memoized version of the function keeps a cache of the mapping from arguments
  to results and, when calls with the same arguments are repeated often, has
  higher performance at the expense of higher memory use."
  {:added "1.0"
   :static true}
  [f]
  (let [mem (volatile! {})]
    (fn [& args]
      (if-let [e (find @mem args)]
        (val e)
        (let [ret (apply f args)]
          (vswap! mem assoc args ret)
          ret)))))


(defprotocol IChildren
  :extend-via-metadata true
  (-children [elem]
    "Returns sub elements of elem. Useful for traversal."))


(extend-protocol IChildren
  #?(:cljs cljs.core/PersistentVector
     :clj clojure.lang.PersistentVector)
  (-children [this]
    this)
  nil
  (-children [this]
    nil))



(extend-type #?(:clj Object
                :cljs default)
  IChildren
  (-children [this]
    nil))

(defn children
  "Returns sub elements of elem. Useful for traversal."
  [elem]
  (-children elem))

(defn width
  "Returns the width of elem."
  [elem]
  (let [[width height] (bounds elem)]
    width))
(defn height
  "Returns the height of elem."
  [elem]
  (let [[width height] (bounds elem)]
    height))






(defn mouse-move
  "Returns the intents of a mouse move event on elem."
  ([elem pos]
   (-mouse-move elem pos)))

;; TODO: make-mouse-move global work when the top level elem has an offset
;; (ui/mouse-move-global
;;                     (ui/translate 10 10
;;                                   (ui/on-mouse-move-global
;;                                    (fn [pos]
;;                                      [[:the-pos pos]])
;;                                    (ui/spacer 20 20)))
;;                     [10 10])
;; (ui/mouse-move-global
;;                     (ui/translate 10 10
;;                                   (ui/on-mouse-move-global
;;                                    (fn [pos]
;;                                      [[:the-pos pos]])
;;                                    (ui/spacer 20 20)))
;;                     [0 0])
(defn mouse-move-global
  "Returns the intents of a mouse move event on elem. Will -mouse-move-global for all elements and their children."
  ([elem global-pos]
   (-mouse-move-global elem global-pos)))

(defn mouse-event [elem pos button mouse-down? mods]
  (-mouse-event elem pos button mouse-down? mods))

(defn mouse-down
  "Returns the intents of a mouse down event on elem."
  [elem [mx my :as pos]]
  (mouse-event elem pos 0 true 0))

(defn mouse-up
  "Returns the intents of a mouse up event on elem."
  [elem [mx my :as pos]]
  (mouse-event elem pos 0 false 0))

(defn mouse-enter-global
  "Returns the intents of an event representing when the mouse enters or leaves the window.

  Note: This event is new and is not implemented for all backends."
  [elem enter?]
  (-mouse-enter-global elem enter?))

(defn drop [elem paths pos]
  (-drop elem paths pos))

(defn scroll [elem offset pos]
  (-scroll elem offset pos))


(defmacro make-event-handler [protocol-name protocol protocol-fn]
  `(fn handler# [elem# & args#]
     #_(when-not (or (satisfies? protocol elem#) (satisfies? IComponent elem#))
         (throw (Exception. (str "Expecting " protocol-name " or IComponent, got " (type elem#) " " elem#))))
     (cond
       (satisfies? ~protocol elem#)
       (apply ~protocol-fn elem# args#)
       (satisfies? IChildren elem#)
       (let [intents# (transduce
                    (map #(apply handler# % args#))
                    into
                    []
                    (children elem#))]
         (-bubble elem# intents#)))))

(def
  ^{:arglists '([elem key]),
    :doc "Returns the intents of a key press event on elem."}
  key-press (make-event-handler "IKeyPress" IKeyPress -key-press))
(def
  ^{:arglists '([elem key scancode action mods]),
    :doc "Returns the intents of a key event on elem."}
  key-event (make-event-handler "IKeyEvent" IKeyEvent -key-event))
(def
  ^{:arglists '([elem]),
    :doc "Returns the intents of a clipboard cut event on elem."}
  clipboard-cut (make-event-handler "IClipboardCut" IClipboardCut -clipboard-cut))
(def
  ^{:arglists '([elem]),
    :doc "Returns the intents of a clipboard copy event on elem."}
  clipboard-copy (make-event-handler "IClipboardCopy" IClipboardCopy -clipboard-copy))
(def
  ^{:arglists '([elem s]),
    :doc "Returns the intents of a clipboard paste event on elem."}
  clipboard-paste (make-event-handler "IClipboardPaste" IClipboardPaste -clipboard-paste))

(defrecord Label [text font]
    IOrigin
    (-origin [_]
        [0 0]))

(defn label
  "Graphical elem that can draw text.

  label will use the default line spacing for newline.
  font should be a membrane.ui.Font"
  ([text]
   (label (str text) default-font))
  ([text font]
   (Label. (str text) font)))

(defn pr-label
  ([x]
   (pr-label x 30))
  ([x max-length]
   (pr-label x max-length nil))
  ([x max-length font]
   (let [s (pr-str x)
         s (if max-length
             (subs s 0 (min max-length (count s)))
             s)]
    (label s (or font default-font)))))

(defrecord TextSelection [text selection font]
    IOrigin
    (-origin [_]
        [0 0]))

(defn text-selection
  "Graphical elem for drawing a selection of text."
  ([text [selection-start selection-end :as selection]]
   (TextSelection. (str text) selection default-font))
  ([text [selection-start selection-end :as selection] font]
   (TextSelection. (str text) selection font)))


(defrecord TextCursor [text cursor font]
    IOrigin
    (-origin [_]
        [0 0]))

(defn text-cursor
  "Graphical elem that can draw a text cursor

   font should be a membrane.ui.Font"
  ([text cursor]
   (TextCursor. (str text) cursor default-font))
  ([text cursor font]
   (TextCursor. (str text) cursor font)))



(defrecord Image [image-path size opacity]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
  (-bounds [_]
    size)
)




#?
(:clj
 (do
   (defn- image-size-raw [image-path]
     (try
       (try
         ;; quack! works for BufferedImage and cljfx Image
         [(.getWidth image-path)
          (.getHeight image-path)]
         (catch Exception e
           (with-open [is (clojure.java.io/input-stream image-path)]
             (let [image-stream (ImageIO/createImageInputStream is)
                   buffered-image (ImageIO/read image-stream)]
               [(.getWidth buffered-image)
                (.getHeight buffered-image)]))))
       (catch Exception e
         (.printStackTrace e)
         [0 0])))

   (def image-size* (atom (memoize image-size-raw)))

   (defn image-size
     "Returns the [width, height] of the file at image-path."
     [image-path]
     (@image-size* image-path)))

 :cljs
 (defn image-size [image-path]
   (assert false "image size should be replaced by implementation")))

(defn image
  "Graphical element that draws an image.

  `image-path`: using the skia backend, `image-path` can be one of
  - a string filename
  - a java.net.URL
  - a byte array containing the bytes of supported image format
  This is useful for drawing images included in a jar. Simply put your image in your resources folder, typically resources.
  Draw the images in the jar with `(ui/image (clojure.java.io/resource \"filename.png\"))`

  The image can be drawn at a different size by supplying a size.
  Supply a nil size will use the the original image size.

  The image can be aspect scaled by supply a size with one of the dimensions as nil.

  For example, to draw an image with width 30 with aspect scaling, `(image \"path.png\" [30 nil])`

  opacity is a float between 0 and 1.

  Allowable image formats may vary by platform, but will typically include png and jpeg.
  "
  ([image-path]
   (image image-path nil nil))
  ([image-path [width height :as size]]
   (image image-path size nil))
  ([image-path [width height :as size] opacity]
   (let [size (if (nil? size)
                (image-size image-path)
                (let [[w h] size]
                  (cond
                    (and w h)
                    [w h]

                    (and w (nil? h))
                    (let [[actual-width actual-height] (image-size image-path)]
                      [w (* actual-height
                            (/ w (max 1 actual-width)))])

                    (and (nil? w) h)
                    (let [[actual-width actual-height] (image-size image-path)]
                      [(* actual-width
                          (/ h (max 1 actual-height)))
                       h])

                    :else
                    (image-size image-path))))
         opacity (if (nil? opacity)
                   1
                   opacity)]
     (Image. image-path size opacity))))


;; (defrecord Group [drawables]
;;     IOrigin
;;     (-origin [_]
;;         [0 0])

;;   IBounds
;;   (-bounds [this]
;;     (reduce
;;      (fn [[max-width max-height] elem]
;;        (let [[ox oy] (origin elem)
;;              [w h] (bounds elem)]
;;          [(max max-width (+ ox w))
;;           (max max-height (+ oy h))]))
;;      [0 0]
;;      drawables))

;;   IChildren
;;   (-children [this]
;;     drawables))

;; (swap! default-draw-impls
;;        assoc Group (fn [draw]
;;                      (fn [this]
;;                        (doseq [drawable (:drawables this)]
;;                          (draw drawable)))))

;; (defn group
;;   "Creates a graphical elem that will draw drawables in order"
;;   [& drawables]
;;   (Group. drawables))


(defrecord Translate [x y drawable]
    IOrigin
    (-origin [this]
        [x y])

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (Translate. x y (first childs)))

  IChildren
  (-children [this]
      [drawable])

  IBounds
  (-bounds [this]
    (child-bounds drawable)))

(defn translate
  "A graphical elem that will shift drawable's origin by x and y and draw it at its new origin."
  [x y drawable]
  (Translate. x y drawable))


(defrecord Rotate [degrees drawable]
    IOrigin
    (-origin [this]
        [0 0])

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (Rotate. degrees (first childs)))

  IChildren
  (-children [this]
      [drawable])

  IBounds
  (-bounds [this]
      (child-bounds drawable)))

(defn- rotate [degrees drawable]
  (Rotate. degrees drawable))

(defrecord AffineTransform [matrix drawable])
(defrecord Skew [sx sy drawable])

(defrecord Spacer [x y]
    IOrigin
    (-origin [_]
        [0 0])
    IBounds
    (-bounds [this]
        [x y]))

(swap! default-draw-impls
       assoc Spacer (fn [draw]
                      (fn [this])))

(defn spacer
  "An empty graphical element with width x and height y.

  Useful for layout."

  ([x]
   (Spacer. x x))
  ([x y]
   (Spacer. x y)))


(defrecord FixedBounds [size drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        size)

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (FixedBounds. size (first childs)))

  IChildren
  (-children [this]
      [drawable]))

(swap! default-draw-impls
       assoc FixedBounds
       (fn [draw]
         (fn [this]
           (draw (:drawable this)))))

(defn fixed-bounds [size drawable]
  (FixedBounds. size drawable))

(defrecord Padding [top right bottom left drawable]
  IOrigin
  (-origin [this]
    [0 0])

  IMouseEvent
  (-mouse-event [this [mx my :as pos] button mouse-down? mods]
    (when-let [[mx my] (within-bounds? this pos)]
      (mouse-event drawable [(- mx left)
                             (- my top)] button mouse-down? mods)))

  IScroll
  (-scroll [this input-offset [mx my :as pos]]
    (scroll drawable
            input-offset
            [(- mx left)
             (- my top)]))

  IDrop
  (-drop [this paths [mx my :as pos]]
    (drop drawable
          paths
          [(- mx left)
           (- my top)]))

  IMouseMove
  (-mouse-move [this [mx my :as pos]]
    (when-let [[mx my] (within-bounds? this pos)]
      (mouse-move drawable [(- mx left)
                            (- my top)])))

  IMouseMoveGlobal
  (-mouse-move-global [this mouse-offset]
    (let [[mx my] mouse-offset]
      (-default-mouse-move-global this [(- mx left)
                                        (- my top)])))

  IMakeNode
  (make-node [this childs]
    (assert (= (count childs) 1))
    (Padding. left right bottom top (first childs)))

  IChildren
  (-children [this]
    [drawable])

  IBounds
  (-bounds [this]
    (let [[w h] (child-bounds drawable)]
      [(+ w left right)
       (+ h top bottom)])))

(swap! default-draw-impls
       assoc Padding
       (fn [draw]
         (fn [this]
           (draw
            (translate (:left this) (:top this)
                       (:drawable this))))))

(defn padding
  "Adds empty space around an element. The bounds of the returned element will include the padding.

  ;; Add 5 pixels of empty space around all 4 sides of `elem`
  (padding 5 elem)

  ;; Add 3 pixels of padding to the left and right
  ;; and 5 pixels to the top and bottom
  (padding 3 5 elem)

  ;; 1 px to the top
  ;; 2 pixels to the right
  ;; 3 pixels to the bottom
  ;; 4 pixels to the left.
  (padding 1 2 3 4 elem)"
  ([p elem]
   (Padding. p p p p elem))
  ([px py elem]
   (Padding. py px py px elem))
  ([top right bottom left elem]
   (Padding. top right bottom left elem)))


(defrecord Path [points]
    IOrigin
    (-origin [_]
        [0 0])
  IBounds
  (-bounds [this]
    (let [maxx (apply max (map first points))
          maxy (apply max (map second points))]
      [maxx maxy])))


(defn path
  "A graphical element that will draw lines connecting points.

  See with-style, with-stroke-width, and with-color for more options."
  [& points]
  (Path. points))


(defrecord WithColor [color drawables]
    IOrigin
    (-origin [_]
        [0 0])
  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

    IMakeNode
    (make-node [this childs]
      (WithColor. color childs))

  IChildren
  (-children [this]
    drawables))

(defn with-color
  "Use color for all children. Color is a vector of [r g b] or [r g b a]. All values should be between 0 and 1 inclusive."
  [color & drawables]
  (WithColor. color drawables))

(defrecord WithStyle [style drawables]
    IOrigin
    (-origin [_]
        [0 0])
    IBounds
    (-bounds [this]
        (bounds drawables))

    IMakeNode
    (make-node [this childs]
      (WithStyle. style childs))

    IChildren
    (-children [this]
        drawables))

(defn with-style
  "Style for drawing paths and polygons

  style is one of:
:membrane.ui/style-fill
:membrane.ui/style-stroke
:membrane.ui/style-stroke-and-fill
"
  [style & drawables]
  (WithStyle. style (vec drawables)))

(defrecord WithStrokeWidth [stroke-width drawables]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (bounds drawables))

    IMakeNode
    (make-node [this childs]
      (WithStrokeWidth. stroke-width childs))

    IChildren
    (-children [this]
        drawables))
(defn with-stroke-width
  "Set the stroke width for drawables."
  [stroke-width & drawables]
  (WithStrokeWidth. stroke-width (vec drawables)))


(defrecord Scale [scalars drawables]
  IOrigin
  (-origin [_]
    [0 0])
  IBounds
  (-bounds [this]
    (let [[w h] (bounds drawables)
          [sx sy] scalars]
      [(* w sx)
       (* h sy)]))

  IMouseEvent
  (-mouse-event [this mpos button mouse-down? mods]
    (when-let [mpos (within-bounds? this mpos)]
      (mouse-event drawables [(/ (nth mpos 0)
                                 (nth scalars 0))
                              (/ (nth mpos 1)
                                 (nth scalars 1))]
                   button mouse-down? mods)))

  IScroll
  (-scroll [this input-offset mpos]
    (scroll drawables
            input-offset
            [(/ (nth mpos 0)
                (nth scalars 0))
             (/ (nth mpos 1)
                (nth scalars 1))]))

  IDrop
  (-drop [this paths mpos]
    (drop drawables
          paths
          [(/ (nth mpos 0)
              (nth scalars 0))
           (/ (nth mpos 1)
              (nth scalars 1))]))

  IMouseMove
  (-mouse-move [this mpos]
    (when-let [mpos (within-bounds? this mpos)]
      (mouse-move drawables
                  [(/ (nth mpos 0)
                      (nth scalars 0))
                   (/ (nth mpos 1)
                      (nth scalars 1))])))

  IMouseMoveGlobal
  (-mouse-move-global [this mpos]
    (-default-mouse-move-global this
                                [(/ (nth mpos 0)
                                    (nth scalars 0))
                                 (/ (nth mpos 1)
                                    (nth scalars 1))]))

  IMakeNode
  (make-node [this childs]
    (Scale. scalars childs))

  IChildren
  (-children [this]
    drawables))

(defn scale
  "Draw drawables using scalars which is a vector of [scale-x scale-y]"
  [sx sy & drawables]
  (Scale. [sx sy] (vec drawables)))


(defrecord Arc [radius rad-start rad-end steps]

    IOrigin
    (-origin [_]
        [0 0])
  IBounds
  (-bounds [this]
      [0 0]))

(defn- arc [radius rad-start rad-end]
  (Arc. radius rad-start rad-end 10))

(defrecord Rectangle [width height]
    IOrigin
    (-origin [_]
        [0 0])
  IBounds
  (-bounds [this]
      [width height]))

(swap! default-draw-impls
       assoc Rectangle
       (fn [draw]
         (fn [this]
           (let [{:keys [width height]} this]
             (draw (path [0 0] [0 height] [width height] [width 0] [0 0]))))))

(defn rectangle
  "Graphical elem that draws a rectangle.

  See with-style, with-stroke-width, and with-color for more options."
  [width height]
  (Rectangle. width height))

(defn filled-rectangle
  "Graphical elem that draws a filled rectangle with color, [r g b] or [r g b a]."
  [color width height]
  (with-color color
    (with-style :membrane.ui/style-fill
      (Rectangle. width height))))

(defrecord RoundedRectangle [width height border-radius]
    IOrigin
    (-origin [_]
        [0 0])
  IBounds
  (-bounds [this]
      [width height]))

(defn rounded-rectangle
  "Graphical elem that draws a rounded rectangle."
  [width height border-radius]
  (RoundedRectangle. width height border-radius))

(defn bordered-draw [this]
  (let [drawable (:drawable this)
        [width height] (child-bounds drawable)]
    [(let [gray  0.65]
       (with-color [gray gray gray]
         (with-style ::style-stroke
           (rectangle width height))))
     drawable]))

(defrecord Bordered [drawable]
    IOrigin
    (-origin [this]
      [0 0])

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (Bordered. (first childs)))


  IChildren
  (-children [this]
      (bordered-draw this))

  IBounds
  (-bounds [this]
    (child-bounds drawable)))

(swap! default-draw-impls
       assoc Bordered
       (fn [draw]
         (fn [this]
           (draw (bordered-draw this)))))

(defn bordered
  "Graphical elem that will draw drawable with a gray border. Also allows a specified padding.

  ;; Add a border without padding
  (bordered elem)

  ;; Add border with 5 px padding around elem
  (bordered 5 elem)

  ;; Add border
  ;; Add 3 pixels of padding to the left and right
  ;; and 5 pixels to the top and bottom
  (bordered [3 5] elem)

  ;; Add border
  ;; 1 px to the top
  ;; 2 pixels to the right
  ;; 3 pixels to the bottom
  ;; 4 pixels to the left.
  (bordered [1 2 3 4] elem)"
  ([elem]
   (Bordered. elem))
  ([pad elem]
   (if (number? pad)
     (Bordered.
      (padding pad elem))
     ;; assume seq
     (case (count pad)
       2 (Bordered.
          (padding (nth pad 0)
                   (nth pad 1)
                   elem))
       4 (Bordered.
          (padding (nth pad 0)
                   (nth pad 1)
                   (nth pad 2)
                   (nth pad 3)
                   elem))))))

(defn fill-bordered-draw [this]
  (let [{:keys [color drawable]} this
        [width height] (child-bounds drawable)]
    [(filled-rectangle color
                       width height)
     drawable]))

(defrecord FillBordered [color drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (FillBordered. color (first childs)))

  IChildren
  (-children [this]
      [drawable])

  IBounds
  (-bounds [this]
    (child-bounds drawable)))

(swap! default-draw-impls
       assoc FillBordered
       (fn [draw]
         (fn [this]
           (draw (fill-bordered-draw this)))))

(defn fill-bordered
  "Graphical elem that will draw elem with filled border."
  [color pad drawable]
  (if (vector? pad)
    (let [[px py] pad]
      (FillBordered.
       color
       (padding px py
                drawable)))
    (FillBordered.
     color
     (padding pad drawable))))

(defn draw-checkbox [checked?]
  (if checked?
    (let [border [0.14901960784313725 0.5254901960784314 0.9882352941176471]
          fill [0.2 0.5607843137254902 0.9882352941176471]]
      (with-style ::style-stroke
        [(with-style ::style-fill
           (with-color fill
             (rounded-rectangle 12 12 2)))

         (with-color border
           (rounded-rectangle 12 12 2))

         (translate 0 1
                    (with-stroke-width
                      1.5
                      (with-color [0 0 0 0.3]
                        (path [2 6] [5 9] [10 2]))))

         (with-stroke-width
           1.5
           (with-color [1 1 1]
             (path [2 6] [5 9] [10 2])))
         ]))
    (let [gray 0.6862745098039216]
      (with-style ::style-stroke
                 (with-color [gray gray gray ]
                               (rounded-rectangle 12 12 2))))))



(defrecord Checkbox [checked?]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (bounds (draw-checkbox checked?)))

    IChildren
    (-children [this]
        [(draw-checkbox checked?)]))

(swap! default-draw-impls
       assoc Checkbox
       (fn [draw]
         (fn [this]
           (draw (draw-checkbox (:checked? this))))))

(defn checkbox
  "Graphical elem that will draw a checkbox."
  [checked?]
  (Checkbox. checked?))


(defn box-contains?
  "Tests whether [px py] is within  [x y width height]."
  [[x y width height] [px py]]
  (and (<= px (+ x width))
       (>= px x)
       (<= py (+ y height))
       (>= py y)))

(defn button-draw [this]
  (let [text (:text this)
        [text-width text-height] (bounds (label text))
        padding 12
        rect-width (+ text-width padding)
        rect-height (+ text-height padding)
        border-radius 3
        ]
    [
     (when (:hover? this)
       (with-color [0.9 0.9 0.9]
         (rounded-rectangle rect-width rect-height border-radius)))
     (with-style ::style-stroke
       [
        (with-color [0.76 0.76 0.76 1]
          (rounded-rectangle (+ 0.5 rect-width) (+ 0.5 rect-height) border-radius))
        (with-color [0.85 0.85 0.85]
          (rounded-rectangle rect-width rect-height border-radius))])

     (translate (/ padding 2)
                (- (/ padding 2) 2)
                (label text))]))

(defrecord Button [text on-click hover?]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [_]
    (let [[text-width text-height] (bounds (label text))
          padding 12
          btn-width (+ text-width padding)
          btn-height (+ text-height padding)]
      [btn-width btn-height]))

  IMouseEvent
  (-mouse-event [this pos button mouse-down? mods]
    (when (and mouse-down? on-click (within-bounds? this pos))
      (on-click))))

(swap! default-draw-impls
       assoc Button
       (fn [draw]
         (fn [this]
           (draw (button-draw this)))))

(defn button
  "Graphical elem that draws a button. Optional on-click function may be provided that is called with no arguments when button has a mouse-down event."
  ([text]
   (Button. text nil false))
  ([text on-click]
   (Button. text on-click false))
  ([text on-click hover?]
   (Button. text on-click hover?)))


(defrecord OnClick [on-click drawables]
    IOrigin
    (-origin [_]
        [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))


    IMakeNode
    (make-node [this childs]
      (OnClick. on-click childs))


  IChildren
  (-children [this]
    drawables)

  IMouseEvent
  (-mouse-event [this pos button mouse-down? mods]
    (when (and mouse-down? on-click (within-bounds? this pos))
      (on-click))))

(swap! default-draw-impls
       assoc OnClick
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-click
  "Wrap an element with a mouse down event handler, on-click. 

  on-click must accept 0 arguments and should return a sequence of intents."
  [on-click & drawables]
  (OnClick. on-click drawables))


;; Keeping for backwards compatibility
;; in the offchance someone cares about this type.
(defrecord OnMouseDown [on-mouse-down drawables]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))


  IMakeNode
  (make-node [this childs]
    (OnMouseDown. on-mouse-down childs))


  IChildren
  (-children [this]
    drawables)

  IMouseEvent
  (-mouse-event [this pos button mouse-down? mods]
    (if mouse-down?
      (when on-mouse-down
        (when-let [local-pos (within-bounds? this pos)]
          (on-mouse-down local-pos)))
      (when-let [local-pos (within-bounds? this pos)]
        (let [intents
              (some #(seq (-mouse-event % local-pos button mouse-down? mods))
                    (reverse (children this)))]
          intents)))))

(swap! default-draw-impls
       assoc OnMouseDown
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-down
  "Wraps drawables and adds an event handler for mouse-down events.

  on-mouse-down should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of intents."
  [on-mouse-down & drawables]
  (OnMouseDown. on-mouse-down drawables))

(defrecord OnMouseDownRaw [on-mouse-down-raw drawables]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))


  IMakeNode
  (make-node [this childs]
    (OnMouseDownRaw. on-mouse-down-raw childs))


  IChildren
  (-children [this]
    drawables)

  IMouseEvent
  (-mouse-event [this pos button mouse-down? mods]
    (if mouse-down?
      (when on-mouse-down-raw
        (on-mouse-down-raw pos))
      (when-let [local-pos (within-bounds? this pos)]
        (let [intents
              (some #(seq (-mouse-event % local-pos button mouse-down? mods))
                    (reverse (children this)))]
          intents)))))

(swap! default-draw-impls
       assoc OnMouseDownRaw
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-down-raw
  "Wraps drawables and adds an event handler for mouse-down events. 
  Unlike `on-mouse-down`, does not ignore events outside the bounds of `drawables`.

  on-mouse-down-raw should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of intents."
  [on-mouse-down-raw & drawables]
  (OnMouseDownRaw. on-mouse-down-raw drawables))

;; Keeping for backwards compatibility
;; in the offchance someone cares about this type.
(defrecord OnMouseUp [on-mouse-up drawables]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

  IMakeNode
  (make-node [this childs]
    (OnMouseUp. on-mouse-up childs))

  IChildren
  (-children [this]
    drawables)

  IMouseEvent
  (-mouse-event [this pos button mouse-down? mods]
    (if mouse-down?
      (when-let [local-pos (within-bounds? this pos)]
        (let [intents
              (some #(seq (-mouse-event % local-pos button mouse-down? mods))
                    (reverse (children this)))]
          intents))
      (when on-mouse-up
        (when-let [local-pos (within-bounds? this pos)]
          (on-mouse-up local-pos))))))

(swap! default-draw-impls
       assoc OnMouseUp
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-up
  "Wraps drawables and adds an event handler for mouse-up events.

  on-mouse-up should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of intents."
  [on-mouse-up & drawables]
  (OnMouseUp. on-mouse-up drawables))

(defrecord OnMouseUpRaw [on-mouse-up-raw drawables]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

  IMakeNode
  (make-node [this childs]
    (OnMouseUpRaw. on-mouse-up-raw childs))

  IChildren
  (-children [this]
    drawables)

  IMouseEvent
  (-mouse-event [this pos button mouse-down? mods]
    (if mouse-down?
      (when-let [local-pos (within-bounds? this pos)]
        (let [intents
              (some #(seq (-mouse-event % local-pos button mouse-down? mods))
                    (reverse (children this)))]
          intents))
      (when on-mouse-up-raw
        (on-mouse-up-raw pos)))))

(swap! default-draw-impls
       assoc OnMouseUpRaw
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-up-raw
  "Wraps drawables and adds an event handler for mouse-up events.
  Unlike `on-mouse-up`, does not ignore events outside the bounds of `drawables`.

  on-mouse-up should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of intents."
  [on-mouse-up-raw & drawables]
  (OnMouseUpRaw. on-mouse-up-raw drawables))

(defrecord OnMouseMove [on-mouse-move drawables]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

  IMakeNode
  (make-node [this childs]
    (OnMouseMove. on-mouse-move childs))

  IChildren
  (-children [this]
    drawables)

  IMouseMove
  (-mouse-move [this [mx my :as pos]]
    (when on-mouse-move
      (when-let [pos (within-bounds? this pos)]
        (on-mouse-move pos)))))

(swap! default-draw-impls
       assoc OnMouseMove
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-move
  "Wraps drawables and adds an event handler for mouse-move events.

  on-mouse-move down should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of intents."
  [on-mouse-move & drawables]
  (OnMouseMove. on-mouse-move drawables))

(defrecord OnMouseMoveRaw [on-mouse-move-raw drawables]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

  IMakeNode
  (make-node [this childs]
    (OnMouseMove. on-mouse-move childs))

  IChildren
  (-children [this]
    drawables)

  IMouseMove
  (-mouse-move [this [mx my :as pos]]
    (when on-mouse-move-raw
      (on-mouse-move-raw pos))))

(swap! default-draw-impls
       assoc OnMouseMoveRaw
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-move-raw
  "Wraps drawables and adds an event handler for mouse-move events.
  Unlike `on-mouse-move`, does not ignore events outside the bounds of `drawables`.

  on-mouse-move down should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of intents."
  [on-mouse-move-raw & drawables]
  (OnMouseMoveRaw. on-mouse-move-raw drawables))

(defrecord OnMouseMoveGlobal [on-mouse-move-global drawables]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

  IMakeNode
  (make-node [this childs]
    (OnMouseMoveGlobal. on-mouse-move-global childs))

  IChildren
  (-children [this]
    drawables)

  IHasMouseMoveGlobal
  (has-mouse-move-global [this]
    true)

  IMouseMoveGlobal
  (-mouse-move-global [this pos]
    (when on-mouse-move-global
      (on-mouse-move-global pos))))

(swap! default-draw-impls
       assoc OnMouseMoveGlobal
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-move-global
  "Wraps drawables and adds an event handler for mouse-move-global events.

  on-mouse-move-global down should take 1 argument [mx my] of the mouse position in global coordinates and return a sequence of intents."
  [on-mouse-move-global & drawables]
  (OnMouseMoveGlobal. on-mouse-move-global drawables))

(defrecord OnMouseEnterGlobal [on-mouse-enter-global drawables]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

  IMakeNode
  (make-node [this childs]
    (OnMouseEnterGlobal. on-mouse-enter-global childs))

  IChildren
  (-children [this]
    drawables)

  IMouseEnterGlobal
  (-mouse-enter-global [this enter?]
    (when on-mouse-enter-global
      (on-mouse-enter-global enter?))))

(swap! default-draw-impls
       assoc OnMouseEnterGlobal
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-enter-global
  "Wraps drawables and adds an event handler for mouse-enter-global events.

  on-mouse-enter-global down should take 1 argument `enter?`
  that represents the mouse entering or leaving
  the window.

  Returns a sequence of intents."
  [on-mouse-enter-global & drawables]
  (OnMouseEnterGlobal. on-mouse-enter-global drawables))

(defrecord OnMouseEvent [on-mouse-event drawables]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (reduce
         (fn [[max-width max-height] elem]
           (let [[ox oy] (origin elem)
                 [w h] (bounds elem)]
             [(max max-width (+ ox w))
              (max max-height (+ oy h))]))
         [0 0]
         drawables))

    IMakeNode
    (make-node [this childs]
      (OnMouseEvent. on-mouse-event childs))


    IChildren
    (-children [this]
        drawables)

    IMouseEvent
    (-mouse-event [this pos button mouse-down? mods]
      (when on-mouse-event
        (when-let [local-pos (within-bounds? this pos)]
          (on-mouse-event local-pos button mouse-down? mods)))))

(swap! default-draw-impls
       assoc OnMouseEvent
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-event
  "Wraps drawables and adds an event handler for mouse events.

  on-mouse-event should take 4 arguments [pos button mouse-down? mods] and return a sequence of intents."
  [on-mouse-event & drawables]
  (OnMouseEvent. on-mouse-event drawables))


(defrecord OnDrop [on-drop drawables]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (reduce
         (fn [[max-width max-height] elem]
           (let [[ox oy] (origin elem)
                 [w h] (bounds elem)]
             [(max max-width (+ ox w))
              (max max-height (+ oy h))]))
         [0 0]
         drawables))

    IMakeNode
    (make-node [this childs]
      (OnDrop. on-drop childs))


    IChildren
    (-children [this]
        drawables)

    IDrop
    (-drop [this paths pos]
      (when on-drop
        (when-let [pos (within-bounds? this pos)]
          (on-drop paths pos)))))

(swap! default-draw-impls
       assoc OnDrop
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-drop
  "Wraps drawables and adds an event handler for drop events.

  on-drop should take 2 arguments [paths pos] and return a sequence of intents."
  [on-drop & drawables]
  (OnDrop. on-drop drawables))

(defrecord OnKeyPress [on-key-press drawables]
    IOrigin
    (-origin [_]
        [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

  IHasKeyPress
  (has-key-press [this]
      (boolean on-key-press))

  IKeyPress
  (-key-press [this key]
    (when on-key-press
      (on-key-press key)))

    IMakeNode
    (make-node [this childs]
      (OnKeyPress. on-key-press childs))


  IChildren
  (-children [this]
      drawables))

(swap! default-draw-impls
       assoc OnKeyPress
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-key-press
  "Wraps drawables and adds an event handler for key-press events.

  on-key-press should take 1 argument key and return a sequence of intents."
  [on-key-press & drawables]
  (OnKeyPress. on-key-press drawables))

(defrecord OnKeyEvent [on-key-event drawables]
    IOrigin
    (-origin [_]
        [0 0])


  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))
  IHasKeyEvent
  (has-key-event [this]
      (boolean on-key-event))

  IKeyEvent
  (-key-event [this key scancode action mods]
      (when on-key-event
        (on-key-event key scancode action mods)))

    IMakeNode
    (make-node [this childs]
      (OnKeyEvent. on-key-event childs))


  IChildren
  (-children [this]
      drawables))

(swap! default-draw-impls
       assoc OnKeyEvent
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-key-event
  "Wraps drawables and adds a handler for key events.

  on-key-event should take 4 arguments key, scancode, action, mods and return a sequence of intents."
  [on-key-event & drawables]
  (OnKeyEvent. on-key-event drawables))

(defrecord OnBubble [on-bubble drawables]
    IOrigin
    (-origin [_]
        [0 0])


  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

    IMakeNode
    (make-node [this childs]
      (OnBubble. on-bubble childs))


  IChildren
  (-children [this]
      drawables)

   IBubble
   (-bubble [this intents]
       (on-bubble intents)))

(swap! default-draw-impls
       assoc OnBubble
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-bubble
  "Wraps drawables and adds a handler for bubbling

  on-bubble should take seq of intents"
  [on-bubble & drawables]
  (OnBubble. on-bubble drawables))


(defrecord OnClipboardPaste [on-clipboard-paste drawables]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (reduce
         (fn [[max-width max-height] elem]
           (let [[ox oy] (origin elem)
                 [w h] (bounds elem)]
             [(max max-width (+ ox w))
              (max max-height (+ oy h))]))
         [0 0]
         drawables))

    IMakeNode
    (make-node [this childs]
      (OnClipboardPaste. on-clipboard-paste childs))


    IChildren
    (-children [this]
        drawables)

    IClipboardPaste
    (-clipboard-paste [this s]
        (when on-clipboard-paste
          (on-clipboard-paste s))))

(swap! default-draw-impls
       assoc OnClipboardPaste
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-clipboard-paste
  "Wraps drawables and adds a handler for clipboard paste events.

  on-clipboard-paste should take 1 arguments s and return a sequence of intents."
  [on-clipboard-paste & drawables]
  (OnClipboardPaste. on-clipboard-paste drawables))


(defrecord OnClipboardCopy [on-clipboard-copy drawables]
    IOrigin
    (-origin [_]
        [0 0])
  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

    IMakeNode
    (make-node [this childs]
      (OnClipboardCopy. on-clipboard-copy childs))


  IChildren
  (-children [this]
    drawables)

  IClipboardCopy
  (-clipboard-copy [this]
      (when on-clipboard-copy
        (on-clipboard-copy))))

(swap! default-draw-impls
       assoc OnClipboardCopy
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-clipboard-copy
  "Wraps drawables and adds a handler for clipboard copy events.

  on-clipboard-copy should take 0 arguments and return a sequence of intents."
  [on-clipboard-copy & drawables]
  (OnClipboardCopy. on-clipboard-copy drawables))



(defrecord OnClipboardCut [on-clipboard-cut drawables]
    IOrigin
    (-origin [_]
        [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

    IMakeNode
    (make-node [this childs]
      (OnClipboardCut. on-clipboard-cut childs))


  IChildren
  (-children [this]
    drawables)

  IClipboardCut
  (-clipboard-cut [this]
      (when on-clipboard-cut
        (on-clipboard-cut))))

(swap! default-draw-impls
       assoc OnClipboardCut
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-clipboard-cut
  "Wraps drawables and adds a handler for clipboard cut events.

  on-clipboard-copy should take 0 arguments and return a sequence of intents."
  [on-clipboard-cut & drawables]
  (OnClipboardCut. on-clipboard-cut drawables))


;; TODO: replace with component so that drawing doesn't require pushes and pops for each movement
(defn vertical-layout
  "Returns a graphical elem of elems stacked on top of each other"
  [& elems]
  (let [elems (seq elems)
        first-elem (first elems)
        offset-y (+ (height first-elem)
                    (origin-y first-elem))]
    (when elems
      (loop [elems (next elems)
             offset-y (inc offset-y)
             group-elems [first-elem]]
        (if elems
          (let [elem (first elems)
                dy (+ (height elem)
                      (origin-y elem))]
            (recur
             (next elems)
             (+ offset-y dy 1)
             (conj group-elems
                   (translate 0 offset-y
                              elem))))
          group-elems)))))

;; TODO: replace with component so that drawing doesn't require pushes and pops for each movement
(defn horizontal-layout
  "Returns a graphical elem of elems layed out next to eachother."
  [& elems]
  (let [elems (seq elems)
        first-elem (first elems)
        offset-x (+ (width first-elem)
                    (origin-x first-elem))]
    (when elems
      (loop [elems (next elems)
             offset-x (inc offset-x)
             group-elems [first-elem]]
        (if elems
          (let [elem (first elems)
                dx (+ (width elem)
                      (origin-x elem))]
            (recur
             (next elems)
             (+ offset-x dx 1)
             (conj group-elems
                   (translate offset-x 0
                              elem))))
          group-elems)))))

(defn table-layout
  ([table]
   (table-layout table 0 0))
  ([table cell-padding-x cell-padding-y]
   (let [row-heights (mapv (fn [row]
                             (reduce max (map height row)))
                           table)
         col-widths (reduce (fn [col-widths row]
                              (reduce (fn [col-widths [i elem]]
                                        (update col-widths i #(max (or % 0) (width elem))))
                                      col-widths
                                      (map-indexed vector row)))
                            []
                            table)
         full-padding-x (* 2 cell-padding-x)
         full-padding-y (* 2 cell-padding-y)]
     (into []
           (for [[i row] (map-indexed vector table)
                 [j elem] (map-indexed vector row)]
             (translate (+ cell-padding-x
                           (reduce #(+ full-padding-x %1 %2) 0 (subvec col-widths 0 j)))
                        (+ cell-padding-y
                           (reduce #(+ full-padding-y %1 %2) 0 (subvec row-heights 0 i)))
                        elem))))))


(defn center
  "Centers `elem` within a space of `[width, height]`"
  [elem [width height]]
  (let [[ewidth eheight] (bounds elem)]
    (translate (int (- (/ width 2)
                       (/ ewidth 2)))
               (int (- (/ height 2)
                       (/ eheight 2)))
               elem)))

(defn align-row
  ([alignment row]
   (align-row alignment nil row ))
  ([alignment row-height row]
   (if (= alignment :top)
     row
     (let [h (or row-height (height row))]
       (case alignment
         :bottom
         (into []
               (map (fn [elem]
                      (translate 0 (- h (height elem))
                                 elem)))
               row)
         :center
         (into []
               (map (fn [elem]
                      (translate 0 (/ (- h (height elem))
                                      2)
                                 elem)))
               row))))))

(defn align-column
  ([alignment col]
   (align-column alignment nil col))
  ([alignment col-width col]
   (if (= alignment :left)
     col
     (let [w (or col-width (width col))]
       (case alignment
         :right
         (into []
               (map (fn [elem]
                      (translate (- w (width elem)) 0
                                 elem)))
               col)
         :center
         (into []
               (map (fn [elem]
                      (translate (/ (- w (width elem))
                                    2)
                                 0
                                 elem)))
               col))))))

(defn- align-test []
  (align-column :right
                ;; 600
                (apply
                 vertical-layout
                 (for [i (range 5)]
                   (filled-rectangle [1 0 0]
                                     (* 50 (inc i)) (* 50 (inc i)))))))

(comment
  (require '[membrane.skia :as skia])

  (skia/run #'align-test)
  ,
  )

(defn justify-row-content [justification row-width row]
  (let [content-width (transduce (map width)
                                 +
                                 0
                                 row)
        row-count (count row)
        ]
    ;; All of these have to deal with the fact that
    ;; horizontal-layout adds 1px between each element
    ;; which might have been a bad idea
    (case justification
      :space-between
      (if (< row-count 2)
        row
        (let [gap (/ (- row-width content-width)
                     (dec row-count))

              gap (- gap 2)
              gap (max 0 gap)]
          (apply horizontal-layout
                 (eduction (interpose (spacer gap 0))
                           row))))

      :space-around
      (if (empty? row)
        row
        (let [gap (/ (- row-width content-width)
                     row-count
                     2)
              gap (max 0 (- gap (/ (dec row-count) row-count 2)))]
          (apply horizontal-layout
                 (eduction (map (fn [elem]
                                  (padding gap 0
                                           elem)
                                  ))
                           row))))

      :space-evenly
      (if (empty? row)
        row
        (let [gap (/ (- row-width content-width)
                     (inc row-count))]
          (apply horizontal-layout
                 (cons
                  (spacer (dec gap) 0)
                  (eduction
                   (interpose (spacer (max 0 (- gap 2)) 0))
                   row))))))))

(defn justify-column-content [justification col-height col]
  (let [content-height (transduce (map height)
                                 +
                                 0
                                 col)
        col-count (count col)
        ]
    ;; All of these have to deal with the fact that
    ;; vertical-layout adds 1px between each element
    ;; which might have been a bad idea
    (case justification
      :space-between
      (if (< col-count 2)
        col
        (let [gap (/ (- col-height content-height)
                     (dec col-count))

              gap (- gap 2)
              gap (max 0 gap)]
          (apply vertical-layout
                 (eduction (interpose (spacer 0 gap))
                           col))))

      :space-around
      (if (empty? col)
        col
        (let [gap (/ (- col-height content-height)
                     col-count
                     2)
              gap (max 0 (- gap (/ (dec col-count) col-count 2)))]
          (apply vertical-layout
                 (eduction (map (fn [elem]
                                  (padding 0 gap
                                           elem)))
                           col))))

      :space-evenly
      (if (empty? col)
        col
        (let [gap (/ (- col-height content-height)
                     (inc col-count))]
          (apply vertical-layout
                 (cons
                  (spacer 0 (dec gap))
                  (eduction
                   (interpose (spacer 0 (max 0 (- gap 2))))
                   col))))))))

(defn- justify-row-test []
  (padding 10
           (apply
            vertical-layout
            (for [justification [:space-between
                                 :space-around
                                 :space-evenly]]
              (with-color [1 0 0 0.5]
                (with-style ::style-fill
                  [(with-style ::style-stroke
                     (with-color [0 0 0]
                       (rectangle 400 30)))
                   (justify-row-content justification
                                        400
                                        [(rectangle 10 20)
                                         (rectangle 20 10)
                                         (rectangle 15 30)
                                         ]
                                        )]))))))

(defn- justify-column-test []
  (padding 10
           (apply
            horizontal-layout
            (for [justification [:space-between
                                 :space-around
                                 :space-evenly]]
              (with-color [1 0 0 0.5]
                (with-style ::style-fill
                  [(with-style ::style-stroke
                     (with-color [0 0 0]
                       (rectangle 20 400)))
                   (justify-column-content justification
                                           400
                                           (align-column
                                            :right
                                            [(rectangle 20 20)
                                             (rectangle 20 10)
                                             (rectangle 15 30)
                                             ])
                                           )])))))
  )

(comment
  (skia/run #'justify-column-test)
  ,)


(defrecord OnScroll [on-scroll drawables]
    IOrigin
    (-origin [_]
        [0 0])

  IBounds
  (-bounds [this]
    (reduce
     (fn [[max-width max-height] elem]
       (let [[ox oy] (origin elem)
             [w h] (bounds elem)]
         [(max max-width (+ ox w))
          (max max-height (+ oy h))]))
     [0 0]
     drawables))

  IScroll
  (-scroll [this [offset-x offset-y :as offset] mpos]
    (when on-scroll
      (when-let [mpos (within-bounds? this mpos)]
        (on-scroll offset mpos))))

    IMakeNode
    (make-node [this childs]
      (OnScroll. on-scroll childs))


  IChildren
  (-children [this]
      drawables))

(swap! default-draw-impls
       assoc OnScroll
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))


(defn on-scroll
  "Wraps drawables and adds an event handler for scroll events.

  on-scroll should take 1 argument [offset-x offset-y] of the scroll offset and return a sequence of intents."
  [on-scroll & drawables]
  (OnScroll. on-scroll drawables))


(defrecord ScissorView [offset bounds drawable]
    IOrigin
    (-origin [this]
      offset)
    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (ScissorView. offset bounds (first childs)))
    IChildren
    (-children [this]
        [drawable])
    IBounds
    (-bounds [this]
      bounds))

(defn scissor-view
  "Graphical elem to only draw drawable within bounds with an offset.

  All other drawing will be clipped."
  [offset bounds drawable]
  (ScissorView.  offset bounds drawable))

(defrecord ScrollView [bounds offset drawable]
    IBounds
    (-bounds [_]
        bounds)
  IOrigin
  (-origin [this]
      [0 0])

  IMouseEvent
  (-mouse-event [this [mx my :as pos] button mouse-down? mods]
    (when-let [[mx my] (within-bounds? this pos)]
      (mouse-event drawable [(- mx (nth offset 0))
                             (- my (nth offset 1))] button mouse-down? mods)))

  IScroll
  (-scroll [this input-offset [mx my :as pos]]
    (scroll drawable
            input-offset
            [(- mx (nth offset 0))
             (- my (nth offset 1))]))

  IDrop
  (-drop [this paths [mx my :as pos]]
      (drop drawable
            paths
            [(- mx (nth offset 0))
             (- my (nth offset 1))]))

  IMouseMove
  (-mouse-move [this [mx my :as pos]]
    (when-let [[mx my] (within-bounds? this pos)]
      (mouse-move drawable [(- mx (nth offset 0))
                            (- my (nth offset 1))])))

  IMouseMoveGlobal
  (-mouse-move-global [this mouse-offset]
      (let [[mx my] mouse-offset]
        (-default-mouse-move-global this [(- mx (nth offset 0))
                                          (- my (nth offset 1))])))


  IMakeNode
  (make-node [this childs]
    (assert (= (count childs) 1))
    (ScrollView. offset bounds (first childs)))

  IChildren
  (-children [this]
      [drawable]))

(defn scrollview
  "Graphical elem that will draw drawable offset by offset and clip its drawings to bounds. "
  [bounds offset drawable]
  (ScrollView. bounds offset drawable))


(defprotocol IHandleEvent
  :extend-via-metadata true  
  (-can-handle? [this event-type])
  (-handle-event [this event-type event-args]))


(defrecord EventHandler [event-type handler drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
      (child-bounds drawable))

  IMakeNode
  (make-node [this childs]
    (assert (= (count childs) 1))
    (EventHandler. event-type handler (first childs)))

  IChildren
  (-children [this]
      [drawable])

  IBubble
  (-bubble [this events]
      (apply concat
             (for [intent events
                   :let [intent-type (first intent)]]
               (if (-can-handle? this intent-type)
                 (-handle-event this intent-type (rest intent))
                 [intent]))))

  IHandleEvent
    (-can-handle? [this other-event-type]
        (= event-type other-event-type))

  (-handle-event [this event-type event-args]
      (apply handler event-args)))

(swap! default-draw-impls
       assoc EventHandler
       (fn [draw]
         (fn [this]
           (draw (:drawable this)))))


(defmulti on-handler
  (fn [event-type handler body]
    event-type)
  :default :membrane.ui/on-handler-default)

(defmethod on-handler :membrane.ui/on-handler-default
  [event-type handler body]
  (EventHandler. event-type handler body))

(defmacro ^:private cond-let
  "Takes a binding-form and a set of test/expr pairs. Evaluates each test
  one at a time. If a test returns logical true, cond-let evaluates and
  returns expr with binding-form bound to the value of test and doesn't
  evaluate any of the other tests or exprs. To provide a default value
  either provide a literal that evaluates to logical true and is
  binding-compatible with binding-form, or use :else as the test and don't
  refer to any parts of binding-form in the expr. (cond-let binding-form)
  returns nil."
  [binding & clauses]
  (when-let [[test expr & more] clauses]
    (if (= test :else)
      expr
      `(if-let [~binding ~test]
         ~expr
         (cond-let ~binding ~@more)))))

(defrecord OnEvent [handlers body]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [this]
    (child-bounds body))

  IMakeNode
  (make-node [this childs]
    (assert (= (count childs) 1))
    (OnEvent. handlers (first childs)))

  IChildren
  (-children [this]
    [body])

  IMouseMove
  (-mouse-move [this pos]
    (cond-let handler

      (:mouse-move-raw handlers)
      (handler pos)

      (:mouse-move handlers)
      (when-let [pos (within-bounds? this pos)]
        (handler pos))

      :else
      (when-let [local-pos (within-bounds? this pos)]
        (let [intents
              (some #(seq (-mouse-move % local-pos))
                    (reverse (children this)))]
          (-bubble this intents)))))

  IMouseEvent
  (-mouse-event [this pos button mouse-down? mods]
    (assert
     (not (and (contains? handlers :mouse-event)
               (or (contains? handlers :mouse-down)
                   (contains? handlers :mouse-up)))))
    (if-let [on-mouse-event (:mouse-event handlers)]
      (when-let [local-pos (within-bounds? this pos)]
        (on-mouse-event local-pos button mouse-down? mods))
      (if mouse-down?
        (cond-let handler

          (:mouse-down-raw handlers)
          (handler pos)

          (:mouse-down handlers)
          (when-let [local-pos (within-bounds? this pos)]
            (handler local-pos))

          :else
          (when-let [local-pos (within-bounds? this pos)]
            (let [intents
                  (some #(seq (-mouse-event % local-pos button mouse-down? mods))
                        (reverse (children this)))]
              intents)))

        ;; mouse up
        (cond-let handler
          (:mouse-up-raw handlers)
          (handler pos)

          (:mouse-up handlers)
          (when-let [local-pos (within-bounds? this pos)]
            (handler local-pos))

          :else
          (when-let [local-pos (within-bounds? this pos)]
            (let [intents
                  (some #(seq (-mouse-event % local-pos button mouse-down? mods))
                        (reverse (children this)))]
              intents))))))

  IDrop
  (-drop [this paths pos]
    (if-let [on-drop (:drop handlers)]
      (when-let [pos (within-bounds? this pos)]
        (on-drop paths pos))
      (when-let [local-pos (within-bounds? this pos)]
        (let [intents
              (some #(seq (-drop % paths local-pos))
                    (reverse (children this)))]
          (-bubble this intents)))))

  IScroll
  (-scroll [elem offset mpos]
    (if-let [on-scroll (:scroll handlers)]
      (on-scroll offset mpos)
      (when-let [local-pos (within-bounds? elem mpos)]
        (let [intents
              (some #(seq (-scroll % offset local-pos))
                    (reverse (children elem)))]
          (-bubble elem intents)))))

  IHasKeyEvent
  (has-key-event [this]
    (boolean (or (:key-event handlers)
                 (some has-key-event (children this)))))

  IKeyEvent
  (-key-event [this key scancode action mods]
    (if-let [on-key-event (:key-event handlers)]
      (on-key-event key scancode action mods)
      (let [intents (mapcat #(-key-event % key scancode action mods) (children this))]
        (-bubble this intents))))

  IHasKeyPress
  (has-key-press [this]
    (boolean
     (or (:key-press handlers)
         (some has-key-press (children this)))))

  IKeyPress
  (-key-press [this key]
    (if-let [on-key-press (:key-press handlers)]
      (on-key-press key)
      (let [intents (mapcat #(-key-press % key) (children this))]
        (-bubble this intents))))

  IHasMouseMoveGlobal
  (has-mouse-move-global [this]
    (boolean (or (:mouse-move-global handlers)
                 (some has-mouse-move-global (children this)))))

  IMouseMoveGlobal
  (-mouse-move-global [this pos]
    (if-let [on-mouse-move-global (:mouse-move-global handlers)]
      (on-mouse-move-global pos)
      (-default-mouse-move-global this pos)))

  IMouseEnterGlobal
  (-mouse-enter-global [this enter?]
    (if-let [on-mouse-enter-global (:mouse-enter-global handlers)]
      (on-mouse-enter-global enter?)
      (let [intents (mapcat #(-mouse-enter-global % enter?) (children this))]
        (-bubble this intents))))

  IClipboardCopy
  (-clipboard-copy [this]
    (if-let [on-clipboard-copy (:clipboard-copy handlers)]
      (on-clipboard-copy)
      (let [intents (mapcat #(clipboard-copy %) (children this))]
        (-bubble this intents))))

  IClipboardCut
  (-clipboard-cut [this]
    (if-let [on-clipboard-cut (:clipboard-cut handlers)]
      (on-clipboard-cut)
      (let [intents (mapcat #(clipboard-cut %) (children this))]
        (-bubble this intents))))

  IClipboardPaste
  (-clipboard-paste [this s]
    (if-let [on-clipboard-paste (:clipboard-paste handlers)]
      (on-clipboard-paste s)
      (let [intents (mapcat #(clipboard-paste % s) (children this))]
        (-bubble this intents))))

  IBubble
  (-bubble [this events]
    (if-let [on-bubble (:bubble handlers)]
      (on-bubble events)
      (apply concat
             (for [intent events
                   :let [intent-type (first intent)]]
               (if (-can-handle? this intent-type)
                 (-handle-event this intent-type (rest intent))
                 [intent])))))

  IHandleEvent
  (-can-handle? [this other-event-type]
    (contains? handlers other-event-type))

  (-handle-event [this event-type event-args]
    (let [handler (get handlers event-type)]
      (assert handler)
      (apply handler event-args))))

(defn ^:private multi-on
  [handlers body]
  (OnEvent. handlers body))

;; Same as OnEvent, but other events "pass through"
(defrecord OnEventRaw [handlers body]
  IOrigin
  (-origin [_]
    [0 0])

  IBounds
  (-bounds [this]
    (child-bounds body))

  IMakeNode
  (make-node [this childs]
    (assert (= (count childs) 1))
    (OnEvent. handlers (first childs)))

  IChildren
  (-children [this]
    [body])

  IMouseMove
  (-mouse-move [this pos]
    (if-let [on-mouse-move (:mouse-move handlers)]
      (on-mouse-move pos)
      (-mouse-move body pos)))

  IMouseEvent
  (-mouse-event [this pos button mouse-down? mods]
    (assert
     (not (and (contains? handlers :mouse-event)
               (or (contains? handlers :mouse-down)
                   (contains? handlers :mouse-up)))))
    (if-let [on-mouse-event (:mouse-event handlers)]
      (on-mouse-event pos button mouse-down? mods)
      (if mouse-down?
        (if-let [on-mouse-down (:mouse-down handlers)]
          (on-mouse-down pos)
          (-mouse-event body pos button mouse-down? mods))
        (if-let [on-mouse-up (:mouse-up handlers)]
          (on-mouse-up pos)
          (-mouse-event body pos button mouse-down? mods)))))

  IDrop
  (-drop [this paths pos]
    (if-let [on-drop (:drop handlers)]
      (on-drop paths pos)
      (-drop body paths pos)))

  IScroll
  (-scroll [elem offset mpos]
    (if-let [on-scroll (:scroll handlers)]
      (on-scroll offset mpos)
      (-scroll body offset mpos)))

  IHasKeyEvent
  (has-key-event [this]
    (boolean (or (:key-event handlers)
                 (some has-key-event (children this)))))

  IKeyEvent
  (-key-event [this key scancode action mods]
    (if-let [on-key-event (:key-event handlers)]
      (on-key-event key scancode action mods)
      (-key-event body key scancode action mods)))

  IHasKeyPress
  (has-key-press [this]
    (boolean
     (or (:key-press handlers)
         (some has-key-press (children this)))))

  IKeyPress
  (-key-press [this key]
    (if-let [on-key-press (:key-press handlers)]
      (on-key-press key)
      (-key-press body key)))

  IHasMouseMoveGlobal
  (has-mouse-move-global [this]
    (boolean (or (:mouse-move-global handlers)
                 (some has-mouse-move-global (children this)))))

  IMouseMoveGlobal
  (-mouse-move-global [this pos]
    (if-let [on-mouse-move-global (:mouse-move-global handlers)]
      (on-mouse-move-global pos)
      (-mouse-move-global body pos)))

  IMouseEnterGlobal
  (-mouse-enter-global [this enter?]
    (if-let [on-mouse-enter-global (:mouse-enter-global handlers)]
      (on-mouse-enter-global enter?)
      (-mouse-enter-global body enter?)))

  ;; Clipboard events don't have default implementations.
  ;; They probably should to match the other events, but
  ;; I don't want to make a big change this moment
  ;; without thinking about it a bit more.
  ;; For now, just calling wrappers.
  IClipboardCopy
  (-clipboard-copy [this]
    (if-let [on-clipboard-copy (:clipboard-copy handlers)]
      (on-clipboard-copy)
      (clipboard-copy body)))

  IClipboardCut
  (-clipboard-cut [this]
    (if-let [on-clipboard-cut (:clipboard-cut handlers)]
      (on-clipboard-cut)
      (clipboard-cut body)))

  IClipboardPaste
  (-clipboard-paste [this s]
    (if-let [on-clipboard-paste (:clipboard-paste handlers)]
      (on-clipboard-paste s)
      (clipboard-paste body s)))

  IBubble
  (-bubble [this events]
    (if-let [on-bubble (:bubble handlers)]
      (on-bubble events)
      (-bubble body events)))

  IHandleEvent
  (-can-handle? [this other-event-type]
    (contains? handlers other-event-type))

  (-handle-event [this event-type event-args]
    (let [handler (get handlers event-type)]
      (assert handler)
      (apply handler event-args))))

(defn raw-on [handlers body]
  (OnEventRaw. handlers body))

(defn on
  "Wraps an elem with event handlers.

  events are pairs of events and event handlers and the last argument should be an elem.

  example:

  Adds do nothing event handlers for mouse-down and mouse-up events on a label that says \"Hello!\"
  (on :mouse-down (fn [[mx my]] nil)
      :mouse-up (fn [[mx my]] nil)
     (label \"Hello!\"))
  "
  [& events]
  (loop [evs (seq (reverse (partition 2 events)))
         body (last events)]
    (if evs
      (let [[event-type handler] (first evs)]
        (recur (next evs)
               (case event-type
                 :mouse-down
                 (on-mouse-down handler
                                       body)
                 :mouse-event
                 (on-mouse-event handler
                                 body)

                 :drop
                 (on-drop handler
                          body)

                 :scroll
                 (on-scroll handler
                            body)

                 :key-event
                 (on-key-event handler
                               body )
                 :key-press
                 (on-key-press handler
                                     body)
                 :mouse-up
                 (on-mouse-up handler
                                     body)
                 :mouse-move
                 (on-mouse-move handler
                                body)

                 :mouse-move-global
                 (on-mouse-move-global handler
                                       body)

                 :mouse-enter-global
                 (on-mouse-enter-global handler
                                        body)

                 :clipboard-copy
                 (on-clipboard-copy handler
                                           body)

                 :clipboard-cut
                 (on-clipboard-cut handler
                                          body)

                 :clipboard-paste
                 (on-clipboard-paste handler
                                            body)
                 
                 
                 ;; else
                 (on-handler event-type handler body))))
      body)))


(defn wrap-on
  "Wraps an elem with event handlers.

  events are pairs of events and event handlers and the last argument should be an elem.
  The event handlers should accept an extra first argument to the event which is the original event handler.

  example:

  Wraps a button with a mouse-down handler that only returns an intent when the x coordinate is even.
  (on :mouse-down (fn [handler [mx my]]
                     (when (even? mx)
                       (handler [mx my])))
     (button \"Hello!\"
            (fn []
               [[:hello!]])))
  "
  [& events]
  (loop [evs (seq (reverse (partition 2 events)))
         body (last events)]
    (if evs
      (let [[event-type handler] (first evs)]
        (recur (next evs)
               (case event-type
                 :mouse-down
                 (on-mouse-event
                  (fn [mpos button mouse-down? mods]
                    (if mouse-down?
                      (handler (fn [pos]
                                 (mouse-event body pos button mouse-down? mods))
                               mpos)
                      (mouse-event body mpos button mouse-down? mods)))
                  body)

                 :mouse-event
                 (on-mouse-event
                  (fn [mpos button mouse-down? mods]
                    (handler (fn [mpos button mouse-down? mods]
                               (mouse-event body mpos button mouse-down? mods))
                             mpos button mouse-down? mods))
                  body)

                 :key-event
                 (on-key-event
                  (fn [key scancode action mods]
                    (handler (fn [key scancode action mods]
                               (key-event body key scancode action mods))
                             key scancode action mods))
                  body)

                 :key-press
                 (on-key-press
                  (fn [key]
                    (handler (fn [key]
                               (key-press body key))
                             key))
                  body)

                 :mouse-up
                 (on-mouse-event
                  (fn [mpos button mouse-down? mods]
                    (if (not mouse-down?)
                      (handler (fn [pos]
                                 (mouse-event body pos button mouse-down? mods))
                               mpos)
                      (mouse-event body mpos button mouse-down? mods)))
                  body)

                 :mouse-move
                 (on-mouse-move
                  (fn [pos]
                    (handler (fn [pos] (mouse-move body pos))
                             pos))
                  body)

                 :mouse-move-global
                 (on-mouse-move-global
                  (fn [pos]
                    (handler (fn [pos] (mouse-move-global body pos))
                             pos))
                  body)

                 :scroll
                 (on-scroll
                  (fn [offset pos]
                    (handler (fn [offset pos]
                               (scroll body offset pos))
                             offset
                             pos))
                  body)

                 ;; ;; else
                 ;; (EventHandler. event-type handler body)
                 )))
      body)))

(defrecord NoEvents [drawable]
    IBounds
    (-bounds [this]
      (child-bounds drawable))

  IOrigin
  (-origin [_]
      [0 0])

  IMakeNode
  (make-node [this childs]
    (assert (= (count childs) 1))
    (NoEvents. (first childs)))

  IChildren
    (-children [this]
        [drawable])

    IBubble
    (-bubble [this events]
        nil)

    IHandleEvent
    (-can-handle? [this other-event-type]
        false)

    (-handle-event [this event-type event-args]
        nil)

    IClipboardCopy
    (-clipboard-copy [_] nil)
    IClipboardCut
    (-clipboard-cut [_] nil)
    IClipboardPaste
    (-clipboard-paste [_ s] nil)
    IKeyPress
    (-key-press [this key] nil)
    IKeyType
    (-key-type [this key] nil)
    IMouseMove
    (-mouse-move [this pos] nil)
    IMouseMoveGlobal
    (-mouse-move-global [this pos] nil)
    IMouseWheel
    (-mouse-wheel [this pos] nil)
    IScroll
    (-scroll [this pos mpos] nil))

(swap! default-draw-impls
       assoc NoEvents
       (fn [draw]
         (fn [this]
           (draw (:drawable this)))))


#_(defn no-events [body]
  (NoEvents. body))

(defn no-events [body]
  (let [do-nothing (constantly nil)]
    (on :mouse-event do-nothing
        :mouse-up do-nothing
        :mouse-down do-nothing

        :drop do-nothing
        :scroll do-nothing
        :key-event do-nothing
        :key-press do-nothing

        :mouse-move do-nothing
        :mouse-move-global do-nothing
        :clipboard-copy do-nothing
        :clipboard-cut do-nothing
        :clipboard-paste do-nothing

        body)))

(defrecord NoKeyEvent [drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
      (child-bounds drawable))

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (NoKeyEvent. (first childs)))


    IChildren
    (-children [this]
        [drawable])

    IKeyEvent
    (-key-event [this key scancode action mods]
      nil)

    IHasKeyEvent
    (has-key-event [this]
        false))

(swap! default-draw-impls
       assoc NoKeyEvent
       (fn [draw]
         (fn [this]
           (draw (:drawable this)))))


(defmacro maybe-key-event
  "Only respond to key events when `test` is true."
  [test body]
  `(if ~test
     ~body
     (NoKeyEvent. ~body)))

(defrecord NoKeyPress [drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
      (child-bounds drawable))

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (NoKeyPress. (first childs)))

    IChildren
    (-children [this]
        [drawable])

    IKeyPress
    (-key-press [this info]
      nil)

    IHasKeyPress
    (has-key-press [this]
        false))

(swap! default-draw-impls
       assoc NoKeyPress
       (fn [draw]
         (fn [this]
           (draw (:drawable this)))))


(defmacro maybe-key-press
  "Only respond to key press events when `test` is true."
  [test body]
  `(if ~test
     ~body
     (NoKeyPress. ~body)))

(defrecord TryDraw [drawable error-draw]
    IOrigin
    (-origin [_]
        (try
          (origin drawable)
          (catch #?(:clj Throwable
                    :cljs js/Object) e
            (bounds (label "error")))))

    IBounds
    (-bounds [this]
        (try
          (child-bounds drawable)
          (catch #?(:clj Exception
                    :cljs js/Object) e
            (println e)
            (bounds (label "error"))))
        )

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (TryDraw. (first childs) error-draw))

  IChildren
  (-children [this]
      [drawable]))

(swap! default-draw-impls
       assoc TryDraw
       (fn [draw]
         (fn [this]
           (try
             (draw (:drawable this))
             (catch #?(:clj Throwable
                       :cljs js/Object) e
               ((:error-draw this) draw e))))))


(defn try-draw
  "Tries to draw body. If an exception occurs, calls error-draw with `draw` and the exception

  Example:
  (ui/try-draw error-body
    (fn [draw e]
      (draw (ui/label e))))
  "
  [body error-draw]
  (TryDraw. body error-draw))

(defrecord Cached [drawable]
    IOrigin
    (-origin [_]
        (origin drawable))

    IBounds
    (-bounds [_]
        (bounds drawable))

  IChildren
  (-children [this]
      [drawable]))


(def index-for-position* (atom nil))

(defn index-for-position [font text x y]
  (let [f @index-for-position*]
    (assert f "index-for-position should be replaced by implementation")
    (f font text x y)))

(defn copy-to-clipboard [s])

;; (defrecord WhenDraw [when-draw! drawable]
;;   IOrigin
;;   (-origin [this]
;;     [0 0])

;;   IMakeNode
;;   (make-node [this childs]
;;     (assert (= (count childs) 1))
;;     (WhenDraw. when-draw! (first childs)))

;;   IChildren
;;   (-children [this]
;;     [drawable])

;;   IBounds
;;   (-bounds [this]
;;     (child-bounds drawable)))

;; (swap! default-draw-impls
;;        assoc WhenDraw (fn [draw]
;;                         (fn [this]
;;                           ((:when-draw! this) draw (:drawable this)))))

;; (defn ^:private when-draw!
;;   "Warning! You probably don't want to do this!

;;   This function may be removed.

;;   `when-draw!` is a callback that receives the draw function
;;   and the elem to be drawn.

;;   Called when `elem` is being drawn.

;;   The `elem` is not drawn unless you
;;   draw it. The return value is ignored.

;;   Example:

;;   (backend/run
;;     (fn []
;;   (when-draw!
;;     (fn [draw elem]


;;   "
;;   [when-draw! elem]
;;   (WhenDraw. when-draw! elem))

#?(:clj
   (do
     (defn add-default-draw-impls! [IDraw draw]
       (doseq [[cls impl] @default-draw-impls]
         (extend cls
           IDraw
           {:draw (impl draw)})))
     (defmacro add-default-draw-impls-cljs! [IDraw draw]
       `(do
          ~@(doall
             (for [[k impl] @default-draw-impls
                   :let [cls (condp = k
                               Object 'default
                               nil nil
                               clojure.lang.PersistentVector 'cljs.core/PersistentVector
                               ;; else
                               (symbol (.getName k)))
                         k (condp = k
                             Object :default
                             nil nil
                             clojure.lang.PersistentVector 'cljs.core/PersistentVector
                             ;; else
                             (symbol (.getName k)))
                         ]]
               `(do
                  (let [draw# ((get @membrane.ui/default-draw-impls ~k) ~draw)]
                    (extend-type ~cls
                      ~IDraw
                      (~draw [this#]
                       (draw# this#))))))))
       )))


(defn ^:private stretch-elems* [size get-stretch measure-size elems]
  (let [fixed-size-total (transduce
                          (comp (remove get-stretch)
                                (map measure-size))
                          +
                          0
                          elems)
        stretch-size (- size fixed-size-total)
        stretch-total (transduce
                       (keep get-stretch)
                       +
                       0
                       elems)
        ;; stretched elems
        elems (into []
                    (map (fn [elem]
                           (if-let [stretch (get-stretch elem)]
                             (let [width (*
                                          stretch-size
                                          (/ stretch stretch-total))]
                               (assoc elem
                                      :flex-layout.stretch/width width))
                             elem)))
                    elems)]
    elems))

(defn flex-layout
  "Arranges `elems` according to `layout`.
  `elems` should be a sequence of views.
  `layout` is described below.

  `layout` is a map with the following keys:
  `:direction` either `:row` or `:column`
  `:gap` number of pixels between elements
  `:justify-content` one of `:start`, `:center`, `:end`, `:space-between`, `:space-around`, `:space-evenly`
  `:align` one of `:start`, `:center`, `:end`
  `:width` Specifies a static width.
  `:height` Specifies a static height."

  [elems layout]
  (let [direction (get layout :direction :row)

        ;; not used
        ;;wrap (get layout :flex/wrap :flex.wrap/nowrap)
        {:keys [get-size
                get-cross-size
                measure-size
                measure-cross-size
                make-spacer
                main-layout
                get-stretch
                align
                ->alignment]}
        ;;[ui-size get-size ui-cross-size get-cross-size get-gap make-spacer main-layout align ->alignment]
        (if (= direction :row)
          {:get-size :width
           :get-cross-size :height
           :measure-size width
           :measure-cross-size height
           :make-spacer #(spacer % 0)
           :main-layout horizontal-layout
           :get-stretch :flex.grow/width
           :align align-row
           :->alignment {:start :top
                         :end :bottom
                         :center :center}}
          ;; :direction :column
          {:get-size :height
           :get-cross-size :width
           :measure-size height
           :measure-cross-size width
           :make-spacer #(spacer 0 %)
           :main-layout vertical-layout
           :get-stretch :flex.grow/height
           :align align-column
           :->alignment {:start :left
                         :end :right
                         :center :center}})

        size (get-size layout)
        fixed-size? (some? size)]
    (if fixed-size?
      (let [ ;; only justify for fixed sizes
            gap (:gap layout)
            justification (get layout :justify-content
                               :start)
            elems (if gap
                    (do
                      (assert (not (#{:space-around
                                      :space-between
                                      :space-evenly} justification))
                              (str :gap " doesn't make sense with " justification))
                      (into []
                            (interpose (make-spacer gap))
                            elems))
                    elems)

            stretchy? (some get-stretch elems)
            elems (if stretchy?
                    (stretch-elems* size get-stretch measure-size elems)
                    elems)

            elems (if-let [alignment (:align layout)]
                    (align (->alignment alignment)
                           (get-cross-size layout)
                           elems)
                    elems)
            elems (case justification
                    :start
                    (apply main-layout elems)

                    :end
                    (let [elems (apply main-layout elems)
                          offset (- size
                                    (measure-size elems))
                          [x y] (if (= direction :row)
                                  [offset 0]
                                  [0 offset])]
                      (translate x y
                                 elems))

                    :center
                    (let [elems (apply main-layout elems)
                          offset (/ (- size
                                       (measure-size elems))
                                    2)
                          [x y] (if (= direction :row)
                                  [offset 0]
                                  [0 offset])]
                      (translate x y
                                 elems))

                    (:space-between
                     :space-around
                     :space-evenly)
                    ((if (= direction :row)
                       justify-row-content
                       justify-column-content)
                     justification
                     size
                     elems))
            [natural-width natural-height] (bounds elems)
            elems (fixed-bounds
                   [(or (:width layout)
                        natural-width)
                    (or (:height layout)
                        natural-height)]
                   elems)]
        elems)

      ;; not fixed size
      (let [ ;; justify doesn't make sense for non-fixed-width
            ;; but gaps do.
            gap (:gap layout)
            elems (if gap
                    (interpose (make-spacer gap)
                               elems)
                    elems)
            elems (apply main-layout elems)
            elems (if-let [alignment (:align layout)]
                    (align (->alignment alignment)
                           (get-cross-size layout)
                           elems)
                    elems)

            [natural-width natural-height] (bounds elems)
            elems (fixed-bounds
                   [(or (:width layout)
                        natural-width)
                    (or (:height layout)
                        natural-height)]
                   elems)]
        elems))))

