(ns membrane.java2d
  (:require [membrane.ui :as ui
             :refer [IBounds
                     bounds
                     origin]])
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
           java.awt.Graphics2D
           java.awt.Component
           java.awt.event.MouseEvent
           java.awt.event.KeyEvent
           java.awt.event.MouseListener
           java.awt.event.MouseMotionListener
           java.awt.event.KeyListener
           java.awt.event.WindowEvent
           java.awt.Dimension
           javax.swing.JFrame))

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
         (bit-or 0
                 (if (= :bold
                        (:weight font))
                   Font/BOLD
                   0)
                 (if (= :italic
                        (:slant font))
                   Font/ITALIC
                   0))
         (or (:size font)
             (:size ui/default-font))) )

(defn font-exists? [font]
  (not= "Dialog"
        (.getFamily ^Font (get-java-font font))))

(defn get-font-render-context []
  (if *g*
    (.getFontRenderContext ^Graphics2D *g*)
    (FontRenderContext. nil (boolean true) (boolean true))))

(defn font-advance-x [font s]
  (let [jfont (get-java-font font)
        frc (get-font-render-context)
        glyphs (.createGlyphVector jfont frc s)
        glyph (.getGlyphMetrics glyphs 0)]
    (.getAdvanceX glyph)))

(defn font-line-height [font]
  (let [frc (get-font-render-context)
        jfont (get-java-font font)
        ;; I don't think the characters matter here
        s ""
        metrics (.getLineMetrics ^Font jfont s frc)]
    (.getHeight metrics)))

(defn font-metrics [font]

  ;; You can get font metrics from the default toolkit,
  ;; the integer return type is too coarse
  #_(let [font-metrics (.getFontMetrics (Toolkit/getDefaultToolkit)
                                      (get-java-font font))]
    ;; these are all integers instead of floats >:/
      {:ascent (.getAscent font-metrics)
     :descent (.getDescent font-metrics)
     :leading (.getLeading font-metrics)
     :line-height (.getHeight font-metrics)
     :max-advance (.getMaxAdvance font-metrics)
     :max-ascent (.getMaxAscent font-metrics)
     :max-descent (.getMaxDescent font-metrics)
     :uniform-line-metrics (.hasUniformLineMetrics font-metrics)})
  (let [frc (get-font-render-context)
        jfont (get-java-font font)
        ;; I don't think the characters matter here
        s ""
        metrics (.getLineMetrics ^Font jfont s frc)]
    {:ascent (.getAscent metrics)
     :descent (.getDescent metrics)
     :leading (.getLeading metrics)}))

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


