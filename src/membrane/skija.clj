(ns membrane.skija
  (:require
   [membrane.ui :as ui
    :refer [IBounds
            IChildren
            IOrigin
            origin
            translate
            bounds]]
   [net.n01se.clojure-jna :as jna])
  (:import com.sun.jna.Pointer
           com.sun.jna.Memory
           com.sun.jna.ptr.FloatByReference
           com.sun.jna.ptr.IntByReference
           com.sun.jna.IntegerType
           java.awt.image.BufferedImage)
  (:import
   java.nio.ByteBuffer
   [org.jetbrains.skija BackendRenderTarget Canvas ColorSpace DirectContext FramebufferFormat Paint Rect RRect Surface SurfaceColorFormat SurfaceOrigin FontMgr FontStyle Font Path PaintMode Data Image]
   [org.lwjgl.glfw Callbacks GLFW GLFWErrorCallback
    GLFWMouseButtonCallback
    GLFWKeyCallback
    GLFWCursorPosCallback
    GLFWScrollCallback
    GLFWFramebufferSizeCallback
    GLFWWindowRefreshCallback
    GLFWDropCallback
    GLFWCharCallback]
   [org.lwjgl.opengl GL GL11]
   [org.lwjgl.system MemoryUtil]))

;; (set! *warn-on-reflection* true)

(defn color [^long l]
  (.intValue (Long/valueOf l)))

(def *rect-color (atom (color 0xFFCC3333)))

(def ^:dynamic *paint* {})
(def ^:dynamic *canvas* {})
(def ^:dynamic *font-cache* (atom {}))
(def ^:dynamic *image-cache* nil)
(def ^:dynamic *draw-cache* nil)

(def keycodes
  {:unknown -1
   " " 32
   "'" 39
   "," 44
   "-" 45
   "." 46
   "/" 47
   "0" 48
   "1" 49
   "2" 50
   "3" 51
   "4" 52
   "5" 53
   "6" 54
   "7" 55
   "8" 56
   "9" 57
   ";" 59
   "=" 61
   "A" 65
   "B" 66
   "C" 67
   "D" 68
   "E" 69
   "F" 70
   "G" 71
   "H" 72
   "I" 73
   "J" 74
   "K" 75
   "L" 76
   "M" 77
   "N" 78
   "O" 79
   "P" 80
   "Q" 81
   "R" 82
   "S" 83
   "T" 84
   "U" 85
   "V" 86
   "W" 87
   "X" 88
   "Y" 89
   "Z" 90
   "[" 91
   "\\" 92
   "]" 93
   :grave_accent 96
   :world_1 161
   :world_2 162
   :escape 256
   :enter 257
   :tab 258
   :backspace 259
   :insert 260
   :delete 261
   :right 262
   :left 263
   :down 264
   :up 265
   :page_up 266
   :page_down 267
   :home 268
   :end 269
   :caps_lock 280
   :scroll_lock 281
   :num_lock 282
   :print_screen 283
   :pause 284
   :f1 290
   :f2 291
   :f3 292
   :f4 293
   :f5 294
   :f6 295
   :f7 296
   :f8 297
   :f9 298
   :f10 299
   :f11 300
   :f12 301
   :f13 302
   :f14 303
   :f15 304
   :f16 305
   :f17 306
   :f18 307
   :f19 308
   :f20 309
   :f21 310
   :f22 311
   :f23 312
   :f24 313
   :f25 314
   :kp_0 320
   :kp_1 321
   :kp_2 322
   :kp_3 323
   :kp_4 324
   :kp_5 325
   :kp_6 326
   :kp_7 327
   :kp_8 328
   :kp_9 329
   :kp_decimal 330
   :kp_divide 331
   :kp_multiply 332
   :kp_subtract 333
   :kp_add 334
   :kp_enter 335
   :kp_equal 336
   :left_shift 340
   :left_control 341
   :left_alt 342
   :left_super 343
   :right_shift 344
   :right_control 345
   :right_alt 346
   :right_super 347
   :menu 348})
