(ns membrane.ui
  #?@(:cljs
      [(:require-macros [membrane.ui :refer [defcomponent]])])
  #?@(:clj
      [
       
       ])
  )

#?
(:clj
 (defmacro defcomponent [name [& fields] & opts+specs]
   `(defrecord ~name [~@fields]
      IComponent
      ;; (~'cid [this#]
      ;;   cid#)
      ~@opts+specs)))


(defrecord Font [name size weight])


(def default-font (Font. #? (:clj (if (.exists (clojure.java.io/file "/System/Library/Fonts/HelveticaNeueDeskInterface.ttc"))
                                    "/System/Library/Fonts/HelveticaNeueDeskInterface.ttc"
                                    "/usr/share/fonts/truetype/ubuntu/Ubuntu-R.ttf")
                             :cljs "Ubuntu"
                             )
                            14
                            #?(:clj nil
                               :cljs nil)))

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
         (:weight default-font)))


(defprotocol IMouseMove (-mouse-move [this info]))
(defprotocol IMouseDown (-mouse-down [this info]))
(defprotocol IMouseMoveGlobal (-mouse-move-global [this info]))
(defprotocol IMouseEvent (-mouse-event [this pos button mouse-down? mods]))
(defprotocol IScroll (-scroll [this info]))
(defprotocol IMouseUp (-mouse-up [this info]))
(defprotocol IMouseWheel (-mouse-wheel [this info]))
(defprotocol IKeyPress (-key-press [this info]))
(defprotocol IKeyType (-key-type [this info]))
(defprotocol IClipboardPaste (-clipboard-paste [this info]))
(defprotocol IClipboardCopy (-clipboard-copy [_]))
(defprotocol IClipboardCut (-clipboard-cut [_]))

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

(def ^{:arglists '([elem])
       :doc
       "Specifies the top left corner of a component's bounds\n\n  The origin is vector or 2 numbers [x, y]"}
  origin -origin)

(defn origin-x
  "Convience function for returning the x coordinate of elem's origin"
  [elem]
  (first (origin elem)))

(defn origin-y
  "Convience function for returning the y coordinate of elem's origin"
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
    false))

(declare IBubble -bubble)
(defn -default-mouse-move-global [elem offset]
  (let [[ox oy] (origin elem)
        [sx sy] offset
        child-offset [(- sx ox)
                      (- sy oy)]]
    (let [steps
          (reduce into
                  []
                  (for [child (children elem)]
                    (-mouse-move-global child child-offset)))]
      (if (satisfies? IBubble elem)
        (-bubble elem steps)
        steps))))

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

 IKeyPress
 (-key-press [this info]
   (let [steps (mapcat #(-key-press % info) (children this))]
     (if (satisfies? IBubble this)
       (-bubble this steps)
       steps)))

 IKeyEvent
 (-key-event [this key scancode action mods]
   (let [steps (mapcat #(-key-event % key scancode action mods) (children this))]
     (if (satisfies? IBubble this)
       (-bubble this steps)
       steps))))





(defprotocol IDraw
  (draw [this]))

(extend-protocol IDraw
  nil
  (draw [this])
  
  #?(:cljs cljs.core/PersistentVector
     :clj clojure.lang.PersistentVector)
  (draw [this]
    (doseq [drawable this]
      (draw drawable))))

(defprotocol IComponent)

(extend-protocol IComponent
  nil
  #?(:cljs cljs.core/PersistentVector
     :clj clojure.lang.PersistentVector)
    )


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


#?
(:clj
 (do
   (defprotocol PWrapped
     (-unwrap [this]))

   (defn wrap [o]
     (reify
       Object
       (hashCode [_] (System/identityHashCode o))
       PWrapped
       (-unwrap [_] o)))))


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

(def ^{:arglists '([elem])
       :doc "Returns sub elements of elem. Useful for traversal."}
  children -children)

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


(defprotocol IBubble
  "Allows an element add, remove, modify effects emitted from its children."
  (-bubble [_ effects]
    "Called when an effect is being emitted by a child element. The parent element can either return the same effects or allow them to continue to bubble."))


(defn mouse-move
  "Returns the effects of a mouse move event on elem. Will only call -mouse-move on mouse events within an elements bounds."
  ([elem global-pos]
   (mouse-move elem global-pos [0 0]))
  ([elem global-pos offset]
   #_(when-not (or (satisfies? IMouseMove elem) (satisfies? IComponent elem))
       (throw (Exception. (str "Expecting " IMouseMove " or IComponent, got " (type elem) " " elem))))
   (let [[x y] global-pos
         [sx sy] offset
         [ox oy] (origin elem)
         [width height] (bounds elem)
         local-x (- x (+ sx ox))
         local-y (- y (+ sy oy))]
     (when (and
            (< local-x
               width)
            (>= local-x 0)
            (< local-y
               height)
            (>= local-y 0))
      (if (satisfies? IMouseMove elem)
        (-mouse-move elem [local-x local-y])
        ;; else
        (let [child-offset [(+ ox sx)
                            (+ oy sy)]]
          (let [steps 
                (reduce into
                        []
                        (for [child (children elem)]
                          (mouse-move child global-pos child-offset)))]
            (if (satisfies? IBubble elem)
              (-bubble elem steps)
              steps))))))))

(defn mouse-move-global
  "Returns the effects of a mouse move event on elem. Will -mouse-move-global for all elements and their children."
  ([elem global-pos]
   (mouse-move-global elem global-pos [0 0]))
  ([elem global-pos offset]
   (let [[x y] global-pos
         [sx sy] offset
         [ox oy] (origin elem)]
     (if (satisfies? IMouseMoveGlobal elem)
       (let [local-x (- x (+ sx ox))
             local-y (- y (+ sy oy))]
        (-mouse-move-global elem [local-x local-y]))
       ;; else
       (let [child-offset [(+ ox sx)
                           (+ oy sy)]]
         (let [steps
               (reduce into
                       []
                       (for [child (children elem)]
                         (mouse-move-global child global-pos child-offset)))]
           (if (satisfies? IBubble elem)
             (-bubble elem steps)
             steps)))))))

(defn mouse-event
  "Returns the effects of a mouse move event on elem. Will only call -mouse-move on mouse events within an elements bounds.

  mouse-event is used for both mouse up and mouse down events."
  ([elem global-pos button mouse-down? mods]
   (mouse-event elem global-pos button mouse-down? mods [0 0]))
  ([elem global-pos button mouse-down? mods offset]
   #_(when-not (satisfies? IComponent elem)
       (throw (Exception. (str "Expecting IComponent, got " (type elem) " " elem))))
   (let [left-button? (zero? button)
         ;; satisfies is a macro in clojurescript
         protocol-check (if mouse-down?
                          #(satisfies? IMouseDown %)
                          #(satisfies? IMouseUp %)) 
         protocol-fn (if mouse-down?
                       -mouse-down
                       -mouse-up)
         protocol? (protocol-check elem)
         mouse-event? (satisfies? IMouseEvent elem)]

     (cond
       (or protocol?
           mouse-event?)
       (let [[x y] global-pos
             [sx sy] offset
             [ox oy] (origin elem)
             [width height] (bounds elem)
             local-x (- x (+ sx ox))
             local-y (- y (+ sy oy))]
         
         (when (and
                (< local-x
                   width)
                (>= local-x 0)
                (< local-y
                   height)
                (>= local-y 0))
           
           (concat
            (when protocol?
              (protocol-fn elem [local-x local-y]))
            (when mouse-event?
              (-mouse-event elem [local-x local-y] button mouse-down? mods)))))

       ;; (satisfies? IChildren elem)
       ::else
       (let [[ox oy] (origin elem)
             [sx sy] offset
             child-offset [(+ ox sx)
                           (+ oy sy)]]
         (let [steps 
               (some #(seq
                       (mouse-event % global-pos button mouse-down? mods child-offset))
                     (reverse (children elem)))]
           (if (satisfies? IBubble elem)
             (-bubble elem steps)
             steps)))))))

(defn mouse-down
  "Returns the effects of a mouse down event on elem. Will only call -mouse-event or -mouse-down if the position is in the element's bounds."
  [elem [mx my :as pos]]
  (mouse-event elem pos 0 true 0))

(defn mouse-up
  "Returns the effects of a mouse up event on elem. Will only call -mouse-event or -mouse-down if the position is in the element's bounds."
  [elem [mx my :as pos]]
  (mouse-event elem pos 0 false 0))


(defn make-event-handler [protocol-name protocol protocol-fn]
  (fn handler [elem & args]
    #_(when-not (or (satisfies? protocol elem) (satisfies? IComponent elem))
        (throw (Exception. (str "Expecting " protocol-name " or IComponent, got " (type elem) " " elem))))
    (cond
      (satisfies? protocol elem)
      (apply protocol-fn elem args)
      (satisfies? IChildren elem)
      (let [steps (transduce
                   (map #(apply handler % args))
                   into
                   []
                   (children elem))]
        (if (satisfies? IBubble elem)
          (-bubble elem steps)
          steps)))))

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
(def
  ^{:arglists '([elem [offset-x offset-y :as offset]]),
    :doc "Returns the effects of a scroll event on elem."}
  scroll (make-event-handler "IScroll" IScroll -scroll))


(defcomponent Label [text font]
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

(defcomponent TextSelection [text selection font]
    IOrigin
    (-origin [_]
        [0 0]))

(defn text-selection
  "Graphical elem for drawing a selection of text."
  ([text [selection-start selection-end :as selection]]
   (TextSelection. (str text) selection default-font))
  ([text [selection-start selection-end :as selection] font]
   (TextSelection. (str text) selection font)))


(defcomponent TextCursor [text cursor font]
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



(defcomponent Image [image-path size opacity]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
  (-bounds [_]
    size)
)


(defn image-size [image-path]
  (assert false "image size should be replaced by implementation"))

(defn image
  "Graphical element that draws an image.

  `image-path`: using the skia backend, `image-path` can be one of
  - a string filename
  - a java.net.URL
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


(defcomponent Group [drawables]
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

  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
    drawables))

(defn group
  "Creates a graphical elem that will draw drawables in order"
  [& drawables]
  (Group. drawables))


(defcomponent Translate [x y drawable]
    IOrigin
    (-origin [this]
        [x y])
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


(defcomponent Rotate [degrees drawable]
    IOrigin
    (-origin [this]
        [0 0])
  IChildren
  (-children [this]
      [drawable])

  IBounds
  (-bounds [this]
      (bounds drawable)))

(defn- rotate [degrees drawable]
  (Rotate. degrees drawable))

(defcomponent Spacer [x y]
    IOrigin
    (-origin [_]
        [0 0])
    IDraw
    (draw [this])
    IBounds
    (-bounds [this]
        [x y]))

(defn spacer
  "An empty graphical element with width x and height y.

  Useful for layout."
  [x y]
  (Spacer. x y))


(defcomponent FixedBounds [size drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        size)

  IDraw
  (draw [this]
      (draw drawable))
  IChildren
  (-children [this]
      [drawable]))

(defn fixed-bounds [size drawable]
  (FixedBounds. size drawable))

(defcomponent Padding [px py drawable]
    IDraw
    (draw [this]
        (draw
         (translate px py
                    drawable)))
    IOrigin
    (-origin [this]
        [px py])
  IChildren
  (-children [this]
      [drawable])

  IBounds
  (-bounds [this]
      (let [[w h] (bounds drawable)]
        [(+ w px)
         (+ h py)])))

(defn padding [px py elem]
  (Padding. px py elem))


(defcomponent Path [points]
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


(defcomponent WithColor [color drawables]
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
  IChildren
  (-children [this]
    drawables))

(defn with-color
  "Use color for all children. Color is a vector of [r g b] or [r g b a]. All values should be between 0 and 1 inclusive."
  [color & drawables]
  (WithColor. color drawables))

(defcomponent WithStyle [style drawables]
    IOrigin
    (-origin [_]
        [0 0])
    IBounds
    (-bounds [this]
        (bounds drawables))

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

(defcomponent WithStrokeWidth [stroke-width drawables]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (bounds drawables))

    IChildren
    (-children [this]
        drawables))
(defn with-stroke-width
  "Set the stroke width for drawables."
  [stroke-width & drawables]
  (WithStrokeWidth. stroke-width (vec drawables)))


(defcomponent Scale [scalars drawables]
    IOrigin
    (-origin [_]
        [0 0])
    IBounds
    (-bounds [this]
        (let [[w h] (bounds drawables)
              [sx sy] scalars]
          [(* w sx)
           (* h sy)]))
    IChildren
    (-children [this]
        drawables)
    )
(defn scale
  "Draw drawables using scalars which is a vector of [scale-x scale-y]"
  [sx sy & drawables]
  (Scale. [sx sy] (vec drawables)))


(defcomponent Arc [radius rad-start rad-end steps]

    IOrigin
    (-origin [_]
        [0 0])
  IBounds
  (-bounds [this]
      [0 0]))

(defn- arc [radius rad-start rad-end]
  (Arc. radius rad-start rad-end 10))



(defn rectangle
  "Graphical elem that draws a rectangle.

  See with-style, with-stroke-width, and with-color for more options."
  [width height]
  (path [0 0] [0 height] [width height] [width 0] [0 0]))

(defn filled-rectangle
  "Graphical elem that draws a filled rectangle with color, [r g b] or [r g b a]."
  [color width height]
  (with-color color
    (with-style :membrane.ui/style-fill
      (path [0 0] [0 height] [width height] [width 0] [0 0]))))

(defcomponent RoundedRectangle [width height border-radius]
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
    (draw
     [(let [gray  0.65]
        (with-color [gray gray gray]
          (with-style ::style-stroke
            (rectangle (+ width (* 2 padding-x))
                       (+ height (* 2 padding-y))))))
      (translate padding-x
                 padding-y
                 drawable)])))

(defcomponent Bordered [padding-x padding-y drawable]
    IOrigin
    (-origin [_]
        [0 0])

  IChildren
  (-children [this]
      [drawable])

  IBounds
  (-bounds [this]
      (let [[width height] (bounds drawable)]
        [(+ width (* 2 padding-x))
         (+ height (* 2 padding-y))]))
  IDraw
  (draw [this]
      (bordered-draw this)))

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
    (draw
     [
      (filled-rectangle
       color
       (+ width (* 2 padding-x))
       (+ height (* 2 padding-y)))
      (translate padding-x
                 padding-y
                 drawable)])))

(defcomponent FillBordered [color padding-x padding-y drawable]
    IOrigin
    (-origin [_]
        [0 0])

  IChildren
  (-children [this]
      [drawable])

  IBounds
  (-bounds [this]
      (let [[width height] (bounds drawable)]
        [(+ width (* 2 padding-x))
         (+ height (* 2 padding-y))]))
  IDraw
  (draw [this]
      (fill-bordered-draw this)))

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



(defcomponent Checkbox [checked?]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (bounds (draw-checkbox checked?)))

    IDraw
    (draw [this]
        (draw (draw-checkbox checked?)))
    IChildren
    (-children [this]
        [(draw-checkbox checked?)]))
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

(declare text-bounds)
(defn button-draw [this]
  (let [text (:text this)
        [text-width text-height] (bounds (label text))
        padding 12
        rect-width (+ text-width padding)
        rect-height (+ text-height padding)
        border-radius 3
        ]
    (draw
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
                 (label text))])))

(defcomponent Button [text on-click hover?]
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
  IMouseDown
  (-mouse-down [this [mx my]]
      (when on-click
        (on-click)))

  IDraw
  (draw [this]
    (button-draw this)))
(defn button
  "Graphical elem that draws a button. Optional on-click function may be provided that is called with no arguments when button has a mouse-down event."
  ([text]
   (Button. text nil false))
  ([text on-click]
   (Button. text on-click false))
  ([text on-click hover?]
   (Button. text on-click hover?)))


(defcomponent OnClick [on-click drawables]
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

  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
    drawables)

  IMouseDown
  (-mouse-down [this [mx my]]
      (when on-click
        (on-click))))
(defn on-click
  "Wrap an element with a mouse down event handler, on-click. 

  on-click must accept 0 arguments and should return a sequence of effects."
  [on-click & drawables]
  (OnClick. on-click drawables))



(defcomponent OnMouseDown [on-mouse-down drawables]
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

  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
    drawables)

  IMouseDown
  (-mouse-down [this [mx my :as pos]]
      (when on-mouse-down
        (on-mouse-down pos))))
(defn on-mouse-down
  "Wraps drawables and adds an event handler for mouse-down events.

  on-mouse-down should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of effects."
  [on-mouse-down & drawables]
  (OnMouseDown. on-mouse-down drawables))

(defcomponent OnMouseUp [on-mouse-up drawables]
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

  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
    drawables)

  IMouseUp
  (-mouse-up [this [mx my :as pos]]
      (when on-mouse-up
        (on-mouse-up pos))))
(defn on-mouse-up
  "Wraps drawables and adds an event handler for mouse-up events.

  on-mouse-up should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of effects."
  [on-mouse-up & drawables]
  (OnMouseUp. on-mouse-up drawables))

(defcomponent OnMouseMove [on-mouse-move drawables]
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

  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
    drawables)

  IMouseMove
  (-mouse-move [this [mx my :as pos]]
      (when on-mouse-move
        (on-mouse-move pos))))
(defn on-mouse-move
  "Wraps drawables and adds an event handler for mouse-move events.

  on-mouse-move down should take 1 argument [mx my] of the mouse position in local coordinates and return a sequence of effects."
  [on-mouse-move & drawables]
  (OnMouseMove. on-mouse-move drawables))

(defcomponent OnMouseMoveGlobal [on-mouse-move-global drawables]
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

  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
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
(defn on-mouse-move-global
  "Wraps drawables and adds an event handler for mouse-move-global events.

  on-mouse-move-global down should take 1 argument [mx my] of the mouse position in global coordinates and return a sequence of effects."
  [on-mouse-move-global & drawables]
  (OnMouseMoveGlobal. on-mouse-move-global drawables))

(defcomponent OnMouseEvent [on-mouse-event drawables]
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

    IDraw
    (draw [this]
        (doseq [drawable drawables]
          (draw drawable)))
    IChildren
    (-children [this]
        drawables)

    IMouseEvent
    (-mouse-event [this pos button mouse-down? mods]
        (when on-mouse-event
          (on-mouse-event pos button mouse-down? mods))))
(defn on-mouse-event
  "Wraps drawables and adds an event handler for mouse events.

  on-mouse-event should take 4 arguments [pos button mouse-down? mods] and return a sequence of effects."
  [on-mouse-event & drawables]
  (OnMouseEvent. on-mouse-event drawables))


(defcomponent OnKeyPress [on-key-press drawables]
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


  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
    drawables)
)
(defn on-key-press
  "Wraps drawables and adds an event handler for key-press events.

  on-key-press should take 1 argument key and return a sequence of effects."
  [on-key-press & drawables]
  (OnKeyPress. on-key-press drawables))

(defcomponent OnKeyEvent [on-key-event drawables]
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


  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
    drawables)
)
(defn on-key-event
  "Wraps drawables and adds a handler for key events.

  on-key-event should take 4 arguments key, scancode, action, mods and return a sequence of effects."
  [on-key-event & drawables]
  (OnKeyEvent. on-key-event drawables))

(defcomponent OnBubble [on-bubble drawables]
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

  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
      drawables)

   IBubble
   (-bubble [this effects]
       (on-bubble effects)))

(defn on-bubble
  "Wraps drawables and adds a handler for bubbling

  on-bubble should take seq of effects"
  [on-bubble & drawables]
  (OnBubble. on-bubble drawables))


(defcomponent OnClipboardPaste [on-clipboard-paste drawables]
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

    IDraw
    (draw [this]
        (doseq [drawable drawables]
          (draw drawable)))
    IChildren
    (-children [this]
        drawables)

    IClipboardPaste
    (-clipboard-paste [this s]
        (when on-clipboard-paste
          (on-clipboard-paste s))))
(defn on-clipboard-paste
  "Wraps drawables and adds a handler for clipboard paste events.

  on-clipboard-paste should take 1 arguments s and return a sequence of effects."
  [on-clipboard-paste & drawables]
  (OnClipboardPaste. on-clipboard-paste drawables))


(defcomponent OnClipboardCopy [on-clipboard-copy drawables]
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

  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
    drawables)

  IClipboardCopy
  (-clipboard-copy [this]
      (when on-clipboard-copy
        (on-clipboard-copy))))
(defn on-clipboard-copy
  "Wraps drawables and adds a handler for clipboard copy events.

  on-clipboard-copy should take 0 arguments and return a sequence of effects."
  [on-clipboard-copy & drawables]
  (OnClipboardCopy. on-clipboard-copy drawables))



(defcomponent OnClipboardCut [on-clipboard-cut drawables]
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

  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
    drawables)

  IClipboardCut
  (-clipboard-cut [this]
      (when on-clipboard-cut
        (on-clipboard-cut))))
(defn on-clipboard-cut
  "Wraps drawables and adds a handler for clipboard cut events.

  on-clipboard-copy should take 0 arguments and return a sequence of effects."
  [on-clipboard-cut & drawables]
  (OnClipboardCut. on-clipboard-cut drawables))



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


(defn center [elem [width height]]
  (let [[ewidth eheight] (bounds elem)]
    (translate (int (- (/ width 2)
                       (/ ewidth 2)))
               (int (- (/ height 2)
                       (/ eheight 2)))
               elem)))


(defcomponent OnScroll [on-scroll drawables]
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

  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))


  IScroll
  (-scroll [this [offset-x offset-y :as offset]]
    (when on-scroll
      (on-scroll offset)))

  IChildren
  (-children [this]
    drawables))

(defn on-scroll
  "Wraps drawables and adds an event handler for scroll events.

  on-scroll should take 1 argument [offset-x offset-y] of the scroll offset and return a sequence of effects."
  [on-scroll & drawables]
  (OnScroll. on-scroll drawables))


(defcomponent ScissorView [offset bounds drawable]
    IOrigin
    (-origin [this]
        [0 0])
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

(defcomponent ScrollView [bounds offset drawable]
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

  IMouseMove
  (-mouse-move [this [mx my :as pos]]
      (mouse-move drawable [(- mx (nth offset 0))
                            (- my (nth offset 1))]))

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


(defcomponent EventHandler [event-type handler drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (bounds drawable))

  IChildren
  (-children [this]
      [drawable])

  IDraw
  (draw [this]
      (draw drawable))

  IBubble
  (-bubble [this events]
      (apply concat
             (for [step events
                   :let [step-type (first step)]]
               (if (-can-handle? this step-type)
                 (-handle-event this step-type (rest step))
                 [step]))))

  IHandleEvent
    (-can-handle? [this other-event-type]
        (= event-type other-event-type))

  (-handle-event [this event-type event-args]
      (apply handler event-args)))



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
                 (EventHandler. event-type handler body))))
      body)))


(defn wrap-on
  "Wraps an elem with event handlers.

  events are pairs of events and event handlers and the last argument should be an elem.
  The event handlers should accept an extra first argument to the event which is the original event handler.

  example:

  Wraps a button with a mouse-down handler that only returns an effect when the x coordinate is even.
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

                 ;; ;; else
                 ;; (EventHandler. event-type handler body)
                 )))
      body)))

(defcomponent NoEvents [drawable]
    IBounds
    (-bounds [this]
        (bounds drawable))

  IOrigin
  (-origin [_]
      [0 0])

  IChildren
    (-children [this]
        [drawable])

    IDraw
    (draw [this]
        (draw drawable))

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
    IMouseDown
    (-mouse-down [this pos] nil)
    IMouseMove
    (-mouse-move [this pos] nil)
    IMouseMoveGlobal
    (-mouse-move-global [this pos] nil)
    IMouseUp
    (-mouse-up [this pos] nil)
    IMouseWheel
    (-mouse-wheel [this pos] nil)
    IScroll
    (-scroll [this pos] nil))

#_(defn no-events [body]
  (NoEvents. body))

(defn no-events [body]
  (let [do-nothing (constantly nil)]
    (on :mouse-down do-nothing
        :key-press do-nothing
        :mouse-up do-nothing
        :mouse-move do-nothing
        body)))

(defcomponent NoKeyEvent [drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (bounds drawable))

    IChildren
    (-children [this]
        [drawable])

    IDraw
    (draw [this]
        (draw drawable))
    IHasKeyEvent
    (has-key-event [this]
        false))

(defmacro maybe-key-event [test body]
  `(if ~test
     ~body
     (NoKeyEvent. ~body)))

(defcomponent NoKeyPress [drawable]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (bounds drawable))

    IChildren
    (-children [this]
        [drawable])

    IDraw
    (draw [this]
        (draw drawable))
    IHasKeyPress
    (has-key-press [this]
        false))

(defmacro maybe-key-press [test body]
  `(if ~test
     ~body
     (NoKeyPress. ~body)))

(defn ^:dynamic run [& args]
  (throw (Exception. "No backend found. Have you required membrane.skia or membrane.webgl?")))

#?(:clj
   (defn ^:dynamic run-sync [& args]
     (throw (Exception. "No backend found. Have you required membrane.skia or membrane.webgl?"))))
#_(defn run [make-ui]
  (assert false "run should be replaced by implementation"))



(defn index-for-position [font text x y]
  (assert false "image size should be replaced by implementation"))

(defn copy-to-clipboard [s])

