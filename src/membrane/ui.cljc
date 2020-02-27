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

(def main-font (Font. #? (:clj (if (.exists (clojure.java.io/file "/System/Library/Fonts/HelveticaNeueDeskInterface.ttc"))
                                 "/System/Library/Fonts/HelveticaNeueDeskInterface.ttc"
                                 "/usr/share/fonts/truetype/ubuntu/Ubuntu-R.ttf")
                          :cljs ;;"IM Fell English"
                          "Helvetica"
                          )
                         14
                         #?(:clj nil
                            :cljs nil)))


(defprotocol IMouseDrag (-mouse-drag [this info]))
(defprotocol IMouseMove (-mouse-move [this info]))
(defprotocol IMouseDown (-mouse-down [this info]))
(defprotocol IMouseMoveGlobal (-mouse-move-global [this info]))
(defprotocol IScroll (-scroll [this info]))
(defprotocol IMouseUp (-mouse-up [this info]))
(defprotocol IMouseWheel (-mouse-wheel [this info]))
(defprotocol IKeyPress (-key-press [this info]))
(defprotocol IKeyType (-key-type [this info]))
(defprotocol IClipboardPaste (-clipboard-paste [this info]))
(defprotocol IHover (-hover [this info]))

(declare children)

(defprotocol IOrigin
  (-origin [this]))

(extend-protocol IOrigin
  #?(:cljs cljs.core/PersistentVector
     :clj clojure.lang.PersistentVector)
  (-origin [this]
    [0 0])

  nil
  (-origin [this]
    [0 0]))

(def origin #'-origin )
#_(defn origin [x]
  #_(when-not (or (satisfies? IOrigin x) (satisfies? IComponent x))
      (throw (Exception. (str "Expecting IOrigin or IComponent, got " x))))
  (origin x)
  #_(if (satisfies? IOrigin x)
    (-origin x)
    [0 0]))

(defn origin-x [x]
  (first (origin x)))

(defn origin-y [x]
  (second (origin x)))

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



(defprotocol IMouseEvent
  (-mouse-event [this pos button mouse-down? mods]))


(defprotocol IClipboardCopy
  (-clipboard-copy [_]))

(defprotocol IClipboardCut
  (-clipboard-cut [_]))

;; (defprotocol IFocus)

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

(defprotocol IComponent
  #_(cid [this]))

(extend-protocol IComponent
  nil
  #?(:cljs cljs.core/PersistentVector
     :clj clojure.lang.PersistentVector)
    )


(defprotocol IBounds
  (-bounds [this]))

(declare origin bounds)
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
     this)))


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







(defn -bounds-raw [x]
  #_(when-not (or (satisfies? IBounds x) (satisfies? IComponent x))
      (throw (Exception. (str "Expecting IBounds or IComponent, got " (type x) " " x))))
  (if (satisfies? IBounds x)
    (-bounds x)
    [0 0]))



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


(def bounds (memoize -bounds-raw) #__bounds #_(memoize2 _bounds))


(defprotocol IChildren
  (-children [this]))


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

(def children -children )
#_(defn children [x]
  (-children x)
  #_(if (satisfies? IChildren x)
      (-children x)
      []))




(defn width [ibounds]
  (let [[width height] (bounds ibounds)]
    width))
(defn height [ibounds]
  (let [[width height] (bounds ibounds)]
    height))



(defprotocol IBubble
  (-bubble [_ events]))