(def keymap (into {} (map (comp vec reverse) keycodes)))

(defprotocol IDraw
  (draw [this]))

(ui/add-default-draw-impls! IDraw #'draw)


(defn get-font
  ([ui-font]
   (get-font (:name ui-font) (:size ui-font)))
  ([font-name font-size]
   (let [font-size (float (or font-size (:size ui/default-font)))
         key [font-name font-size]
         font
         (if-let [font (get @*font-cache* key)]
           font
           (let [font-face (.matchFamilyStyle (FontMgr/getDefault)
                                              font-name FontStyle/NORMAL)
                 font (Font. font-face font-size)]
             (swap! *font-cache* assoc key font)
             font))]
     font)))

#_(defn draw [^Canvas canvas]
  (let [paint (doto (Paint.) (.setColor @*rect-color))]
    (.translate canvas 320 240)
    (.rotate canvas (mod (/ (System/currentTimeMillis) 10) 360))
    (.drawRect canvas (Rect/makeXYWH -50 -50 100 100) paint)))

(defn display-scale [window]
  (let [x (make-array Float/TYPE 1)
        y (make-array Float/TYPE 1)]
    (GLFW/glfwGetWindowContentScale ^long window ^floats x ^floats y)
    [(first x) (first y)]))



(comment
  (reset! lwjgl.main/*rect-color (lwjgl.main/color 0xFF33CC33)))

(def void Void/TYPE)
(def main-class-loader @clojure.lang.Compiler/LOADER)



(deftype DispatchCallback [f]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (.setContextClassLoader (Thread/currentThread) main-class-loader)

    (import 'com.sun.jna.Native)
    ;; https://java-native-access.github.io/jna/4.2.1/com/sun/jna/Native.html#detach-boolean-
    ;; for some other info search https://java-native-access.github.io/jna/4.2.1/ for CallbackThreadInitializer

    ;; turning off detach here might give a performance benefit,
    ;; but more importantly, it prevents jna from spamming stdout
    ;; with "JNA: could not detach thread"
    (com.sun.jna.Native/detach false)
    (f)
    ;; need turn detach back on so that
    ;; we don't prevent the jvm exiting
    ;; now that we're done
    (com.sun.jna.Native/detach true)))








(extend-type membrane.ui.WithStrokeWidth
  IDraw
  (draw [this]
    (let [stroke-width (:stroke-width this)]
      (binding [*paint* (assoc *paint* ::stroke-width stroke-width)]
        (doseq [drawable (:drawables this)]
           (draw drawable))))))


(extend-type membrane.ui.WithStyle
  IDraw
  (draw [this]
    (let [style (:style this)]
      (binding [*paint* (assoc *paint* ::style style)]
        (doseq [drawable (:drawables this)]
          (draw drawable))))))

(defmacro save-canvas [& args]
  `(try
     (.save ^Canvas *canvas*)
     ~@args
     (finally
       (.restore ^Canvas *canvas*)

       )))
(defn unsigned-bit-shift-left-int
  {:inline (fn [x n] `(clojure.lang.Numbers/shiftLeftInt ~x ~n))}
  [x n]
  (clojure.lang.Numbers/shiftLeftInt x n))


(defn map->paint [m]
  (let [paint (Paint.)]
    (when-let [stroke-width (::stroke-width m)]
      (.setStrokeWidth paint (float stroke-width)))
    (when-let [style (::style m)]
      (.setMode paint (case style
                        :membrane.ui/style-fill PaintMode/FILL
                        :membrane.ui/style-stroke PaintMode/STROKE
                        :membrane.ui/style-stroke-and-fill PaintMode/STROKE_AND_FILL
                        ;; else
                        (assert false "Unknown Fill Style"))))
    (when-let [color (::color m)]
      (let [[r g b a] (case (count color)
                        3 [(nth color 0)
                           (nth color 1)
                           (nth color 2)
                           1]
                        4 color
                        ;; else
                        (assert false "Invalid color format"))]
        (.setColor paint (int (bit-or (unsigned-bit-shift-left-int (* 255 a) 24)
                                      (unsigned-bit-shift-left-int (int (* 255 r)) 16)
                                      (unsigned-bit-shift-left-int (int (* 255 g)) 8)
                                      (int (* 255 b)))))))
    paint))

(defn index-for-position-line [skija-font text px]
  (let [
        glyphs (.getStringGlyphs ^Font skija-font text)
        glyph-widths (.getWidths ^Font skija-font glyphs)
        glyph-count (alength glyphs)

        max-index (max 0 (dec (.length text)))
        chs (char-array (inc max-index))
        ;; fill chs
        _ (.getChars text 0 max-index chs 0)]
    (loop [index 0
           px px]
      (if (or (> index max-index)
              (not (< index glyph-count)))
        index
        (let [width (aget glyph-widths index)
              new-px (- px width)]
          (if (neg? new-px)
            index
            (recur (inc index)
                   new-px)))))))

(defn- index-for-position [font text px py]
  (assert (some? text) "can't find index for nil text")
  (let [skija-font (get-font font)
        line-spacing (.getSpacing ^Font skija-font)

        line-no (loop [py py
                       line-no 0]
                  (if (> py line-spacing)
                    (recur (- py line-spacing)
                           (inc line-no))
                    line-no))
        lines (clojure.string/split-lines text)]
    (if (>= line-no (count lines))
      (count text)
      (let [line (nth lines line-no)]
        (apply +
               ;; newlines
               line-no
               (index-for-position-line skija-font line px)
               (map count (take line-no lines)))))))


(intern (the-ns 'membrane.ui) 'index-for-position index-for-position)

(defn- label-draw [{:keys [text font] :as label}]
  (let [lines (clojure.string/split-lines text)
        skija-font (get-font font)
        line-spacing (.getSpacing ^Font skija-font)]

    (save-canvas
     (doseq [line lines
             :let [line-bytes (.getBytes ^String line "utf-8")]]
       (.translate ^Canvas *canvas* 0 line-spacing)
       ;; (.write ^Memory skia-buf 0 line-bytes 0 (alength ^bytes line-bytes))
       ;; (Skia/skia_next_line *skia-resource* font-ptr)
       (.drawString ^Canvas *canvas* line 0 0 skija-font (map->paint *paint*))
       ;;(Skia/skia_render_line *skia-resource* font-ptr skia-buf (alength line-bytes) (float 0) (float 0))
       ))))

(defn text-bounds [skija-font text]
  (let [lines (clojure.string/split text #"\n" -1)
        ;; metrics (.getLineMetrics ^Font font text frc)
        ;; line-height (.getHeight metrics)
        
        line-spacing (.getSpacing ^Font skija-font)
        
        widths (map (fn [line]
                      (.measureTextWidth ^Font skija-font line))
                    lines)
        maxx (reduce max 0 widths)
        maxy (* line-spacing
                (count lines))]
    [maxx maxy]))


(defrecord LabelRaw [text font]
  IBounds
  (-bounds [_]
    (let [[maxx maxy] (text-bounds (get-font font)
                                             text)]
      [maxx maxy]))

  IDraw
  (draw [this]
    (label-draw this)))

(def ^:dynamic *already-drawing* nil)

(defn- cached-draw [drawable]
  #_(draw drawable)
  (let [padding (float 5)]
    (if *already-drawing*
      (draw drawable)
      (let [[xscale yscale :as content-scale] [1 1] ;; @(:window-content-scale *window*)
            [img img-width img-height]
            (if-let [img-info (get @*draw-cache* [drawable content-scale *paint*])]
              img-info
              (do
                (let [[w h] (bounds drawable)
                      img-width (int (+ (* 2 padding) (max 0 w)))
                      img-height (int (+ (* 2 padding) (max 0 h)))
                      cpu-surface (Surface/makeRasterN32Premul (int (* xscale img-width))
                                                               (int (* yscale img-height)))
                      cpu-canvas (.getCanvas cpu-surface)
                      ;; resource (Skia/skia_offscreen_buffer *skia-resource*
                      ;;                                      (int (* xscale img-width))
                      ;;                                      (int (* yscale img-height)))
                      img (binding [*canvas* cpu-canvas
                                    *already-drawing* true]
                            (when (and (not= xscale 1)
                                       (not= yscale 1))
                              (.scale ^Canvas *canvas*  (float xscale) (float yscale))
                              ;;(Skia/skia_set_scale *skia-resource* (float xscale) (float yscale))
                              )
                            ;; (Skia/skia_translate *skia-resource* padding padding)
                            (.translate ^Canvas *canvas* padding padding)
                            (draw drawable)
                            (.makeImageSnapshot cpu-surface))
                      img-info [img img-width img-height]]
                  (swap! *draw-cache* assoc [drawable content-scale *paint*] img-info)
                  img-info)))]
        (save-canvas
         
         ;; (Skia/skia_translate *skia-resource* (float (- padding)) (float (- padding)))
         (.translate ^Canvas *canvas*  (float (- padding)) (float (- padding)))
         
         ;; (Skia/skia_draw_image_rect *skia-resource* img (float img-width) (float img-height))
         (.drawImageRect ^Canvas *canvas* img (Rect/makeWH img-width img-height) (map->paint *paint*) ))))))

(defrecord Cached [drawable]
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
    ;; (draw drawable)
    (cached-draw drawable)

    ))

(extend-type membrane.ui.Label
  IBounds
  (-bounds [this]
    (let [[maxx maxy] (text-bounds (get-font (:font this))
                                             (:text this))]
      [maxx maxy]))
  IDraw
  (draw [this]
    (draw (->Cached (LabelRaw. (:text this)
                               (:font this))))))


(defn get-image [img-path]
  (let [img
        (if-let [img (get @*image-cache* img-path)]
          img
          (let [data (Data/makeFromFileName img-path)
                img (Image/makeFromEncoded (.getBytes data))]
            (swap! *image-cache* assoc img-path img)
            img))]
    img))


(defn- image-draw [{:keys [image-path size opacity] :as image}]
  (when-let [img (get-image image-path)]
    (let [[w h] size]
      (let [paint (map->paint *paint*) ]
        (when opacity
          (.setColor paint (int (bit-and (unsigned-bit-shift-left-int (* 255 opacity) 24)
                                         (.getColor paint)))))
        (.drawImageRect ^Canvas *canvas* img (Rect/makeWH w h) paint)))))


(extend-type membrane.ui.Image
  IDraw
  (draw [this]
    (image-draw this)))

(extend-type membrane.ui.Translate
  IDraw
  (draw [this]
    (save-canvas
     (.translate ^Canvas *canvas* (float (:x this)) (float (:y this)))
     (draw (:drawable this)))))

(defn text-selection-draw [ui-font text [selection-start selection-end] selection-color]
  (let [
        skija-font (get-font ui-font)
        lines (clojure.string/split-lines text)

        line-spacing (.getSpacing ^Font skija-font)

        selection-height line-spacing

        text (str text "8")

        glyphs (.getStringGlyphs ^Font skija-font text)
        glyph-widths (.getWidths ^Font skija-font glyphs)
        glyph-count (alength glyphs)]
    (loop [x 0
           y 0
           selection-start selection-start
           selection-length (- selection-end selection-start)
           idx 0]
      (when (and (pos? selection-length)
                 (< idx glyph-count))
        (let [c (nth text idx)
              glyph-width (aget glyph-widths idx)]
          (let [new-x (if (= c \newline)
                        0
                        (+ x glyph-width))
                new-y (if (= c \newline)
                        (+ y line-spacing)
                        y)]
            (if (<= selection-start 0)
              (do
                (let [selection-width (if (= c \newline)
                                        5
                                        (- new-x x))]
                  (draw (ui/translate x (+ y (- line-spacing
                                                selection-height))
                                      (ui/filled-rectangle selection-color
                                                           selection-width selection-height))))
                (recur new-x new-y 0 (dec selection-length) (inc idx)))
              (recur new-x new-y (dec selection-start) selection-length (inc idx)))))))))

(def selection-color [0.6980392156862745
                      0.8431372549019608
                      1])

(extend-type membrane.ui.TextSelection
  IBounds
  (-bounds [this]
    (let [[maxx maxy] (text-bounds (get-font (:font this))
                                   (:text this))]
      [maxx maxy]))

  IDraw
  (draw [this]
    (text-selection-draw
     (:font this)
     (:text this)
     (:selection this)
     selection-color)))



(extend-type membrane.ui.TextCursor
  IBounds
  (-bounds [this]
    (let [[maxx maxy] (text-bounds (get-font (:font this))
                                             (:text this))]
      [maxx maxy]))

  IDraw
  (draw [this]
    (let [cursor (min (count (:text this)) (:cursor this))]
      (text-selection-draw (:font this) (str (:text this) "8") [cursor (inc cursor)]
                           [0.9 0.9 0.9]))))


(extend-type membrane.ui.Path
  IDraw
  (draw [this]
    (let []
      (when-let [points (seq (:points this))]
        (let [path (Path.)]
         (let [[x y] (first points)]
           (.moveTo ^Path path ^float x ^float y)
           (doseq [[x y] (next points)]
             (.lineTo ^Path path x y)))
         (.drawPath ^Canvas *canvas* path (map->paint *paint*)))))))

(extend-type membrane.ui.RoundedRectangle
  IDraw
  (draw [this]
    (.drawRRect ^Canvas *canvas* (RRect/makeXYWH 0 0
                                                 (:width this) (:height this)
                                                 (:border-radius this) (:border-radius this))
                (map->paint *paint*) )))


(extend-type membrane.ui.WithColor
  IDraw
  (draw [this]
    (let [color (:color this)]
      (binding [*paint* (assoc *paint* ::color color)]
        (doseq [drawable (:drawables this)]
          (draw drawable))))))

(extend-type membrane.ui.Scale
  IDraw
  (draw [this]
    (let [[sx sy] (:scalars this)]
      (save-canvas
       (.scale ^Canvas *canvas* (float sx) (float sy))
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


(defn scissor-draw [scissor-view]
  (save-canvas
   (let [[ox oy] (:offset scissor-view)
         [w h] (:bounds scissor-view)]
     (.clipRect ^Canvas *canvas* (Rect/makeXYWH ox oy w h))
     (draw (:drawable scissor-view)))))

(extend-type membrane.ui.ScissorView
  IDraw
  (draw [this]
      (scissor-draw this)))


(defn- scrollview-draw [scrollview]
  (draw
   (ui/->ScissorView [0 0]
                  (:bounds scrollview)
                  (let [[mx my] (:offset scrollview)]
                    (translate mx my (:drawable scrollview))))))

(extend-type membrane.ui.ScrollView
  IDraw
  (draw [this]
    (scrollview-draw this)))

(defn framebuffer-size-callback [f]
  (proxy [GLFWFramebufferSizeCallback] []
    (invoke [window width height]
      (f window width height))))

(defn key-callback [f]
  (proxy [GLFWKeyCallback] []
    (invoke [window, key, scancode, action, mods]
      (f window key scancode action mods))))
;; glfwSetKeyCallback(window, keyCallback = new GLFWKeyCallback() {
;;     @Override
;;     public void invoke (long window, int key, int scancode, int action, int mods) {

;;     }
;; });

(defn mouse-button-callback [f]
  (proxy [GLFWMouseButtonCallback] []
    (invoke [window button action mods]
      (f window button action mods))))

;; glfwSetMouseButtonCallback(window, mouseCallback = new GLFWMouseButtonCallback() {
;; 	@Override
;; 	public void invoke(long window, int button, int action, int mods) {

;; 	}
;; });


(defn cursor-pos-callback [f]
  (proxy [GLFWCursorPosCallback] []
    (invoke [window xpos ypos]
      (f window xpos ypos))))
;; glfwSetCursorPosCallback(window, posCallback = new GLFWCursorPosCallback() {
;; 	@Override
;; 	public void invoke(long window, double xpos, double ypos) {

;; 	}
;; });

(defn scroll-callback [f]
  (proxy [GLFWScrollCallback] []
    (invoke [window xoffset yoffset]
      (f window xoffset yoffset))))

;; glfwSetScrollCallback(window, scrollCallback = new GLFWScrollCallback() {
;; 	@Override
;; 	public void invoke(long window, double xoffset, double yoffset) {

;; 	}
;; });

(defn window-refresh-callback [f]
  (proxy [GLFWWindowRefreshCallback] []
    (invoke [window]
      (f window))))

(defn drop-callback [f]
  (proxy [GLFWDropCallback] []
    (invoke [window paths]
      (f window paths))))

(defn char-callback [f]
  (proxy [GLFWCharCallback] []
    (invoke [window codepoint]
      (f window codepoint))))

(defn- int->bytes [i]
  (-> (ByteBuffer/allocate 4)
      (.putInt i)
      (.array)))

(def key-action-map
  {1 :press
   2 :repeat
   3 :release})

(defn run* [view-fn & [{:keys [window-title
                               window-start-x
                               window-start-y
                               window-start-width
                               window-start-height]
                        :as options}]]
  (.set (GLFWErrorCallback/createPrint System/err))
  (GLFW/glfwInit)
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)
  (let [width  (int (or window-start-width  640))
        height (int (or window-start-height 480))
        window-title (if window-title
                       (do
                         (assert (string? window-title) "If window title is provided, it must be a string")
                         window-title)
                       "Membrane")
        window (GLFW/glfwCreateWindow width height window-title MemoryUtil/NULL MemoryUtil/NULL)]

    (when (or window-start-x window-start-y)
      (assert (and window-start-x window-start-y)
        "If window-start-x or window-start-y are provided, both must be provided.")
      (GLFW/glfwSetWindowPos window (int window-start-x) (int window-start-y)))

    (GLFW/glfwMakeContextCurrent window)
    (GLFW/glfwSwapInterval 1)
    (GLFW/glfwShowWindow window)  
    (GL/createCapabilities)
    (let [context (DirectContext/makeGL)
          fb-id   (GL11/glGetInteger 0x8CA6)
          [scale-x scale-y] (display-scale window)
          target  (BackendRenderTarget/makeGL (* scale-x width) (* scale-y height) 0 8 fb-id FramebufferFormat/GR_GL_RGBA8)
          surface (Surface/makeFromBackendRenderTarget context target SurfaceOrigin/BOTTOM_LEFT SurfaceColorFormat/RGBA_8888 (ColorSpace/getSRGB))
          canvas  (.getCanvas surface)
          view-atom (atom nil)
          last-draw (atom nil)
          mouse-position (atom [0 0])]
      (.scale canvas scale-x scale-y)
      
      (binding [*canvas* canvas
                *paint* {}
                *font-cache* (atom {})
                *draw-cache* (atom {})
                *image-cache* (atom {})
                ]
        (try
          (letfn [(repaint! []
                    (assert (identical? canvas *canvas*))
                    
                    (let [layer (.save canvas)

                          view (view-fn)
                          last-view @view-atom]
                      (when (not= view last-view)
                        (reset! view-atom view)
                        (.clear canvas (color 0xFFFFFFFF))
                        (draw view)
                        (.restoreToCount canvas layer)
                        (.flush context)
                        (GLFW/glfwSwapBuffers window)))
                    
                    )
                  (on-mouse-button [window button action mods]
                    (assert (identical? canvas *canvas*))
                    (try
                      (ui/mouse-event @view-atom @mouse-position button (= 1 action) mods)
                      (catch Exception e
                        (println e))))
                  (on-scroll [window offset-x offset-y]
                    (ui/scroll @view-atom [(* 2 offset-x) (* 2 offset-y)] @mouse-position)
                    (repaint!))
                  (on-framebuffer-size [window width height]
                    ;; should be reshaping here
                    )
                  (on-window-refresh [window]
                    (repaint!))

                  (on-drop [window paths]
                    (try
                      (ui/drop @view-atom (vec paths) @mouse-position)
                      (catch Exception e
                        (println e))))
                  (on-cursor-pos [window x y]
                    (try
                      (doall (ui/mouse-move @view-atom [x y]))
                      (doall (ui/mouse-move-global @view-atom [x y]))
                      (catch Exception e
                        (println e)))


                    (reset! mouse-position [(double x)
                                            (double y)])

                    (repaint!))

                  (on-key [window key scancode action mods]
                    (let [action (get key-action-map action :unknown)
                          ui @view-atom]
                      (ui/key-event ui key scancode action mods)
                      (cond

                        ;; paste
                        (and (= key 86)
                             (= action :press)
                             (= mods 8))
                        (let [nodes (->> (tree-seq (fn [n]
                                                     true)
                                                   ui/children
                                                   ui)
                                         (filter #(satisfies? ui/IClipboardPaste %)))]
                          (when-let [s (GLFW/glfwGetClipboardString window)]
                            (doseq [node nodes]
                              (ui/-clipboard-paste node s))))

                        ;; cut
                        (and (= key 88)
                             (= action :press)
                             (= mods 8))
                        (let [node (->> (tree-seq (fn [n]
                                                    true)
                                                  ui/children
                                                  ui)
                                        (filter #(satisfies? ui/IClipboardCut %))
                                        ;; maybe should be last?
                                        first)]
                          (when-let [s (ui/-clipboard-cut node)]
                            #_(GLFW/glfwSetClipboardString window s )))

                        ;; copy
                        (and (= key 67)
                             (= action :press)
                             (= mods 8))
                        (ui/clipboard-copy ui)

                        ;; special keys
                        (or (= :press action)
                            (= :repeat action))
                        (let [k (get keymap key)]
                          (when (keyword? k)
                            (try
                              (ui/key-press ui k)
                              (catch Exception e
                                (println e)))

                            ))
                        ))

                    (repaint!))

                  (on-char [window codepoint]
                    (let [k (String. ^bytes (int->bytes codepoint) "utf-32")
                          ui @view-atom]
                      (try
                        (ui/key-press ui k)
                        (catch Exception e
                          (println e))))

                    (repaint!))
                  ]
            (GLFW/glfwSetMouseButtonCallback window
                                             (mouse-button-callback
                                              on-mouse-button))
            (GLFW/glfwSetScrollCallback window
                                        (scroll-callback on-scroll))
            (GLFW/glfwSetFramebufferSizeCallback window
                                                 (framebuffer-size-callback
                                                  on-framebuffer-size))
            (GLFW/glfwSetWindowRefreshCallback window
                                               (window-refresh-callback
                                                on-window-refresh))
            (GLFW/glfwSetDropCallback window
                                      (drop-callback on-drop))
            (GLFW/glfwSetCursorPosCallback window
                                           (cursor-pos-callback on-cursor-pos))
            (GLFW/glfwSetKeyCallback window
                                     (key-callback on-key))
            (GLFW/glfwSetCharCallback window
                                      (char-callback on-char))
            (loop []
              (when (not (GLFW/glfwWindowShouldClose window))
                (repaint!)
                (GLFW/glfwPollEvents)
                (recur))))
          (catch Exception e
            (prn "crash in event loop" e))))

      (Callbacks/glfwFreeCallbacks window)
      (GLFW/glfwHideWindow window)
      (GLFW/glfwDestroyWindow window)
      (GLFW/glfwPollEvents)
      (GLFW/glfwTerminate)
      ;; (GLFW/glfwSetErrorCallback nil)

      )))

(def objlib (try
              (com.sun.jna.NativeLibrary/getInstance "CoreFoundation")
              (catch UnsatisfiedLinkError e
                    nil)))

(def main-queue (when objlib
                  (.getGlobalVariableAddress ^com.sun.jna.NativeLibrary objlib "_dispatch_main_q")))

(def dispatch_sync (when objlib
                     (.getFunction ^com.sun.jna.NativeLibrary objlib "dispatch_sync_f")))

(defonce callbacks (atom []))

(defn- dispatch-sync [f]
  (if (and main-queue dispatch_sync)
    (let [callback (DispatchCallback. f)
          args (to-array [main-queue nil callback])]
      (.invoke ^com.sun.jna.Function dispatch_sync void args)
      ;; please don't garbage collect me :D
      (identity args)
      nil)
    (f)))

(comment
  (.invoke dispatch_sync void (to-array [main-queue nil my-callback]))
  ,)

(defn my-view []
  (ui/filled-rectangle [1 0 0] 100 100))

(defn run
  "Open a window and call `view-fn` to draw. Returns when the window is closed.

  `view-fn` should be a 0 argument function that returns a view.
  `view-fn` will be called for every repaint.

  `options` is a map that can contain the following keys
  Optional parameters

  `window-title`: The string that appears in the title bar of the window.

  `window-start-width`: the starting width of the window
  `window-start-height`: the starting height of the window
  note: The window may be resized.

  `window-start-x`: the starting x coordinate of the top left corner of the window
  `window-start-y`: the starting y coordinate of the top left corner of the window
  note: The window may be moved.

  "
  ([view-fn] (run view-fn {}))
  ([view-fn options]
   (dispatch-sync #(run* view-fn options))))

(def counter-state (atom 0))
(defn counter-ui []
  (ui/on
   :mouse-down
   (fn [_]
     (prn "swapping some counter state")
     (swap! counter-state inc))
   (ui/label (str "count: " @counter-state))))

;; (require '[membrane.example.kitchen-sink :as ks])
(defn -main [& args]
  ;; (run #(ui/image "/Users/adrian/Documents/sketchup/cards/Fully+3D-printable+wind-up+car+gift+card/images/10a8da40fe773f83ac236135b685c141.png" [200 200] 0.5))

  (require '[membrane.example.todo :as todo-app])
  (require '[membrane.component :as component])

  ;; (run ((requiring-resolve 'membrane.component/make-app) #'ks/show-examples {:examples @ks/examples}))

  (run ((requiring-resolve 'membrane.component/make-app) (requiring-resolve 'membrane.example.todo/todo-app)
          {:todos
           [{:complete? false
             :description "first"}
            {:complete? false
             :description "second"}
            {:complete? true
             :description "third"}]
           :next-todo-text ""})
    {:window-title "Todo App"
     :window-start-width 600
     :window-start-height 700
     :window-start-x 50
     :window-start-y 100})
  (shutdown-agents)
  )
(comment
  (require '[membrane.example.todo :as todo-app])
  (require '[membrane.component :as component])

  (run (component/make-app #'todo-app/todo-app
                           {:todos
                            [{:complete? false
                              :description "first"}
                             {:complete? false
                              :description "second"}
                             {:complete? true
                              :description "third"}]
                            :next-todo-text ""}))


  

  ,)




