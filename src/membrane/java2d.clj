(ns membrane.java2d
  (:require [membrane.ui :as ui
             :refer [IBounds
                     bounds
                     origin
                     defcomponent]])
  (:import java.awt.BasicStroke
           java.awt.image.BufferedImage
           javax.imageio.ImageIO
           java.awt.image.RescaleOp
           java.awt.geom.Path2D$Double
           java.awt.geom.RoundRectangle2D$Double
           java.awt.Color
           java.awt.Font
           java.awt.Toolkit
           java.awt.font.FontRenderContext
           java.awt.RenderingHints
           java.awt.GraphicsEnvironment
           java.awt.Graphics2D))

(def ^:dynamic *g* nil)
(def ^:dynamic *paint-style* :membrane.ui/style-fill)
(def ^:dynamic *image-cache* nil)

(defn get-image [image]
  (if (instance? BufferedImage image)
    image
    (if-let [img (get @*image-cache* image)]
      img
      (let [img (with-open [is (clojure.java.io/input-stream image)]
                  (let [image-stream (ImageIO/createImageInputStream is)
                        buffered-image (ImageIO/read image-stream)]
                    buffered-image))]
        (swap! *image-cache* assoc image img)
        img))))

(defn get-java-font [font]
  (Font. (:name font)
         0
         (or (:size font)
             (:size ui/default-font))) )

(defn merge-stroke
  "Create a new java.awt.BasicStroke with the non properties replaced.
  
  keys:

  `:width`
  float 	getLineWidth()
  Returns the line width.

  `:cap`
  int 	getEndCap()
  Returns the end cap style.

  `:join`
  int 	getLineJoin()
  Returns the line join style.

  `:miter-limit`
  float 	getMiterLimit()
  Returns the limit of miter joins.

  `:dash`
  float[] 	getDashArray()
  Returns the array representing the lengths of the dash segments.

  `:dash-phase`
  float 	getDashPhase()
  Returns the current dash phase.
"
  [stroke {:keys [width cap join miter-limit dash dash-phase]}]
  (BasicStroke. (or width (.getLineWidth ^BasicStroke stroke))
                (or cap (.getEndCap ^BasicStroke stroke))
                (or join (.getLineJoin ^BasicStroke stroke))
                (or miter-limit (.getMiterLimit ^BasicStroke stroke))
                (or dash (.getDashArray ^BasicStroke stroke))
                (or dash-phase (.getDashPhase ^BasicStroke stroke))))

(defprotocol IDraw
  (draw [this]))