(defn mouse-move
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

(def key-press (make-event-handler "IKeyPress" IKeyPress -key-press))
(def key-event (make-event-handler "IKeyEvent" IKeyEvent -key-event))
(def clipboard-cut (make-event-handler "IClipboardCut" IClipboardCut -clipboard-cut))
(def clipboard-copy (make-event-handler "IClipboardCopy" IClipboardCopy -clipboard-copy))
(def clipboard-paste (make-event-handler "IClipboardPaste" IClipboardPaste -clipboard-paste))
(def scroll (make-event-handler "IScroll" IScroll -scroll))



(defcomponent Label [text options]
    IOrigin
    (-origin [_]
        [0 0]))

(defn label [text & options]


  (let [options (apply hash-map options)]
    (when-let [font-size (:font-size options)]
      (assert (number? font-size) "Font size must be numeric")
      (assert (pos? font-size) "Font size must be positive"))
    (Label. (str text) options)))

(defcomponent TextSelection [text selection options]
    IOrigin
    (-origin [_]
        [0 0]))

(defn text-selection [text [selection-start selection-end :as selection] & options]
  (TextSelection. (str text) selection (apply hash-map options)))


(defcomponent TextCursor [text cursor options]
    IOrigin
    (-origin [_]
        [0 0]))

(defn text-cursor [text cursor & options]
  (TextCursor. (str text) cursor (apply hash-map options)))



(defcomponent Image [image-path size opacity]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
  (-bounds [_]
    size)
)


(declare image-size)
(defn image-size [image-path]
  (assert false "image size should be replaced by implementation"))

(defn image
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

(defn group [& drawables]
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
      (bounds drawable)))

(defn translate [x y drawable]
  (Translate. (int x) (int y) drawable))


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

(defn rotate [degrees drawable]
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

(defn spacer [x y]
  (Spacer. (int x) (int y)))


(defcomponent Path [points]
    IOrigin
    (-origin [_]
        [0 0])
  IBounds
  (-bounds [this]
    (let [maxx (apply max (map first points))
          maxy (apply max (map second points))]
      [maxx maxy])))


(defn path [& points]
  (Path. points))


(defcomponent Polygon [color points]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
  (-bounds [this]
    (let [maxx (apply max (map first points))
          maxy (apply max (map second points))]
      [maxx maxy])))

(defn polygon [color & points]
  (Polygon. color points))

(defcomponent UseColor [color drawables]
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

(defn use-color [color & drawables]
  (UseColor. color drawables))

(defcomponent UseStyle [style drawables]
    IOrigin
    (-origin [_]
        [0 0])
    IBounds
    (-bounds [this]
        (bounds drawables))

    IChildren
    (-children [this]
        drawables))

(defn use-style [style & drawables]
  "Style for drawing paths and polygons

one of:
:membrane.ui/style-fill
:membrane.ui/style-stroke
:membrane.ui/style-stroke-and-fill
"
  (UseStyle. style (vec drawables)))

(defcomponent UseStrokeWidth [stroke-width drawables]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (bounds drawables))

    IChildren
    (-children [this]
        drawables))
(defn use-stroke-width [stroke-width & drawables]
  (UseStrokeWidth. stroke-width (vec drawables)))


(defcomponent UseScale [scalars drawables]
    IOrigin
    (-origin [_]
        [0 0])
    IBounds
    (-bounds [this]
        (bounds drawables))
    IChildren
    (-children [this]
        drawables)
    )
(defn use-scale [scalars & drawables]
  (UseScale. scalars (vec drawables)))


(defcomponent Arc [radius rad-start rad-end steps]

    IOrigin
    (-origin [_]
        [0 0])
  IBounds
  (-bounds [this]
      [0 0]))

(defn arc [radius rad-start rad-end]
  (Arc. radius rad-start rad-end 10))



(defn rectangle [width height]
  (path [0 0] [0 height] [width height] [width 0] [0 0]))

(defn filled-rectangle [color width height]
  (polygon color [0 0] [0 height]  [width height] [width 0]  [0 0]))

(defcomponent RoundedRectangle [width height border-radius]
    IOrigin
    (-origin [_]
        [0 0])
  IBounds
  (-bounds [this]
      [width height]))

(defn rounded-rectangle [width height border-radius]
  (RoundedRectangle. width height border-radius))

(defn bordered-draw [this]
  (let [{:keys [drawable padding-x padding-y]} this
        [width height] (bounds drawable)]
    (draw
     [(let [gray  0.65]
        (use-color [gray gray gray]
                   (rectangle (+ width (* 2 padding-x))
                              (+ height (* 2 padding-y)))))
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

(defn bordered [padding drawable]
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

(defn fill-bordered [color padding drawable]
  (if (vector? padding)
    (let [[px py] padding]
      (FillBordered. color px py drawable))
    (FillBordered. color padding padding drawable)))

(defn draw-checkbox [checked?]
  (if checked?
    (let [border [0.14901960784313725 0.5254901960784314 0.9882352941176471]
          fill [0.2 0.5607843137254902 0.9882352941176471]]
      [(use-color fill
                     (rounded-rectangle 12 12 2))

       (use-style ::style-stroke
                  (use-color border
                             (rounded-rectangle 12 12 2)))

       (translate 0 1
                  (use-stroke-width
                   1.5
                   (use-color [0 0 0 0.3]
                              (path [2 6] [5 9] [10 2]))))

       (use-stroke-width
        1.5
        (use-color [1 1 1]
                      (path [2 6] [5 9] [10 2])))
       ])
    (let [gray 0.6862745098039216]
      (use-style ::style-stroke
                 (use-color [gray gray gray ]
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
(defn checkbox [checked?]
  (Checkbox. checked?))


(defn box-contains? [[x y width height] [px py]]
  (and (<= px (+ x width))
       (>= px x)
       (<= py (+ y height))
       (>= py y)))

(declare text-bounds)
(defn button-draw [this]
  (let [text (:text this)
        [text-width text-height] (bounds (label text))
        padding 20]
    (draw
     [
      (rectangle (+ text-width padding) (+ text-height padding))
      
      (translate (/ padding 2)
                 (/ padding 2)
                 (label text))])))

(defcomponent Button [text on-click]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [_]
        (let [padding 20
              [text-width text-height] (bounds (label text))]
          [(+ text-width padding) (+ text-height padding)]))
  IMouseDown
  (-mouse-down [this [mx my]]
      (when on-click
        (on-click)))

  IDraw
  (draw [this]
    (button-draw this)))
(defn button [text & [on-click on-hover hover?]]
  (Button. text on-click))


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
(defn on-click [on-click & drawables]
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
(defn on-mouse-down [on-mouse-down & drawables]
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
(defn on-mouse-up [on-mouse-up & drawables]
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
(defn on-mouse-move [on-mouse-move & drawables]
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
(defn on-mouse-move-global [on-mouse-move-global & drawables]
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
(defn on-mouse-event [on-mouse-event & drawables]
  (OnMouseEvent. on-mouse-event drawables))


(defcomponent OnKeyPress [on-keypress drawables]
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
      (boolean on-keypress))

  IKeyPress
  (-key-press [this key]
    (when on-keypress
      (on-keypress key)))


  IDraw
  (draw [this]
      (doseq [drawable drawables]
        (draw drawable)))
  IChildren
  (-children [this]
    drawables)
)
(defn on-keypress [on-keypress & drawables]
  (OnKeyPress. on-keypress drawables))

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
(defn on-key-event [on-key-event & drawables]
  (OnKeyEvent. on-key-event drawables))


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
(defn on-clipboard-paste [on-clipboard-paste & drawables]
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
(defn on-clipboard-copy [on-clipboard-copy & drawables]
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
(defn on-clipboard-cut [on-clipboard-cut & drawables]
  (OnClipboardCut. on-clipboard-cut drawables))



(defn vertical-layout [& elems]
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

(defn horizontal-layout [& elems]
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

(defn on-scroll [on-scroll & drawables]
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

(defn scissor-view [offset bounds drawable]
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

(defn scrollview [bounds offset drawable]
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



(defn on [& events]
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
                 :keypress
                 (on-keypress handler
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


(defn wrap-on [& events]
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

                 :keypress
                 (on-keypress
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

(def ^:dynamic run)

#?(:clj
   (def ^:dynamic run-sync))
#_(defn run [make-ui]
  (assert false "run should be replaced by implementation"))



(defn index-for-position [font text x y]
  (assert false "image size should be replaced by implementation"))
