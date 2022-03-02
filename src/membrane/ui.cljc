(ns membrane.ui
  #?(:cljs (:require-macros [membrane.ui :refer [make-event-handler]]))
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


(defprotocol IMouseMove (-mouse-move [elem pos]))
(defprotocol IMouseMoveGlobal (-mouse-move-global [elem pos]))
(defprotocol IMouseEvent (-mouse-event [elem pos button mouse-down? mods]))
(defprotocol IDrop (-drop [elem paths pos]))
(defprotocol IScroll (-scroll [elem delta mpos]))
(defprotocol IMouseWheel (-mouse-wheel [elem delta]))
(defprotocol IKeyPress (-key-press [elem key]))
(defprotocol IKeyType (-key-type [elem key]))
(defprotocol IClipboardPaste (-clipboard-paste [elem contents]))
(defprotocol IClipboardCopy (-clipboard-copy [elem]))
(defprotocol IClipboardCut (-clipboard-cut [elem]))

(defprotocol IMakeNode
  (make-node [node childs]))

(extend-protocol IMakeNode
  #?(:cljs cljs.core/PersistentVector
     :clj clojure.lang.PersistentVector)
  (make-node [node childs]
    (vec childs)))

(declare children)

(defprotocol IOrigin
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
  "Specifies the top left corner of a component's bounds. The origin is vector
  of 2 numbers [x, y]."
  [elem]
  (-origin elem))

(defn origin-x
  "Convience function for returning the x coordinate of elem's origin."
  [elem]
  (first (origin elem)))

(defn origin-y
  "Convience function for returning the y coordinate of elem's origin."
  [elem]
  (second (origin elem)))

(defprotocol IKeyEvent
  (-key-event [this key scancode action mods]))

(defprotocol IHasKeyEvent
  (has-key-event [this]))
(defprotocol IHasKeyPress
  (has-key-press [this]))
(defprotocol IHasMouseMoveGlobal
  (has-mouse-move-global [this]))

(defprotocol IBubble
  "Allows an element add, remove, modify effects emitted from its children."
  (-bubble [_ effects]
    "Called when an effect is being emitted by a child element. The parent
    element can either return the same effects or allow them to continue to
    bubble."))

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
  IScroll
  (-scroll [elem offset local-pos])
  IDrop
  (-drop [elem paths local-pos]
    nil)
  IBubble
  (-bubble [elem effects]
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

  IBubble
  (-bubble [this intents]
    intents)

  IMouseEvent
  (-mouse-event [elem local-pos button mouse-down? mods]
    (let [intents
          ;; use seq to make sure we don't stop for empty sequences
          (some #(when-let [local-pos (within-bounds? % local-pos)]
                   (seq (-mouse-event % local-pos button mouse-down? mods)))
                (reverse (children elem)))]
      (-bubble elem intents)))

  IMouseMove
  (-mouse-move [elem local-pos]
    (let [intents
          ;; use seq to make sure we don't stop for empty sequences
          (some #(when-let [local-pos (within-bounds? % local-pos)]
                   (seq (-mouse-move % local-pos)))
                (reverse (children elem)))]
      (-bubble elem intents)))

  IScroll
  (-scroll [elem offset local-pos]
    (let [intents
          ;; use seq to make sure we don't stop for empty sequences
          (some #(when-let [local-pos (within-bounds? % local-pos)]
                   (seq (-scroll % offset local-pos)))
                (reverse (children elem)))]
      (-bubble elem intents)))

  IDrop
  (-drop [elem paths local-pos]
    (let [intents
          ;; use seq to make sure we don't stop for empty sequences
          (some #(when-let [local-pos (within-bounds? % local-pos)]
                   (seq (-drop % paths local-pos)))
                (reverse (children elem)))]
      (-bubble elem intents)))

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
  (-bounds [elem]
    "Returns a 2 element vector with the [width, height] of an element's bounds with respect to its origin"))