(ui/add-default-draw-impls! IDraw #'draw)


(defmacro push-paint [& body]
  `(let [p# (.getPaint ^Graphics2D *g*)]
     (try
       ~@body
       (finally
         (.setPaint ^Graphics2D *g* p#)))))

(defmacro push-stroke [& body]
  `(let [stroke# (.getStroke ^Graphics2D *g*)]
     (try
       ~@body
       (finally
         (.setStroke ^Graphics2D *g* stroke#)))))

(defmacro push-transform [& body]
  `(let [transform# (.getTransform ^Graphics2D *g*)]
     (try
       ~@body
       (finally
         (.setTransform ^Graphics2D *g* transform#)))))

(defmacro push-color [& body]
  `(let [color# (.getColor ^Graphics2D *g*)]
     (try
       ~@body
       (finally
         (.setColor ^Graphics2D *g* color#)))))

(defmacro push-color [& body]
  `(let [color# (.getColor ^Graphics2D *g*)]
     (try
       ~@body
       (finally
         (.setColor ^Graphics2D *g* color#)))))

(defmacro push-font [& body]
  `(let [font# (.getFont ^Graphics2D *g*)]
     (try
       ~@body
       (finally
         (.setFont ^Graphics2D *g* font#))))
  )


(extend-type membrane.ui.WithStrokeWidth
  IDraw
  (draw [this]

    
    (let [stroke-width (:stroke-width this)]
      (push-stroke
        (.setStroke ^Graphics2D *g* (merge-stroke (.getStroke ^Graphics2D *g*)
                                      {:width stroke-width}))
        (doseq [drawable (:drawables this)]
          (draw drawable))))))

(extend-type membrane.ui.WithStyle
  IDraw
  (draw [this]
    (let [style (:style this)]
      (binding [*paint-style* style]
        (doseq [drawable (:drawables this)]
            (draw drawable))))))


(defn get-font-render-context []
  (if *g*
    (.getFontRenderContext ^Graphics2D *g*)
    (FontRenderContext. nil (boolean true) (boolean true))))

(defn text-bounds [font text]
  (let [lines (clojure.string/split text #"\n" -1)
        frc (get-font-render-context)
        metrics (.getLineMetrics ^Font font text frc)
        line-height (.getHeight metrics)
        
        
        widths (map (fn [line]
                      (let [rect2d (.getStringBounds ^Font font line frc)]
                        (.getWidth ^java.awt.geom.Rectangle2D rect2d)))
                    lines)
        maxx (reduce max 0 widths)
        maxy (* (dec line-height)
                (count lines))]
    [maxx maxy]))


(defcomponent LabelRaw [text font]
    IBounds
    (-bounds [_]
        (let [[maxx maxy] (text-bounds (get-java-font font)
                                                 text)
              maxx (max 0 maxx)
              maxy (max 0 maxy)]
          [maxx maxy]))

    IDraw
    (draw [this]
        (let [lines (clojure.string/split (:text this) #"\n" -1)
              font (get-java-font (:font this))
              frc (get-font-render-context)
              metrics (.getLineMetrics ^Font font text frc)
              line-height (.getHeight metrics)

]
          (push-transform
           (push-font
           
            (when font
              (.setFont ^Graphics2D *g* font))
            (doseq [line lines]
              (.translate ^Graphics2D *g* ^double (double 0.0) ^double (double (dec line-height)))
              (.drawString ^Graphics2D *g* ^String line 0 0))
            ))
          
          )))



(declare ->Cached rectangle)
(extend-type membrane.ui.Label
  IBounds
  (-bounds [this]
    (let [[maxx maxy] (text-bounds (get-java-font (:font this))
                                             (:text this))
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
      [maxx maxy]))
  IDraw
  (draw [this]
    (draw (LabelRaw. (:text this)
                     (:font this)))))


(defn image-draw [{:keys [image-path size opacity] :as image}]
  (when-let [bi (get-image image-path)]
    (let [[w h] size]
      (push-transform
       (when (or (not= (.getWidth ^BufferedImage bi) w)
                 (not= (.getHeight ^BufferedImage bi) h))
         (let [sx (/ w (.getWidth ^BufferedImage bi))
               sy (/ h (.getHeight ^BufferedImage bi))]
           (.scale ^Graphics2D *g* sx sy)))
       
       (if opacity
         (let [scales (into-array Float/TYPE [1 1 1 opacity])
               offsets (float-array 4)
               op (RescaleOp. ^floats scales ^floats offsets nil)]
           (.drawImage ^Graphics2D *g* ^BufferedImage bi op 0 0))
         (.drawImage ^Graphics2D *g* ^BufferedImage bi 0 0 nil))))))


(extend-type membrane.ui.Image
  IDraw
  (draw [this]
    (image-draw this)))

(extend-type membrane.ui.Translate
  IDraw
  (draw [this]
    (push-transform
     (.translate ^Graphics2D *g* (double (:x this)) (double (:y this)))
     (draw (:drawable this)))))



#_(defn text-selection-draw [{:keys [text font]
                            [selection-start selection-end] :selection
                            :as text-selection}]
  (let [font-ptr (get-font font)
        lines (clojure.string/split-lines text)]

    (save-canvas
     (loop [lines (seq lines)
            selection-start selection-start
            selection-end selection-end]
       (if (and lines (>= selection-end 0))
         (let [line (first lines)
               line-bytes (.getBytes ^String line "utf-8")
               line-count (count line)]
           (when (< selection-start line-count)
             (.write ^Memory skia-buf 0 line-bytes 0 (alength ^bytes line-bytes))
             (Skia/skia_render_selection *skia-resource* font-ptr skia-buf (alength line-bytes) (int (max 0 selection-start)) (int (min selection-end
                                                                                                                                        line-count))))
           (Skia/skia_next_line *skia-resource* font-ptr)
           (recur (next lines) (- selection-start line-count 1) (- selection-end line-count 1))))))))

#_(extend-type membrane.ui.TextSelection
  IBounds
  (-bounds [this]
    (let [[minx miny maxx maxy] (text-bounds (get-font (:font this))
                                             (:text this))
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
      [maxx maxy]))

  IDraw
  (draw [this]
    (text-selection-draw this)))

#_(defn text-cursor-draw [{:keys [text font cursor]
                         :as text-cursor}]
  (let [cursor (min (count text)
                    cursor)
        font-ptr (get-font font)
        lines (clojure.string/split-lines (str text " "))]
    (save-canvas
     (loop [lines (seq lines)
            cursor cursor]
       (if (and lines (>= cursor 0))
         (let [line (first lines)
               line-bytes (.getBytes ^String line "utf-8")
               line-count (count line)]
           (.write ^Memory skia-buf 0 line-bytes 0 (alength ^bytes line-bytes))
           (when (< cursor (inc line-count))
             (Skia/skia_render_cursor *skia-resource* font-ptr skia-buf (alength line-bytes) (int (max 0 cursor))))
           (Skia/skia_next_line *skia-resource* font-ptr)

           (recur (next lines) (- cursor line-count 1))))))))

#_(extend-type membrane.ui.TextCursor
  IBounds
  (-bounds [this]
    (let [[minx miny maxx maxy] (text-bounds (get-font (:font this))
                                             (:text this))
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
      [maxx maxy]))

  IDraw
  (draw [this]
    (text-cursor-draw this)))


(defn stroke-or-fill [shape]
  (case *paint-style*
    :membrane.ui/style-fill (.fill ^Graphics2D *g* shape)
    :membrane.ui/style-stroke (.draw ^Graphics2D *g* shape)
    :membrane.ui/style-stroke-and-fill (do (.draw ^Graphics2D *g* shape)
                                           (.fill ^Graphics2D *g* shape))))

(extend-type membrane.ui.Path
  IDraw
  (draw [this]
    (let [points (:points this)
          path (java.awt.geom.Path2D$Double.)
          [x1 y1] (first points)]
      (.moveTo path x1 y1)
      (doseq [[x y] (rest points)]
        (.lineTo path x y))
      (stroke-or-fill path))))


(extend-type membrane.ui.RoundedRectangle
  IDraw
  (draw [this]
    (let [rect (java.awt.geom.RoundRectangle2D$Double. 0 0
                                                       (:width this) (:height this)
                                                       (:border-radius this) (:border-radius this))]
      (stroke-or-fill rect))))

(extend-type membrane.ui.WithColor
  IDraw
  (draw [this]
    (let [[r g b a] (:color this)]
      (push-color
       (.setColor ^Graphics2D *g* (Color. (float r) (float g) (float b)
                              (float (if a
                                       (float a)
                                       1))))
       (doseq [drawable (:drawables this)]
         (draw drawable))))))

(extend-type membrane.ui.Scale
  IDraw
  (draw [this]
    (let [[sx sy] (:scalars this)]
      (push-transform
       (.scale ^Graphics2D *g* sx sy)
       (doseq [drawable (:drawables this)]
        (draw drawable))))))

(extend-type membrane.ui.Arc
  IDraw
  (draw [this]
    #_(let [arc-length (- (:rad-end this) (:rad-start this))]
      (draw-line-strip
       (doseq [i (range (inc (:steps this)))
               :let [pct (/ (float i) (:steps this))
                     rad (- (+ (:rad-start this)
                               (* arc-length pct)))
                     x (* (:radius this) (Math/cos rad))
                     y (* (:radius this) (Math/sin rad))]]
         (vertex x y))))))


#_(defn scissor-draw [scissor-view]
  (save-canvas
   (let [[ox oy] (:offset scissor-view)
         [w h] (:bounds scissor-view)]
     (Skia/skia_clip_rect *skia-resource* (float ox) (float oy) (float w) (float h))
     (draw (:drawable scissor-view)))))

#_(extend-type membrane.ui.ScissorView
  IDraw
  (draw [this]
      (scissor-draw this)))


#_(defn scrollview-draw [scrollview]
  (draw
   (ui/->ScissorView [0 0]
                  (:bounds scrollview)
                  (let [[mx my] (:offset scrollview)]
                    (translate mx my (:drawable scrollview))))))

#_(extend-type membrane.ui.ScrollView
  IDraw
  (draw [this]
    (scrollview-draw this)))



#_(defcomponent Cached [drawable]
    IOrigin
    (-origin [_]
        (origin drawable))

    IBounds
    (-bounds [_]
        (bounds drawable))

  IChildren
  (-children [this]
      [drawable])

  IDraw
  (draw [this]
    (cached-draw drawable)

    )
  )

(defn image-size-raw [image-path]
  (try
    (with-open [is (clojure.java.io/input-stream image-path)]
      (let [image-stream (ImageIO/createImageInputStream is)
            buffered-image (ImageIO/read image-stream)]
        [(.getWidth buffered-image)
         (.getHeight buffered-image)]))
    (catch Exception e
      (.printStackTrace e)
      [0 0])))

(def image-size (memoize image-size-raw))
(intern (the-ns 'membrane.ui)
        (with-meta 'image-size
          {:arglists '([image-path])
           :doc "Returns the [width, height] of the file at image-path."})
        image-size)

(defn draw-to-image
  ([elem]
   (draw-to-image elem nil))
  ([elem size]

   (let [[w h :as size] (if size
                          size
                          (let [[w h] (bounds elem)
                                [ox oy] (origin elem)]
                            [(+ w ox)
                             (+ h oy)]))
         _ (assert (and (pos? (first size))
                        (pos? (second size)))
                   "Size must be two positive numbers [w h]")
         img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)]
     (binding [*g* (.createGraphics img)
               *image-cache* (atom {})]
       (.setRenderingHint ^Graphics2D *g*
                          RenderingHints/KEY_ANTIALIASING
                          RenderingHints/VALUE_ANTIALIAS_ON)
       (.setRenderingHint ^Graphics2D *g*
                          RenderingHints/KEY_TEXT_ANTIALIASING,
                          RenderingHints/VALUE_TEXT_ANTIALIAS_ON)
       

       
       (.setFont ^Graphics2D *g* (get-java-font (ui/font nil nil)))

       (draw (ui/filled-rectangle [1 1 1]
                                  w h))
       (.setColor ^Graphics2D *g* (Color/BLACK))
       (draw elem))
     img)))

(defn save-to-image!
  [f elem]
  (let [bi (draw-to-image elem)]
    (with-open [os (clojure.java.io/output-stream f)]
      (ImageIO/write ^BufferedImage bi "png" os))))

(defn available-font-families []
  (let [ge (GraphicsEnvironment/getLocalGraphicsEnvironment)]
    (.getAvailableFontFamilyNames ge)))

(defn img-test []
  (save-to-image!
   "img-test.png"
   (ui/with-color [1 0 0]
     [(ui/image "resources/Clojure_logo.svg.png"
                [nil 200]
                0.5)
      
      (ui/rectangle 50 50)
      (ui/rectangle 100 100)

      (ui/with-color [0 0 0]
        (ui/translate 100 100
                      (ui/label "hello\nthere")))
      
      ]))
  )



(defn -main [& args]
  (try
    (img-test)
    (println "done!")
    (catch Exception e
      (println e)))
  )