(defrecord LabelRaw [text font]
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
       
       (if (and opacity (not= 1 opacity))
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


(def selection-color [0.6980392156862745
                      0.8431372549019608
                      1])
(defn text-selection-draw [font text [selection-start selection-end] selection-color]
  (let [

        jfont (get-java-font font)
        lines (clojure.string/split-lines text)

        frc (get-font-render-context)
        metrics (.getLineMetrics ^Font jfont text frc)
        line-height (double (.getHeight metrics))
        selection-height line-height

        text (str text "8")

        glyphs (.createGlyphVector jfont frc text)]
    (loop [x 0
           y 0
           selection-start selection-start
           selection-length (- selection-end selection-start)
           idx 0]
      (when (pos? selection-length)
        (let [c (nth text idx)
              glyph (.getGlyphMetrics glyphs idx)]
          (let [new-x (if (= c \newline)
                        0
                        (+ x (.getAdvanceX glyph)))
                new-y (if (= c \newline)
                        (+ y (dec line-height))
                        y)]
            (if (<= selection-start 0)
              (do
                (let [selection-width (if (= c \newline)
                                        5
                                        (- new-x x))]
                  (draw (ui/translate x (+ y (- line-height
                                                selection-height))
                                      (ui/filled-rectangle selection-color
                                                           selection-width selection-height))))
                (recur new-x new-y 0 (dec selection-length) (inc idx)))
              (recur new-x new-y (dec selection-start) selection-length (inc idx)))))))))

(extend-type membrane.ui.TextSelection
  IBounds
  (-bounds [this]
    (let [[maxx maxy] (text-bounds (get-java-font (:font this))
                                   (:text this))
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
      [maxx maxy]))

  IDraw
  (draw [this]
    (text-selection-draw
     (:font this)
     (:text this)
     (:selection this)
     selection-color)))

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

(extend-type membrane.ui.TextCursor
  IBounds
  (-bounds [this]
    (text-bounds (get-java-font (:font this)) (:text this)))

  IDraw
  (draw [this]
    (let [cursor (min (count (:text this)) (:cursor this))]
      (text-selection-draw (:font this) (str (:text this) "8") [cursor (inc cursor)]
                           [0.9 0.9 0.9]))
    ))
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



#_(defrecord Cached [drawable]
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

(defn index-for-position-line [frc font text px]
  (let [max-index (max 0 (dec (.length text)))
        chs (char-array (inc max-index))
        ;; fill chs
        _ (.getChars text 0 max-index chs 0)]
    (loop [index 0
           px px]
      (if (> index max-index)
        index
        (let [rect2d (.getStringBounds ^Font font chs index (inc index) frc)
              width (.getWidth rect2d)
              new-px (- px width)]
          (if (neg? new-px)
            index
            (recur (inc index)
                   new-px)))))))

(defn- index-for-position [font text px py]
  (assert (some? text) "can't find index for nil text")
  (let [frc (get-font-render-context)
        jfont (get-java-font font)
        metrics (.getLineMetrics ^Font jfont text frc)
        line-height (.getHeight metrics)

        line-no (loop [py py
                       line-no 0]
                  (if (> py line-height)
                    (recur (- py line-height)
                           (inc line-no))
                    line-no))
        lines (clojure.string/split-lines text)


        ]
    (if (>= line-no (count lines))
      (count text)
      (let [line (nth lines line-no)]
        (apply +
               line-no
               (index-for-position-line frc jfont line px)
               (map count (take line-no lines)))))))


(intern (the-ns 'membrane.ui) 'index-for-position index-for-position)

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

(defn printable? [c]
  (let [block (java.lang.Character$UnicodeBlock/of \backspace)]
    (and (not (Character/isISOControl c))
         (not= KeyEvent/CHAR_UNDEFINED c)
         (some? block)
         (not= block java.lang.Character$UnicodeBlock/SPECIALS))))


(defn- -key-typed [window e]
  (let [c (.getKeyChar e)
        ui @(:ui window)]
    (try
      (when (printable? c)
        (ui/key-press ui (str c)))
      (catch Exception e
        (println e)))))

(def keycodes
  {:unknown -1
   :grave_accent KeyEvent/VK_DEAD_GRAVE
   :escape KeyEvent/VK_ESCAPE 
   :enter KeyEvent/VK_ENTER
   :tab KeyEvent/VK_TAB
   :backspace KeyEvent/VK_BACK_SPACE
   :insert KeyEvent/VK_INSERT
   :delete KeyEvent/VK_DELETE
   :right KeyEvent/VK_RIGHT
   :left KeyEvent/VK_LEFT
   :down KeyEvent/VK_DOWN
   :up KeyEvent/VK_UP
   :page_up KeyEvent/VK_PAGE_UP
   :page_down KeyEvent/VK_PAGE_DOWN
   :home KeyEvent/VK_HOME
   :end KeyEvent/VK_END
   :caps_lock KeyEvent/VK_CAPS_LOCK
   :scroll_lock KeyEvent/VK_SCROLL_LOCK
   :num_lock KeyEvent/VK_NUM_LOCK
   :print_screen KeyEvent/VK_PRINTSCREEN 
   :pause KeyEvent/VK_PAUSE 
   :f1 KeyEvent/VK_F1
   :f2 KeyEvent/VK_F2
   :f3 KeyEvent/VK_F3
   :f4 KeyEvent/VK_F4
   :f5 KeyEvent/VK_F5
   :f6 KeyEvent/VK_F6
   :f7 KeyEvent/VK_F7
   :f8 KeyEvent/VK_F8
   :f9 KeyEvent/VK_F9
   :f10 KeyEvent/VK_F10
   :f11 KeyEvent/VK_F11
   :f12 KeyEvent/VK_F12
   :f13 KeyEvent/VK_F13
   :f14 KeyEvent/VK_F14
   :f15 KeyEvent/VK_F15
   :f16 KeyEvent/VK_F16
   :f17 KeyEvent/VK_F17
   :f18 KeyEvent/VK_F18
   :f19 KeyEvent/VK_F19
   :f20 KeyEvent/VK_F20
   :f21 KeyEvent/VK_F21
   :f22 KeyEvent/VK_F22
   :f23 KeyEvent/VK_F23
   :f24 KeyEvent/VK_F24
   :kp_0 KeyEvent/VK_NUMPAD0
   :kp_1 KeyEvent/VK_NUMPAD1
   :kp_2 KeyEvent/VK_NUMPAD2
   :kp_3 KeyEvent/VK_NUMPAD3
   :kp_4 KeyEvent/VK_NUMPAD4
   :kp_5 KeyEvent/VK_NUMPAD5
   :kp_6 KeyEvent/VK_NUMPAD6
   :kp_7 KeyEvent/VK_NUMPAD7
   :kp_8 KeyEvent/VK_NUMPAD8
   :kp_9 KeyEvent/VK_NUMPAD9
   :kp_decimal KeyEvent/VK_DECIMAL
   :kp_divide KeyEvent/VK_DIVIDE
   :kp_multiply KeyEvent/VK_MULTIPLY
   :kp_subtract KeyEvent/VK_SUBTRACT
   :kp_add KeyEvent/VK_ADD
   :kp_equal KeyEvent/VK_EQUALS
   :left_shift KeyEvent/VK_SHIFT
   :left_control KeyEvent/VK_CONTROL
   :left_alt KeyEvent/VK_ALT
   ;; :left_super 343
   :right_shift KeyEvent/VK_SHIFT
   :right_control KeyEvent/VK_CONTROL
   :right_alt KeyEvent/VK_ALT
   ;; :right_super 347
   :menu KeyEvent/VK_CONTEXT_MENU})
(def keymap (into {} (map (comp vec reverse) keycodes)))

(def key-action-map
  {1 :press
   2 :repeat
   3 :release})
(defn -key-pressed [window e]
  (let [action :press
        ui @(:ui window)
        mods (.getModifiers e)
        code (.getKeyCode e)
        key-char (.getKeyChar e)]
    (ui/key-event ui key code action mods)
    (let [k (get keymap code)]
      (when (keyword? k)
        (try
          (ui/key-press ui k)
          (catch Exception e
            (println e)))))))

(defn -key-released [window e]
  (let [action :release
        ui @(:ui window)
        mods (.getModifiers e)
        code (.getKeyCode e)
        key-char (.getKeyChar e)]
    (ui/key-event ui key code action mods)))



(defn -on-mouse-down [window e]
  (let [x (.getX ^MouseEvent e)
        y (.getY ^MouseEvent e)
        button (.getButton e)
        mouse-down? true]
    (try
      (membrane.ui/mouse-event @(:ui window) [x y] button mouse-down? nil)
      (catch Exception e
        (throw e)))))


(defn -on-mouse-up [window e]
  (let [x (.getX ^MouseEvent e)
        y (.getY ^MouseEvent e)
        button (.getButton e)
        mouse-down? false]
    (try
      (membrane.ui/mouse-event @(:ui window) [x y] button mouse-down? nil)
      (catch Exception e
        (throw e)))))


(defn -on-mouse-move [window e]
  (let [
        x (.getX ^MouseEvent e)
        y (.getY ^MouseEvent e)
        pos [x y]
        ]
        (try
          (doall (membrane.ui/mouse-move @(:ui window) pos))
          (doall (membrane.ui/mouse-move-global @(:ui window) pos))

          (catch Exception e
            (println e)))))



(defn make-panel [window]
  (let [render (:render window)]
    (proxy
        [Component]
        []
      (getPreferredSize []
        (let [[w h] (ui/bounds (render))]
          (Dimension. (int w) (int h))))
      (paint [g]
        (binding [*g* g
                  *image-cache* (atom {})]
          (let [to-render (swap! (:ui window)
                                 (fn [_]
                                   (render)))]
            (.setColor *g* Color/white)
            (.fillRect *g* 0 0 (.getWidth this) (.getHeight this))
            (.setColor *g* Color/black)
            (draw to-render)))))))



(defn make-uber-listener [window]
  (let [panel @(:panel window)]
    (reify

      KeyListener
      (keyPressed [this e]
        (-key-pressed window e)
        (.repaint panel))

      (keyReleased [this e]
        (-key-released window e)
        (.repaint panel))

      (keyTyped [this e]
        (-key-typed window e)
        (.repaint panel))

      MouseMotionListener
      (mouseMoved [this e]
        (-on-mouse-move window e)
        (.repaint panel))

      (mouseDragged [this e]
        (-on-mouse-move window e)
        (.repaint panel))

      MouseListener
      (mousePressed [this e]
        (-on-mouse-down window e)
        (.repaint panel)
        )

      (mouseReleased [this e]
        (-on-mouse-up window e)
        (.repaint panel))

      (mouseEntered [this e])

      (mouseExited [this e])

      (mouseClicked [this e]))))


(defn run
  ([make-ui]
   (run make-ui {}))
  ([make-ui {:keys [window-start-width
                    window-start-height
                    window-start-x
                    window-start-y] :as options}]
   (let [window {:window (atom nil)
                 :panel (atom nil)
                 :ui (atom nil)
                 :render make-ui}
         panel (doto (make-panel window)
                 (.setFocusable true))
         _ (reset! (:panel window)
                   panel)
         listener (make-uber-listener window)
         _ (doto panel
             (.addMouseListener listener)
             (.addMouseMotionListener listener)
             (.addKeyListener listener))
         f (doto (JFrame. "title")
             (.add panel)
             (.pack)
             (.show))]
     {::repaint (fn []
                  (.repaint ^java.awt.Component panel))})))

(defn -main [& args]
  (run ((requiring-resolve 'membrane.component/make-app)
        (requiring-resolve 'membrane.example.todo/todo-app)
        @(requiring-resolve 'membrane.example.todo/todo-state))))

(comment
  (do
    (require '[membrane.example.todo :as todo]
             '[membrane.component :as component
               :refer [defui]]
             '[membrane.basic-components :as basic])

    (run (component/make-app #'todo/todo-app todo/todo-state))))