(def
  ^{:arglists '([elem])
    :doc
    "Returns a 2 element vector with the [width, height] of an element's bounds with respect to its origin"}
  bounds (memoize #(-bounds %)))

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
  "Returns the effects of a mouse move event on elem. Will only call -mouse-move
  on mouse events within an element's bounds."
  ([elem pos]
   (when-let [local-pos (within-bounds? elem pos)]
     (-mouse-move elem local-pos))))

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
  "Returns the effects of a mouse move event on elem. Will -mouse-move-global for all elements and their children."
  ([elem global-pos]
   (-mouse-move-global elem global-pos)))

(defn mouse-event [elem pos button mouse-down? mods]
  (when-let [local-pos (within-bounds? elem pos)]
    (-mouse-event elem local-pos button mouse-down? mods)))

(defn mouse-down
  "Returns the effects of a mouse down event on elem. Will only call
  -mouse-event or -mouse-down if the position is in the element's bounds."
  [elem [mx my :as pos]]
  (mouse-event elem pos 0 true 0))

(defn mouse-up
  "Returns the effects of a mouse up event on elem. Will only call -mouse-event
  or -mouse-down if the position is in the element's bounds."
  [elem [mx my :as pos]]
  (mouse-event elem pos 0 false 0))

(defn drop [elem paths pos]
  (when-let [local-pos (within-bounds? elem pos)]
    (-drop elem paths local-pos)))

(defn scroll [elem offset pos]
  (when-let [local-pos (within-bounds? elem pos)]
    (-scroll elem offset local-pos)))


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
    :doc "Returns the effects of a key press event on elem."}
  key-press (make-event-handler "IKeyPress" IKeyPress -key-press))
(def
  ^{:arglists '([elem key scancode action mods]),
    :doc "Returns the effects of a key event on elem."}
  key-event (make-event-handler "IKeyEvent" IKeyEvent -key-event))
(def
  ^{:arglists '([elem]),
    :doc "Returns the effects of a clipboard cut event on elem."}
  clipboard-cut (make-event-handler "IClipboardCut" IClipboardCut -clipboard-cut))
(def
  ^{:arglists '([elem]),
    :doc "Returns the effects of a clipboard copy event on elem."}
  clipboard-copy (make-event-handler "IClipboardCopy" IClipboardCopy -clipboard-copy))
(def
  ^{:arglists '([elem s]),
    :doc "Returns the effects of a clipboard paste event on elem."}
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
  "Like [[label]] except that it calls `pr-str` on its argument and uses that
  result as the contents of a new [[label]] element. Lines longer than
  `max-length` are cut off at `max-length`."
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
    size))




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

  This is useful for drawing images included in a jar. Simply put your image in
  your resources folder, typically resources. Draw the images in the jar
  with `(ui/image (clojure.java.io/resource \"filename.png\"))`

  The image can be drawn at a different size by supplying a size. Supply a `nil`
  size to use the the original image size.

  The image can be aspect scaled by supplying a size with one of the dimensions
  as `nil`.

  For example, to draw an image with width 30 and aspect scaling, `(image
  \"path.png\" [30 nil])`

  Opacity is a float between 0 and 1.

  Allowable image formats may vary by platform, but will typically include png
  and jpeg.
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
      (let [[w h] (bounds drawable)
            [ox oy] (origin drawable)]
        [(+ w ox)
         (+ h oy)])))

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
      (bounds drawable)))

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
        [left top])

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (Padding. left right bottom top (first childs)))

  IChildren
  (-children [this]
      [drawable])

  IBounds
  (-bounds [this]
      (let [[w h] (bounds drawable)]
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

    IMakeNode
    (make-node [this childs]
      (Scale. scalars childs))

    IChildren
    (-children [this]
        drawables)
    )
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
  (let [{:keys [drawable padding-x padding-y]} this
        [width height] (bounds drawable)]
    [(let [gray  0.65]
       (with-color [gray gray gray]
         (with-style ::style-stroke
           (rectangle (+ width (* 2 padding-x))
                      (+ height (* 2 padding-y))))))
     (translate padding-x
                padding-y
                drawable)]))

(defrecord Bordered [padding-x padding-y drawable]
    IOrigin
    (-origin [this]
        (origin (bordered-draw this)))


    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (Bordered. padding-x padding-y (first childs)))


  IChildren
  (-children [this]
      (bordered-draw this))

  IBounds
  (-bounds [this]
      (let [[width height] (bounds drawable)]
        [(+ width (* 2 padding-x))
         (+ height (* 2 padding-y))])))

(swap! default-draw-impls
       assoc Bordered
       (fn [draw]
         (fn [this]
           (draw (bordered-draw this)))))

(defn bordered
  "Graphical elem that will draw drawable with a gray border."
  [padding drawable]
  (if (vector? padding)
    (let [[px py] padding]
      (Bordered. px py drawable))
    (Bordered. padding padding drawable)))

(defn fill-bordered-draw [this]
  (let [{:keys [color drawable padding-x padding-y]} this
        [width height] (bounds drawable)]
    [
     (filled-rectangle
      color
      (+ width (* 2 padding-x))
      (+ height (* 2 padding-y)))
     (translate padding-x
                padding-y
                drawable)]))

(defrecord FillBordered [color padding-x padding-y drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (FillBordered. color padding-x padding-y (first childs)))


  IChildren
  (-children [this]
      [drawable])

  IBounds
  (-bounds [this]
      (let [[width height] (bounds drawable)]
        [(+ width (* 2 padding-x))
         (+ height (* 2 padding-y))])))

(swap! default-draw-impls
       assoc FillBordered
       (fn [draw]
         (fn [this]
           (draw (fill-bordered-draw this)))))

(defn fill-bordered
  "Graphical elem that will draw elem with filled border."
  [color padding drawable]
  (if (vector? padding)
    (let [[px py] padding]
      (FillBordered. color px py drawable))
    (FillBordered. color padding padding drawable)))

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
    (when (and mouse-down? on-click)
      (on-click))))

(swap! default-draw-impls
       assoc Button
       (fn [draw]
         (fn [this]
           (draw (button-draw this)))))

(defn button
  "Graphical elem that draws a button. Optional on-click function may be
  provided that is called with no arguments when button has a mouse-down event."
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
    (when (and mouse-down? on-click)
      (on-click))))

(swap! default-draw-impls
       assoc OnClick
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-click
  "Wrap an element with a mouse down event handler, on-click. 

  on-click must accept 0 arguments and should return a sequence of effects."
  [on-click & drawables]
  (OnClick. on-click drawables))



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
        (on-mouse-down pos))
      (let [intents
            ;; use seq to make sure we don't stop for empty sequences
            (some #(when-let [local-pos (within-bounds? % pos)]
                     (seq (-mouse-event % local-pos button mouse-down? mods)))
                  (reverse (children this)))]
        (-bubble this intents)))))

(swap! default-draw-impls
       assoc OnMouseDown
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-down
  "Wraps drawables and adds an event handler for mouse-down events.

  on-mouse-down should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of effects."
  [on-mouse-down & drawables]
  (OnMouseDown. on-mouse-down drawables))

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
      (let [intents
            ;; use seq to make sure we don't stop for empty sequences
            (some #(when-let [local-pos (within-bounds? % pos)]
                     (seq (-mouse-event % local-pos button mouse-down? mods)))
                  (reverse (children this)))]
        (-bubble this intents))
      (when on-mouse-up
        (on-mouse-up pos)))))

(swap! default-draw-impls
       assoc OnMouseUp
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-up
  "Wraps drawables and adds an event handler for mouse-up events.

  on-mouse-up should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of effects."
  [on-mouse-up & drawables]
  (OnMouseUp. on-mouse-up drawables))

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
        (on-mouse-move pos))))

(swap! default-draw-impls
       assoc OnMouseMove
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-move
  "Wraps drawables and adds an event handler for mouse-move events.

  on-mouse-move down should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of effects."
  [on-mouse-move & drawables]
  (OnMouseMove. on-mouse-move drawables))

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

  on-mouse-move-global down should take 1 argument [mx my] of the mouse position in global coordinates and return a sequence of effects."
  [on-mouse-move-global & drawables]
  (OnMouseMoveGlobal. on-mouse-move-global drawables))

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
          (on-mouse-event pos button mouse-down? mods))))

(swap! default-draw-impls
       assoc OnMouseEvent
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-mouse-event
  "Wraps drawables and adds an event handler for mouse events.

  on-mouse-event should take 4 arguments [pos button mouse-down? mods] and return a sequence of effects."
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
          (on-drop paths pos))))

(swap! default-draw-impls
       assoc OnDrop
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-drop
  "Wraps drawables and adds an event handler for drop events.

  on-drop should take 2 arguments [paths pos] and return a sequence of effects."
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

  on-key-press should take 1 argument key and return a sequence of effects."
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

  on-key-event should take 4 arguments key, scancode, action, mods and return a sequence of effects."
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
   (-bubble [this effects]
       (on-bubble effects)))

(swap! default-draw-impls
       assoc OnBubble
       (fn [draw]
         (fn [this]
           (doseq [drawable (:drawables this)]
             (draw drawable)))))

(defn on-bubble
  "Wraps drawables and adds a handler for bubbling

  on-bubble should take seq of effects"
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

  on-clipboard-paste should take 1 arguments s and return a sequence of effects."
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

  on-clipboard-copy should take 0 arguments and return a sequence of effects."
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

  on-clipboard-copy should take 0 arguments and return a sequence of effects."
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


(defn center [elem [width height]]
  (let [[ewidth eheight] (bounds elem)]
    (translate (int (- (/ width 2)
                       (/ ewidth 2)))
               (int (- (/ height 2)
                       (/ eheight 2)))
               elem)))


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
      (on-scroll offset mpos)))

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

  on-scroll should take 1 argument [offset-x offset-y] of the scroll offset and
  return a sequence of effects."
  [on-scroll & drawables]
  (OnScroll. on-scroll drawables))


(defrecord ScissorView [offset bounds drawable]
    IOrigin
    (-origin [this]
        [0 0])
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
      (mouse-event drawable [(- mx (nth offset 0))
                             (- my (nth offset 1))] button mouse-down? mods))

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
      (mouse-move drawable [(- mx (nth offset 0))
                            (- my (nth offset 1))]))

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
  (-can-handle? [this event-type])
  (-handle-event [this event-type event-args]))


(defrecord EventHandler [event-type handler drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (mapv +
              (origin drawable)
              (bounds drawable)))

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

(defn on
  "Wraps an elem with event handlers.

  events are pairs of events and event handlers and the last argument should be an elem.

  example:

  Adds no-op event handlers for mouse-down and mouse-up events on a label that says \"Hello!\"
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

  `events` are pairs of events and event handlers and the last argument should be
  an elem.
  
  The event handlers should accept an extra first argument to the event which is
  the original event handler.

  example:

  Wraps a button with a mouse-down handler that only returns an effect when the
  x coordinate is even.
  
  ```clojure
  (on :mouse-down (fn [handler [mx my]]
                     (when (even? mx)
                       (handler [mx my])))
     (button \"Hello!\"
            (fn []
               [[:hello!]])))
  ```
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
        (bounds drawable))

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
        (bounds drawable))

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (NoKeyEvent. (first childs)))


    IChildren
    (-children [this]
        [drawable])

    IHasKeyEvent
    (has-key-event [this]
        false))

(swap! default-draw-impls
       assoc NoKeyEvent
       (fn [draw]
         (fn [this]
           (draw (:drawable this)))))


(defmacro maybe-key-event [test body]
  `(if ~test
     ~body
     (NoKeyEvent. ~body)))

(defrecord NoKeyPress [drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (bounds drawable))

    IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (NoKeyPress. (first childs)))

    IChildren
    (-children [this]
        [drawable])

    IHasKeyPress
    (has-key-press [this]
        false))

(swap! default-draw-impls
       assoc NoKeyPress
       (fn [draw]
         (fn [this]
           (draw (:drawable this)))))


(defmacro maybe-key-press [test body]
  `(if ~test
     ~body
     (NoKeyPress. ~body)))

(defrecord TryDraw [drawable error-draw]
    IOrigin
    (-origin [_]
        (try
          (origin drawable)
          (catch #?(:clj Exception
                    :cljs js/Object) e
            (bounds (label "error")))))

    IBounds
    (-bounds [this]
        (try
          (bounds drawable)
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
             (catch #?(:clj Exception
                       :cljs js/Object) e
               ((:error-draw this) draw e))))))


(defn try-draw [body error-draw]
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


