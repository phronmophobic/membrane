(ns membrane.skia
  (:require [net.n01se.clojure-jna :as jna]
            [clojure.core.async :refer [go
                                        put!
                                        chan
                                        <!
                                        timeout
                                        dropping-buffer
                                        promise-chan
                                        close!
                                        alts!
                                        thread]
             :as async]
            [membrane.ui :as ui
             :refer [IChildren
                     children
                     IBubble
                     -bubble
                     IKeyPress
                     -key-press
                     IKeyEvent
                     -key-event
                     IClipboardCut
                     -clipboard-cut
                     IClipboardCopy
                     -clipboard-copy
                     IClipboardPaste
                     -clipboard-paste
                     IBounds
                     bounds
                     IOrigin
                     origin
                     translate
                     mouse-event
                     mouse-move
                     mouse-move-global
                     IScroll
                     -scroll]]
            [membrane.toolkit :as tk])
  (:import com.sun.jna.Pointer
           com.sun.jna.Memory
           com.sun.jna.ptr.FloatByReference
           com.sun.jna.ptr.IntByReference
           com.sun.jna.IntegerType
           java.awt.image.BufferedImage
           java.util.function.Supplier
           java.lang.ref.Cleaner)
  (:import java.nio.ByteBuffer
           com.phronemophobic.membrane.Skia)
  (:gen-class))

(defmacro print-timing [& body]
  `(let [threadmx-bean# (java.lang.management.ManagementFactory/getThreadMXBean)
         before-time# (.getCurrentThreadUserTime threadmx-bean#)
         ret# (do
                ~@body)
         after-time# (.getCurrentThreadUserTime threadmx-bean#)]
     (println "timing " ~(str body)
              (/ (- after-time# before-time#)
                 1e6))
     ret#))

(def ^:private cleaner (delay (Cleaner/create)))
(def ^:private void Void/TYPE)
(def ^:private main-class-loader @clojure.lang.Compiler/LOADER)

(def ^:private
  opengl (try
           (com.sun.jna.NativeLibrary/getInstance "opengl")
           (catch java.lang.UnsatisfiedLinkError e
             (try
               (com.sun.jna.NativeLibrary/getInstance "GL")
               (catch java.lang.UnsatisfiedLinkError e
                 (try
                   (com.sun.jna.NativeLibrary/getInstance "OpenGL")
                   (catch java.lang.UnsatisfiedLinkError e
                     nil)))))))
(def ^:private
  objlib (try
           (com.sun.jna.NativeLibrary/getInstance "CoreFoundation")
           (catch java.lang.UnsatisfiedLinkError e
             nil)))

;; These libraries is absolutely necessary to show windows, but it's crashing the documentation generator
(def ^:private
  glfw (try
         (com.sun.jna.NativeLibrary/getInstance "glfw")
         (catch java.lang.UnsatisfiedLinkError e
           nil)))
(def ^:private
  membraneskialib (try
                    (com.sun.jna.NativeLibrary/getInstance "membraneskia")
                    (catch java.lang.UnsatisfiedLinkError e
                      nil)))

(def ffi-buf*
  (ThreadLocal/withInitial
   (reify
     Supplier
     (get [_]
       (Memory. 4096)))))
(defmacro ffi-buf []
  `^Memory (.get  ^ThreadLocal ffi-buf*))
(defmacro ffi-buf-size []
  `(.size (ffi-buf)))


(def ^:dynamic *paint* {})

(defprotocol IDraw
  :extend-via-metadata true
  (draw [this]))

(ui/add-default-draw-impls! IDraw #'draw)

(defmacro defc
  ([fn-name lib ret]
   `(defc ~fn-name ~lib ~ret []))
  ([fn-name lib ret args]
   (let [cfn-sym (with-meta (gensym "cfn") {:tag 'com.sun.jna.Function})]
     `(if ~lib
        (let [~cfn-sym (.getFunction ~(with-meta lib {:tag 'com.sun.jna.NativeLibrary})
                                     ~(name fn-name))]
          (defn- ~fn-name [~@args]
            (.invoke ~cfn-sym
                     ~ret (to-array [~@args]))))
        (defn- ~fn-name [~@args]
          (throw (Exception. (str ~(name fn-name) " not loaded."))))))))

(defmacro if-class
  ([class-name then]
   `(if-class ~class-name
      ~then
      nil))
  ([class-name then else?]
   `(try
      (Class/forName ~(name class-name))
      ~then
      (catch ClassNotFoundException e#
        ~else?))))

(defmacro defgl
  ([fn-name ret]
   `(defc ~fn-name opengl ~ret))
  ([fn-name ret args]
   `(defc ~fn-name opengl ~ret ~args)))
(defc glGetError opengl Integer/TYPE)
(defgl glViewport void [x y width height])
(defgl glClearStencil void [s])
(defgl glClear void [mask])


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

(def GL_UNPACK_ALIGNMENT (int 0x0CF5) )
(def GL_COLOR_BUFFER_BIT (int 0x00004000))
(def GL_STENCIL_BUFFER_BIT (int 0x00000400))
(def GLFW_VISIBLE (int 0x00020004))


(def GLFW_MOD_SHIFT 0x0001)
(def GLFW_MOD_CONTROL 0x0002)
(def GLFW_MOD_ALT 0x0004)
(def GLFW_MOD_SUPER 0x0008)
(def GLFW_MOD_CAPS_LOCK 0x0010)
(def GLFW_MOD_NUM_LOCK 0x0020)
(def GLFW_CONTEXT_VERSION_MAJOR (int 0x00022002))
(def GLFW_CONTEXT_VERSION_MINOR (int 0x00022003))
(def GLFW_OPENGL_PROFILE (int 0x00022008))
(def GLFW_OPENGL_CORE_PROFILE (int 0x00032001))
(def GLFW_OPENGL_FORWARD_COMPAT  (int 0x00022006))
(def GL_TRUE (int 1))


;; (defc demo_main freetype Integer/TYPE [argc argv])
(defc skia_load_image membraneskialib Pointer [path])
(defc skia_load_image_from_memory membraneskialib Pointer [buf buf-length])
(defc skia_draw_image membraneskialib void [skia-resource image-texture])
(defc skia_draw_image_rect membraneskialib void [skia-resource image-texture w h])

(defc skia_fork_pty membraneskialib Integer/TYPE [rows columns])
(defn- fork-pty [rows columns]
  (let [rows (short rows)
        columns (short columns)
        _ (assert (> rows 0) (str "invalid rows: " rows))
        _ (assert (> columns 0) (str "invalid columns: " columns))
        pty (Skia/skia_fork_pty rows columns)]
    (when (= -1 pty)
      (throw (Exception. "Unable to create pty.")))
    pty))

(def ^:dynamic *image-cache* (atom {}))
(def ^:dynamic *font-cache* (atom {}))
(def ^:dynamic *draw-cache* nil)
(def ^:dynamic *skia-resource* nil)
(def ^:dynamic *window* nil)

(def DEFAULT-COLOR [0.13 0.15 0.16 1])
(declare render-text)
(declare text-bounds)
(declare load-font)


(defc skia_save membraneskialib Void/TYPE [skia-resource])
(defc skia_restore membraneskialib Void/TYPE [skia-resource])
(defc skia_translate membraneskialib Void/TYPE [skia-resource x y])

(defn- test-skia []
  [(translate 0 10
              (ui/label "whoo "))

   (translate 100 100
              (let [s "wassup\nyo."
                    lbl (ui/label s)
                    [w h] (bounds lbl)]
                [(ui/rectangle w h)
                 lbl]
                 ))


   (translate 200.5 200.5
              (ui/rectangle 10 22))])
(comment
  (run #'test-skia))

(defmacro save-canvas [& args]
  `(try
     (Skia/skia_save *skia-resource*)
     ~@args
     (finally
       (Skia/skia_restore *skia-resource*))))

(defc skia_push_paint membraneskialib Void/TYPE [skia-resource])
(defc skia_pop_paint membraneskialib Void/TYPE [skia-resource])
(defmacro push-paint [& args]
  `(try
     (Skia/skia_push_paint *skia-resource*)
     ~@args
     (finally
       (Skia/skia_pop_paint *skia-resource*))))

(def skia-style {:membrane.ui/style-fill (byte 0)
                 :membrane.ui/style-stroke (byte 1)
                 :membrane.ui/style-stroke-and-fill (byte 2)})

(defc skia_set_style membraneskialib Void/TYPE [skia-resource style])
(defn- skia-set-style [skia-resource style]
  (let [style-arg (skia-style style)]
    (assert style-arg (str "Invalid Style: " style "."))
    (Skia/skia_set_style skia-resource style-arg)))

(defc skia_set_stroke_width membraneskialib Void/TYPE [skia-resource width])
(defn- skia-set-stroke-width [skia-resource width]
  (Skia/skia_set_stroke_width skia-resource (float width)))

(extend-type membrane.ui.WithStrokeWidth
    IDraw
    (draw [this]
      (let [stroke-width (:stroke-width this)]
        (binding [*paint* (assoc *paint* ::stroke-width stroke-width)]
          (push-paint
           (skia-set-stroke-width *skia-resource* stroke-width)
           (doseq [drawable (:drawables this)]
             (draw drawable)))))))


(extend-type membrane.ui.WithStyle
  IDraw
  (draw [this]
    (let [style (:style this)]
      (binding [*paint* (assoc *paint* ::style style)]
       (push-paint
        (skia-set-style *skia-resource* style)
        (doseq [drawable (:drawables this)]
          (draw drawable)))))))


(defc skia_set_color membraneskialib Void/TYPE [skia-resource r g b a])
(defn- skia-set-color [skia-resource [r g b a]]
  (Skia/skia_set_color skia-resource (float r) (float g) (float b) (if a
                                                                (float a)
                                                                (float 1))))

(defc skia_set_alpha membraneskialib Void/TYPE [skia-resource alpha])
(defn- skia-set-alpha [skia-resource alpha]
  (Skia/skia_set_alpha skia-resource (unchecked-byte (* alpha 255))))


(def font-dir "/System/Library/Fonts/")
(defn- get-font [font]
  (let [font-ptr
        (if-let [font-ptr (get @*font-cache* font)]
          font-ptr
          (let [font-name (or (:name font)
                              (:name ui/default-font))
                font-path (cond
                            (nil? font-name)
                            nil

                            (.startsWith ^String font-name "/")
                            font-name

                            (.exists (clojure.java.io/file font-dir font-name))
                            (.getCanonicalPath (clojure.java.io/file font-dir font-name))

                            :else font-name)]
            (let [font-size (or (:size font)
                                (:size ui/default-font))
                  font-ptr (load-font font-path font-size (:weight font) (:width font) (:slant font))]
              (swap! *font-cache* assoc font font-ptr)
              font-ptr)))]
    font-ptr))

(defc skia_font_family_name membraneskialib Void/TYPE [font family-name len])
(defn skia-font-family-name [font-ptr]
  (assert (instance? Pointer font-ptr))
  (let [buf (ffi-buf)]
    (skia_font_family_name font-ptr buf (.size ^Memory buf))
    (.getString ^Memory buf 0 "utf-8")))

(defn font-exists? [font]
  (let [font-ptr (get-font font)]
    (= (skia-font-family-name font-ptr)
       (:name font))))

(defn logical-font->font-family
  "Returns the font family for the given `logical-font`.

  `logical-font`: should be one of :monospace :serif :sans-serif"
  [logical-font]
  (let [skia-logical-font
        (case logical-font
          :serif "serif"
          :sans-serif "sans-serif"
          :monospace "monospace"
          nil)]
    (when skia-logical-font
      (skia-font-family-name
       (get-font (ui/font skia-logical-font 12))))))

(defc glGetError opengl Integer/TYPE)
(defc skia_render_line membraneskialib Void/TYPE [resource font-ptr line text-length x y])
(defc skia_next_line membraneskialib Void/TYPE [resource font-ptr])
(def byte-array-class (type (byte-array 0)))

(defn- label-draw [{:keys [text font] :as label}]
  (let [lines (clojure.string/split-lines text)
        font-ptr (get-font font)
        buf (ffi-buf)]
    (save-canvas
     (doseq [line lines
             :let [line-bytes (.getBytes ^String line "utf-8")
                   size (min (.size buf) (alength ^bytes line-bytes))]]
       (.write ^Memory buf 0 line-bytes 0 (int size))
       (Skia/skia_next_line *skia-resource* font-ptr)
       (Skia/skia_render_line *skia-resource* font-ptr buf size (float 0) (float 0))))))


(defrecord LabelRaw [text font]
    IBounds
    (-bounds [_]
        (let [[minx miny maxx maxy] (text-bounds (get-font font)
                                                 text)
              maxx (max 0 maxx)
              maxy (max 0 maxy)]
          [maxx maxy]))

    IDraw
  (draw [this]
      (label-draw this)))

(declare ->Cached rectangle)
(extend-type membrane.ui.Label
  IBounds
  (-bounds [this]
    (let [[minx miny maxx maxy] (text-bounds (get-font (:font this))
                                             (:text this))
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
      [maxx maxy]))
  IDraw
  (draw [this]
    (draw (->Cached (LabelRaw. (:text this)
                               (:font this))))))


(def kAlpha_8_SkColorType
  "pixel with alpha in 8-bit byte"
  (int 1))
(def kRGB_565_SkColorType
  "pixel with 5 bits red, 6 bits green, 5 bits blue, in 16-bit word"
  (int 2))(def kARGB_4444_SkColorType
  "pixel with 4 bits for alpha, red, green, blue; in 16-bit word"
  (int 3))(def kRGBA_8888_SkColorType
  "pixel with 8 bits for red, green, blue, alpha; in 32-bit word"
  (int 4))(def kRGB_888x_SkColorType
  "pixel with 8 bits each for red, green, blue; in 32-bit word"
  (int 5))(def kBGRA_8888_SkColorType
  "pixel with 8 bits for blue, green, red, alpha; in 32-bit word"
  (int 6))(def kRGBA_1010102_SkColorTyp
  "10 bits for red, green, blue; 2 bits for alpha; in 32-bit word"
  (int 7)  )
(def kBGRA_1010102_SkColorType
  "10 bits for blue, green, red; 2 bits for alpha; in 32-bit word"
  (int 8))
(def kRGB_101010x_SkColorType
  "pixel with 10 bits each for red, green, blue; in 32-bit word"
  (int 9))
(def kBGR_101010x_SkColorType
  "pixel with 10 bits each for blue, green, red; in 32-bit word"
  (int 10))
(def kBGR_101010x_XR_SkColorType
  "pixel with 10 bits each for blue, green, red; in 32-bit word, extended range"
  (int 11))
(def kGray_8_SkColorType
  "pixel with grayscale level in 8-bit byte"
  (int 12))
(def kRGBA_F16Norm_SkColorType
  "pixel with half floats in [0,1] for red, green, blue, alpha;"
  (int 13))
(def kRGBA_F16_SkColorType
  "pixel with half floats for red, green, blue, alpha;"
  (int 14))
(def kRGBA_F32_SkColorType
  "pixel using C float for red, green, blue, alpha; in 128-bit word"
  (int 15))
(def kR8G8_unorm_SkColorType
  "pixel with a uint8_t for red and green"
  (int 16))
(def kA16_float_SkColorType
  "pixel with a half float for alpha"
  (int 17))
(def kR16G16_float_SkColorType
  "pixel with a half float for red and green"
  (int 18))
(def kA16_unorm_SkColorType
  "pixel with a little endian uint16_t for alpha"
  (int 19))
(def kR16G16_unorm_SkColorType
  "pixel with a little endian uint16_t for red and green"
  (int 20))
(def kR16G16B16A16_unorm_SkColorType
  "pixel with a little endian uint16_t for red, green, blue"
  (int 21))
(def kSRGBA_8888_SkColorType (int 22))
(def kR8_unorm_SkColorType (int 23))

(def kOpaque_SkAlphaType
  "pixel is opaque"
  (int 1))
(def kPremul_SkAlphaType
  "pixel components are premultiplied by alpha"
  (int 2))
(def kUnpremul_SkAlphaType
  "pixel components are independent of alpha "
  (int 3))

(defc skia_draw_pixmap membraneskialib void [resource color-type alpha-type buffer width height row-bytes])

(defrecord Pixmap [id buf width height color-type alpha-type row-bytes]
  ui/IOrigin
    (-origin [_]
        [0 0])

    IDraw
    (draw [this]
      (skia_draw_pixmap *skia-resource* color-type alpha-type buf width height row-bytes))

    ui/IBounds
  (-bounds [_]
    [width height]))

(defn ^:private pixmap
  "Element for drawing raw pixel data. pixmap is a fairly low level level primitive."
  [id buf width height color-type alpha-type row-bytes]
  (->Pixmap id buf (int width) (int height) (int color-type) (int alpha-type) (int row-bytes)))

(defprotocol ImageFactory
  "gets or creates an opengl image texture given some various types"
  :extend-via-metadata true
  (get-image-texture [x]))

(extend-type String
  ImageFactory
  (get-image-texture [image-path]
    (if-let [image (get @*image-cache* image-path)]
      image
      (if (.exists (clojure.java.io/file image-path))
        (let [image (Skia/skia_load_image image-path)]
          (swap! *image-cache* assoc image-path image)
          image)
        (do
          (println image-path " does not exist!")
          nil)))))

(extend-type Pointer
  ImageFactory
  (get-image-texture [image-pointer]
    image-pointer))

(defn- slurp-bytes
  "Slurp the bytes from a slurpable thing"
  [x]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (clojure.java.io/copy (clojure.java.io/input-stream x) out)
    (.toByteArray out)))

(extend-type java.net.URL
  ImageFactory
  (get-image-texture [image-url]
    (if-let [image (get @*image-cache* image-url)]
      image
      (let [bytes (slurp-bytes image-url)
            image (Skia/skia_load_image_from_memory bytes (alength ^bytes bytes))]
        (swap! *image-cache* assoc image-url image)
        image))))

(extend (Class/forName "[B")
  ImageFactory
  {:get-image-texture
   (fn [^bytes bytes]
     (if-let [image (get @*image-cache* bytes)]
       image
       (let [image (Skia/skia_load_image_from_memory bytes (alength bytes))]
         (swap! *image-cache* assoc bytes image)
         image)))})

(defn- image-draw [{:keys [image-path size opacity] :as image}]
  (when-let [image-texture (get-image-texture image-path)]
    (let [[w h] size]
      (push-paint
       (when opacity
         (skia-set-alpha *skia-resource* opacity))
       (Skia/skia_draw_image_rect *skia-resource* image-texture (float w) (float h))))))


(extend-type membrane.ui.Image
  IDraw
  (draw [this]
    (image-draw this)))




(extend-type membrane.ui.Translate
  IDraw
  (draw [this]
    (save-canvas
     (Skia/skia_translate *skia-resource* (float (:x this)) (float (:y this)))
     (draw (:drawable this)))))


(extend-type membrane.ui.Rotate
  IDraw
  (draw [this]
    (save-canvas
     (Skia/skia_rotate *skia-resource* (float (:degrees this)))
     (draw (:drawable this)))))


(defrecord Transform [matrix drawable]
  IOrigin
  (-origin [this]
    [0 0])

  ui/IMakeNode
  (make-node [this childs]
    (assert (= (count childs) 1))
    (Transform. matrix (first childs)))

  IChildren
  (-children [this]
    [drawable])

  IBounds
  (-bounds [this]
    [0 0])

  IDraw
  (draw [this]
    (save-canvas
     (Skia/skia_transform *skia-resource*
                          (float (nth matrix 0))
                          (float (nth matrix 1))
                          (float (nth matrix 2))
                          (float (nth matrix 3))
                          (float (nth matrix 4))
                          (float (nth matrix 5)))
     (draw (:drawable this)))))
(defn transform [matrix drawable]
  (Transform. matrix drawable))

(defgl glPixelStorei void [pname param])

(defc skia_render_selection membraneskialib Void/TYPE [skia-resource font-ptr text text-length selection-start selection-end])
(defc skia_line_height membraneskialib Float/TYPE [font-ptr])
(defn skia-line-height [font]
  (skia_line_height (get-font font)))
(defn font-line-height [font]
  (skia-line-height font))

    ;; enum FontMetricsFlags {
    ;;     kUnderlineThicknessIsValid_Flag = 1 << 0, //!< set if fUnderlineThickness is valid
    ;;     kUnderlinePositionIsValid_Flag  = 1 << 1, //!< set if fUnderlinePosition is valid
    ;;     kStrikeoutThicknessIsValid_Flag = 1 << 2, //!< set if fStrikeoutThickness is valid
    ;;     kStrikeoutPositionIsValid_Flag  = 1 << 3, //!< set if fStrikeoutPosition is valid
    ;;     kBoundsInvalid_Flag             = 1 << 4, //!< set if fTop, fBottom, fXMin, fXMax invalid
    ;; };
(def ^:private kUnderlineThicknessIsValid_Flag
  "set if fUnderlineThickness is valid"
  (bit-shift-left 1 0))
(def ^:private kUnderlinePositionIsValid_Flag
  "set if fUnderlinePosition is valid"
  (bit-shift-left 1 1))
(def ^:private kStrikeoutThicknessIsValid_Flag
  "set if fStrikeoutThickness is valid"
  (bit-shift-left 1 2))
(def ^:private kStrikeoutPositionIsValid_Flag
  "set if fStrikeoutPosition is valid"
  (bit-shift-left 1 3))
(def ^:private kBoundsInvalid_Flag
  "set if fTop, fBottom, fXMin, fXMax invalid"
  (bit-shift-left 1 4))



;; See SkFontMetrics.h
;; uint32_t fFlags;              //!< FontMetricsFlags indicating which metrics are valid
;; SkScalar fTop;                //!< greatest extent above origin of any glyph bounding box, typically negative; deprecated with variable fonts
;; SkScalar fAscent;             //!< distance to reserve above baseline, typically negative
;; SkScalar fDescent;            //!< distance to reserve below baseline, typically positive
;; SkScalar fBottom;             //!< greatest extent below origin of any glyph bounding box, typically positive; deprecated with variable fonts
;; SkScalar fLeading;            //!< distance to add between lines, typically positive or zero
;; SkScalar fAvgCharWidth;       //!< average character width, zero if unknown
;; SkScalar fMaxCharWidth;       //!< maximum character width, zero if unknown
;; SkScalar fXMin;               //!< greatest extent to left of origin of any glyph bounding box, typically negative; deprecated with variable fonts
;; SkScalar fXMax;               //!< greatest extent to right of origin of any glyph bounding box, typically positive; deprecated with variable fonts
;; SkScalar fXHeight;            //!< height of lower-case 'x', zero if unknown, typically negative
;; SkScalar fCapHeight;          //!< height of an upper-case letter, zero if unknown, typically negative
;; SkScalar fUnderlineThickness; //!< underline thickness
;; SkScalar fUnderlinePosition;  //!< distance from baseline to top of stroke, typically positive
;; SkScalar fStrikeoutThickness; //!< strikeout thickness
;; SkScalar fStrikeoutPosition;  //!< distance from baseline to bottom of stroke, typically negative

(defc skia_font_metrics membraneskialib void [font-ptr
                                              fFlags
                                              fTop
                                              fAscent
                                              fDescent
                                              fBottom
                                              fLeading
                                              fAvgCharWidth
                                              fMaxCharWidth
                                              fXMin
                                              fXMax
                                              fXHeight
                                              fCapHeight
                                              fUnderlineThickness
                                              fUnderlinePosition
                                              fStrikeoutThickness
                                              fStrikeoutPosition
                                              ])
(defn font-metrics [font]
  (let [fFlags (IntByReference.)
        fTop (FloatByReference.)
        fAscent (FloatByReference.)
        fDescent (FloatByReference.)
        fBottom (FloatByReference.)
        fLeading (FloatByReference.)
        fAvgCharWidth (FloatByReference.)
        fMaxCharWidth (FloatByReference.)
        fXMin (FloatByReference.)
        fXMax (FloatByReference.)
        fXHeight (FloatByReference.)
        fCapHeight (FloatByReference.)
        fUnderlineThickness (FloatByReference.)
        fUnderlinePosition (FloatByReference.)
        fStrikeoutThickness (FloatByReference.)
        fStrikeoutPosition (FloatByReference.)]
    (skia_font_metrics (get-font font)
                       fFlags
                       fTop
                       fAscent
                       fDescent
                       fBottom
                       fLeading
                       fAvgCharWidth
                       fMaxCharWidth
                       fXMin
                       fXMax
                       fXHeight
                       fCapHeight
                       fUnderlineThickness
                       fUnderlinePosition
                       fStrikeoutThickness
                       fStrikeoutPosition)

    (let [flags (.getValue fFlags)]
      (merge
       (when (pos? (bit-and flags
                            kUnderlineThicknessIsValid_Flag))
         {:membrane.skia.font-metrics/underline-thickness (.getValue fUnderlineThickness)})
       (when (pos? (bit-and flags
                            kUnderlinePositionIsValid_Flag))
         {:membrane.skia.font-metrics/underline-position (.getValue fUnderlinePosition)})
       (when (pos? (bit-and flags
                            kStrikeoutThicknessIsValid_Flag))
         {:membrane.skia.font-metrics/strikeout-thickness (.getValue fStrikeoutThickness)})
       (when (pos? (bit-and flags
                            kStrikeoutPositionIsValid_Flag))
         {:membrane.skia.font-metrics/strikeout-position (.getValue fStrikeoutPosition)})
       (when (not (pos? (bit-and flags
                                 kBoundsInvalid_Flag)))
         {:membrane.skia.font-metrics/top (.getValue fTop)
          :membrane.skia.font-metrics/bottom (.getValue fBottom)
          :membrane.skia.font-metrics/xmin (.getValue fXMin)
          :membrane.skia.font-metrics/xmax (.getValue fXMax)})
       {:ascent (.getValue fAscent)
        :descent (.getValue fDescent)
        :leading (.getValue fLeading)
        :membrane.skia.font-metrics/avg-char-width (.getValue fAvgCharWidth)
        :membrane.skia.font-metrics/max-char-width (.getValue fMaxCharWidth)
        :membrane.skia.font-metrics/x-height (.getValue fXHeight)
        :membrane.skia.font-metrics/cap-height (.getValue fCapHeight)}))))

(defc skia_advance_x membraneskialib Float/TYPE [font-ptr text text-length])
(defn skia-advance-x [font text]
  (let [line-bytes (.getBytes ^String text "utf-8")
        buf (ffi-buf)]
    (.write ^Memory buf 0 line-bytes 0 (alength ^bytes line-bytes))
    (skia_advance_x (get-font font) buf (alength line-bytes))))
(defn font-advance-x [font text]
  (skia-advance-x font text))

(defn- text-selection-draw [{:keys [text font]
                            [selection-start selection-end] :selection
                            :as text-selection}]
  (let [font-ptr (get-font font)
        lines (clojure.string/split-lines text)
        buf (ffi-buf)]

    (save-canvas
     (loop [lines (seq lines)
            selection-start selection-start
            selection-end selection-end]
       (if (and lines (>= selection-end 0))
         (let [line (first lines)
               line-bytes (.getBytes ^String line "utf-8")
               line-count (count line)]
           (when (< selection-start line-count)
             (.write ^Memory buf 0 line-bytes 0 (alength ^bytes line-bytes))
             (Skia/skia_render_selection *skia-resource* font-ptr buf (alength line-bytes) (int (max 0 selection-start)) (int (min selection-end
                                                                                                                                        line-count))))
           (Skia/skia_next_line *skia-resource* font-ptr)
           (recur (next lines) (- selection-start line-count 1) (- selection-end line-count 1))))))))

(extend-type membrane.ui.TextSelection
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

(defc skia_render_cursor membraneskialib Void/TYPE [skia-resource font-ptr text text-length cursor])
(defn- text-cursor-draw [{:keys [text font cursor]
                         :as text-cursor}]
  (let [cursor (min (count text)
                    cursor)
        font-ptr (get-font font)
        lines (clojure.string/split-lines (str text " "))
        buf (ffi-buf)]
    (save-canvas
     (loop [lines (seq lines)
            cursor cursor]
       (if (and lines (>= cursor 0))
         ;; todo: we're doing extra work when not drawing a cursor
         (let [line (first lines)
               line-bytes (.getBytes ^String line "utf-8")
               line-count (count line)]
           (.write ^Memory buf 0 line-bytes 0 (alength ^bytes line-bytes))
           (when (< cursor (inc line-count))
             (Skia/skia_render_cursor *skia-resource* font-ptr buf (alength line-bytes) (int (max 0 cursor))))
           (Skia/skia_next_line *skia-resource* font-ptr)

           (recur (next lines) (- cursor line-count 1))))))))

(extend-type membrane.ui.TextCursor
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

(defc skia_draw_path membraneskialib Void/TYPE [skia-resource points points-length])
(extend-type membrane.ui.Path
  IDraw
  (draw [this]
    (let [points (:points this)
          buf (ffi-buf)]
      (loop [i 0
             points (seq points)]
        (when points
          (let [pt (first points)]
            (.setFloat ^Memory buf i (first pt))
            (.setFloat ^Memory buf (+ i 4) (second pt))
            (recur (+ i 8)
                   (next points)))))
      (push-paint
       (Skia/skia_draw_path *skia-resource* buf (* 2 (count points)))))))

(defc skia_draw_rounded_rect membraneskialib Void/TYPE [skia-resource w h radius])
(extend-type membrane.ui.RoundedRectangle
  IDraw
  (draw [this]
    (Skia/skia_draw_rounded_rect *skia-resource*
                            (float (:width this))
                            (float (:height this))
                            (float (:border-radius this)))))


(extend-type membrane.ui.WithColor
  IDraw
  (draw [this]
    (let [color (:color this)]
      (binding [*paint* (assoc *paint* ::color color)]
        (push-paint
         (skia-set-color *skia-resource* color)
         (doseq [drawable (:drawables this)]
           (draw drawable)))))))


(defc skia_set_scale membraneskialib Void/TYPE [skia-resource sx sy])
(extend-type membrane.ui.Scale
  IDraw
  (draw [this]
    (let [[sx sy] (:scalars this)]
      (save-canvas
       (Skia/skia_set_scale *skia-resource* (float sx) (float sy))
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

(defmacro ^:private add-cleaner [type p]
  (let [delete-sym (symbol (str "skia_" type "_delete"))]
      `(let [p# ~p
             ptr# (Pointer/nativeValue p#)]
         (.register ^Cleaner @cleaner p#
                    (fn []
                      (~delete-sym (Pointer. ptr#))))
         p#)))

(defc skia_SkStream_delete membraneskialib Void/TYPE [stream])
(defn- skia-SkStream-delete [stream]
  (assert (instance? Pointer stream))
  (skia_SkStream_delete stream))

(defc skia_SkStream_make_from_bytes membraneskialib Pointer [bs len])
(defn- skia-SkStream-make-from-bytes [^bytes bs]
  (assert (bytes? bs))
  (add-cleaner
   SkStream
   (skia_SkStream_make_from_bytes bs (int (alength bs)))))

(defc skia_SkStream_make_from_path membraneskialib Pointer [fname])
(defn- skia-SkStream-make-from-path [fname]
  (assert (string? fname))
  (add-cleaner
   SkStream
   (skia_SkStream_make_from_path fname)))

(defc skia_SkSVGDOM_delete membraneskialib Void/TYPE [svg])
(defn- skia-SkSVGDOM-delete [svg]
  (assert (instance? Pointer svg))
  (skia_SkSVGDOM_delete svg))

(defc skia_SkSVGDOM_make membraneskialib Pointer [stream])
(defn- skia-SkSVGDOM-make [stream]
  (assert (instance? Pointer stream))
  (add-cleaner
   SkSVGDOM
   (skia_SkSVGDOM_make stream)))

(defc skia_SkSVGDOM_render membraneskialib Pointer [svg resource])
(defn- skia-SkSVGDOM-render [svg resource]
  (assert (instance? Pointer svg))
  (assert (instance? Pointer resource))
  (skia_SkSVGDOM_render svg resource))

(defc skia_SkSVGDOM_instrinsic_size membraneskialib Pointer [svg width* height*])
(defn- skia-SkSVGDOM-instrinsic-size [svg]
  (assert (instance? Pointer svg))
  (let [width (FloatByReference.)
        height (FloatByReference.)]
    (skia_SkSVGDOM_instrinsic_size svg width height)
    [(.getValue width)
     (.getValue height)]))

(defprotocol SVGFactory
  "gets or creates an opengl image texture given some various types"
  :extend-via-metadata true
  (get-svg-dom [x]))

(extend-protocol SVGFactory
  String
  (get-svg-dom [svg-str]
    (let [bs (.getBytes svg-str "utf-8")
          stream (skia-SkStream-make-from-bytes bs)
          svg* (skia-SkSVGDOM-make stream)]
      svg*))

  java.io.File
  (get-svg-dom [fname]
    (let [stream (skia-SkStream-make-from-path (.getAbsolutePath fname))
          svg* (skia-SkSVGDOM-make stream)]
      svg*)))

(extend (Class/forName "[B")
  SVGFactory
  {:get-svg-dom
   (fn [^bytes bs]
     (let [stream (skia-SkStream-make-from-bytes bs)
           svg* (skia-SkSVGDOM-make stream)]
       svg*))})

(defn- load-svg [svg]
  (if-let [svg* (get @*image-cache* svg)]
    svg*
    (let [svg* (get-svg-dom svg)]
      (swap! *image-cache* assoc svg svg*)
      svg*)))

(defrecord SVG [svg]
  IOrigin
  (-origin [this]
    [0 0])

  IBounds
  (-bounds [this]
    (skia-SkSVGDOM-instrinsic-size (load-svg svg)))

  IDraw
  (draw [this]
    (skia-SkSVGDOM-render (load-svg svg) *skia-resource*)))

(defn svg
  "Displays an svg element.

  `svg` can be a string representation, a java.io.File, or a utf8-encoded byte array."
  [svg]
  (SVG. svg))

(comment
  (run
    (constantly
     (svg (.getBytes (slurp "/Users/adrian/Downloads/Clojure-Logo.wine.svg") "utf-8"))))
  ,)

(def ^:dynamic *origin* [0 0 0])
(def ^:dynamic *view* nil )

(defc skia_clip_rect membraneskialib Void/TYPE [skia-resource ox oy w h])

(defn- scissor-draw [scissor-view]
  (save-canvas
   (let [[ox oy] (:offset scissor-view)
         [w h] (:bounds scissor-view)]
     (Skia/skia_clip_rect *skia-resource* (float ox) (float oy) (float w) (float h))
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


(defn- wrap-text [text n]
  (loop [[k & text] text
         i 0
         line []
         lines []]
    (cond
     (nil? k)
     (map #(apply str %) (conj lines line))

     (= k \newline)
     (recur text 0 [] (conj lines line))

     (= i (dec n))
     (recur text 0 [] (conj lines (conj line k)))

     :else
     (recur text (inc i) (conj line k) lines)))

)

(declare vertical-layout horizontal-layout)


(def ^com.sun.jna.Function getClass
  (when objlib
    (.getFunction ^com.sun.jna.NativeLibrary objlib "objc_getClass")))
(def ^com.sun.jna.Function argv
  (when objlib
    (.getFunction ^com.sun.jna.NativeLibrary objlib "_NSGetArgv")))
(def ^com.sun.jna.Function argc
  (when objlib
    (.getFunction ^com.sun.jna.NativeLibrary objlib "_NSGetArgc")))


(try
  (defc skia_osx_run_on_main_thread_sync membraneskialib void [callback])
  (catch java.lang.UnsatisfiedLinkError e
    (def skia_osx_run_on_main_thread_sync nil)))

(deftype DispatchCallback [f]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  []))
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
    (try
      (com.sun.jna.Native/detach true)
      (catch IllegalStateException e
        nil))))



(defn- dispatch-sync! [f]
  (if-class com.apple.concurrent.Dispatch
    (.execute (.getBlockingMainQueueExecutor (eval '(com.apple.concurrent.Dispatch/getInstance)))
              f)
    (if skia_osx_run_on_main_thread_sync
      (let [callback (DispatchCallback. f)]
        (skia_osx_run_on_main_thread_sync callback)
        ;; please don't garbage collect me while i'm running
        (identity callback))
      (f)))
  nil)

;; (.invoke getClass Pointer   (to-array ["NSAutoreleasePool"]))

;; https://developer.apple.com/reference/objectivec/1657527-objective_c_runtime?language=objc
;; http://stackoverflow.com/questions/10289890/how-to-write-ios-app-purely-in-c

(defn- nsstring [s]
  (let [NSString (jna/invoke Pointer CoreFoundation/objc_getClass "NSString")
        sel (jna/invoke Pointer CoreFoundation/sel_registerName "stringWithUTF8String:")]
    (jna/invoke Pointer CoreFoundation/objc_msgSend NSString sel s)))

(defn- nsstring->str [nsstring]
  (let [sel (jna/invoke Pointer CoreFoundation/sel_registerName "UTF8String")]
    (jna/invoke String CoreFoundation/objc_msgSend nsstring sel)))

(defn- nsnumber->int [nsnumber]
  (let [sel (jna/invoke Pointer CoreFoundation/sel_registerName "intValue")]
    (jna/invoke Integer/TYPE CoreFoundation/objc_msgSend nsnumber sel))
  )

(defn- objc-selector [sel]
  (jna/invoke Pointer CoreFoundation/sel_registerName sel))


(defmacro def-objc-class [kls]
  `(let [result# (.invoke getClass Pointer (to-array [~(name kls)]))]
     (assert result# (str "No Class found for " ~(name kls)))
     (def ~kls result#)))
(defmacro objc-call [obj return-type sel & args]
  (let [sel-sym (gensym "sel_")]
    `(let [ ;; NSUserDefaults (.invoke getClass Pointer   (to-array ["NSUserDefaults"]))
           ~sel-sym (objc-selector ~sel)]
      ~(when (not= sel "respondsToSelector:")
         `(assert (= (char 1) (objc-call ~obj Character "respondsToSelector:" ~sel-sym))))
      (jna/invoke ~return-type CoreFoundation/objc_msgSend ~obj ~sel-sym ~@args)))
  )

(when objlib
  (def-objc-class NSUserDefaults)
  (def-objc-class NSNumber)
  (def-objc-class NSDictionary))

;; (objc-call standard-user-defaults Pointer "objectForKey:" (nsstring "ApplePressAndHoldEnabled"))
(defn- fix-press-and-hold! []
  (when objlib
    (let [defaults (objc-call NSDictionary Pointer "dictionaryWithObject:forKey:"
                              (objc-call NSNumber Pointer "numberWithBool:" (char 0))
                              (nsstring "ApplePressAndHoldEnabled"))
          standard-user-defaults (objc-call NSUserDefaults Pointer "standardUserDefaults")]
      (objc-call standard-user-defaults void "registerDefaults:" defaults))))




(defn- get-main-st []
  (let [threads (into-array Thread
                            (repeat  (.activeCount (.getThreadGroup (Thread/currentThread) )) nil))]
    (-> (.getThreadGroup (Thread/currentThread) )
        (.enumerate threads))
    (.getStackTrace (get threads 0))
    ))


(def messages (atom []))
(declare run-helper)




(defn- getpid []
  (jna/invoke Integer/TYPE c/getpid))

(defmacro glfw-call [ret fn-name & args]
  `(.invoke ^com.sun.jna.Function
            (.getFunction ^com.sun.jna.NativeLibrary glfw ~(name fn-name))
            ~ret
            (to-array (vector ~@args))))

(defn- glfw-post-empty-event []
  (glfw-call void glfwPostEmptyEvent))

(defmacro gl
  ([fn-name]
   `(gl ~fn-name []))
  ([fn-name args]
   `(jna/invoke void ~(symbol "opengl" (name fn-name)) ~@args)))


(declare sx sy)

(defc skia_text_bounds membraneskialib void [font-ptr text length minx miny maxx maxy])
(defn- text-bounds [font-ptr text]
  (assert (instance? Pointer font-ptr))
  (assert text "Can't get font size of nil text")
  
  (let [text-bytes (.getBytes ^String text "utf-8")
        x (FloatByReference.)
        y (FloatByReference.)
        width (FloatByReference.)
        height (FloatByReference.)]
    (skia_text_bounds font-ptr text-bytes (alength ^bytes text-bytes) x y width height)
    [(.getValue x)
     (.getValue y)
     (.getValue width)
     (.getValue height)
     ]))

(defc skia_index_for_position membraneskialib Integer/TYPE [font-ptr text text-length px])

(defn- index-for-position [font text px py]
  (assert (some? text) "can't find index for nil text")
  (let [font-ptr (get-font font)
        line-height (Skia/skia_line_height font-ptr)
        line-no (loop [py py
                       line-no 0]
               (if (> py line-height)
                 (recur (- py line-height)
                        (inc line-no))
                 line-no))
        lines (clojure.string/split-lines text)]
    (if (>= line-no (count lines))
      (count text)
      (let [line (.getBytes ^String (nth lines line-no) "utf-8")]
        (apply +
               line-no
               (skia_index_for_position font-ptr line (int (alength ^bytes line)) (float px))
               (map count (take line-no lines)))))))


(intern (the-ns 'membrane.ui) 'index-for-position index-for-position)

(defn- copy-to-clipboard [s]
  (let [glfw-window *window*
        window-handle (:window glfw-window)]
    ;; window-handle may be null
    (glfw-call void glfwSetClipboardString window-handle s)))
(intern (the-ns 'membrane.ui) 'copy-to-clipboard copy-to-clipboard)

(defc skia_image_bounds membraneskialib void [img width height])
(defn- image-size-raw [image]
  (let [tex (get-image-texture image)
        width (IntByReference.)
        height (IntByReference.)]
    (assert tex (format "Could not load texture for %s." image))
    (skia_image_bounds tex width height)
    [(.getValue width) (.getValue height)]))

(defonce
  swizzle-image-size
  (reset! membrane.ui/image-size* (memoize image-size-raw)))


(def font-slants
  {:upright 1,
   :italic 2,
   :oblique 3})
(def font-weights
  {:invisible 0
   :thin 100
   :extra-light 200
   :light 300
   :normal 400
   :medium 500
   :semi-bold 600
   :bold 700
   :extra-bold 800
   :black 900
   :extra-black 1000})
(def font-widths
  {:ultracondensed 1
   :extracondensed 2
   :condensed 3
   :semicondensed 4
   :normal 5
   :semiexpanded 6
   :expanded 7
   :extraexpanded 8
   :ultraexpanded 9})

(defc skia_load_font2 membraneskialib Pointer [font-path font-size])
(defn- load-font [path size weight width slant]
  (assert (or (string? path)
              (nil? path)))
  (let [weight (get font-weights weight
                    (or weight -1))
        width (get font-widths width
                   (or width -1))
        slant (get font-slants slant
                   (or slant -1))
        font-ptr (Skia/skia_load_font2 path (float size) (int weight) (int width) (int slant))]
    (assert font-ptr (str "unable to load font: " path " " size))

    font-ptr))

(def ^:dynamic *already-drawing* nil)


(defc skia_offscreen_buffer membraneskialib Pointer [skia-resource width height xscale yscale])
(defc skia_offscreen_image membraneskialib Pointer [skia-resource])
(defn- cached-draw [drawable]
  #_(draw drawable)
  (let [padding (float 5)]
    (if *already-drawing*
      (draw drawable)
      (let [[xscale yscale :as content-scale] @(:window-content-scale *window*)
            [img img-width img-height]
            (if-let [img-info (get @*draw-cache* [drawable content-scale *paint*])]
              img-info
              (do
                (let [[w h] (bounds drawable)
                      img-width (int (+ (* 2 padding) (max 0 w)))
                      img-height (int (+ (* 2 padding) (max 0 h)))
                      resource (Skia/skia_offscreen_buffer *skia-resource*
                                                           (int (* xscale img-width))
                                                           (int (* yscale img-height)))
                      img (binding [*skia-resource* resource
                                    *already-drawing* true]
                            (when (and (not= xscale 1)
                                       (not= yscale 1))
                              (Skia/skia_set_scale *skia-resource* (float xscale) (float yscale)))
                            (Skia/skia_translate *skia-resource* padding padding)
                            (draw drawable)
                            ;; todo: fix memory leak!
                            (Skia/skia_offscreen_image *skia-resource*))
                      img-info [img img-width img-height]]
                  (swap! *draw-cache* assoc [drawable content-scale *paint*] img-info)
                  img-info)))]
        (save-canvas
         (Skia/skia_translate *skia-resource* (float (- padding)) (float (- padding)))
         (Skia/skia_draw_image_rect *skia-resource* img (float img-width) (float img-height)))))))

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
    (cached-draw drawable)

    )
  )

(extend-type membrane.ui.Cached
    IDraw
    (draw [this]
      (cached-draw (:drawable this))))

(defn- get-framebuffer-size [window-handle]
  (let [pix-width (IntByReference.)
        pix-height (IntByReference.)]
    (glfw-call void glfwGetFramebufferSize window-handle pix-width pix-height)
    [(.getValue pix-width)
     (.getValue pix-height)]))

(defn- get-window-content-scale-size [window-handle]
  (let [xscale (FloatByReference.)
        yscale (FloatByReference.)]
    (glfw-call void glfwGetWindowContentScale window-handle xscale yscale)
    [(.getValue xscale)
     (.getValue yscale)]))

(defprotocol IWindow
  (init! [_])
  (reshape! [_ width height])
  (should-close? [_])
  (cleanup! [_])
  (repaint! [_]))

(def GLFW_GAMEPAD_BUTTON_A (int 0))
(def GLFW_GAMEPAD_BUTTON_B (int 1))
(def GLFW_GAMEPAD_BUTTON_X (int 2))
(def GLFW_GAMEPAD_BUTTON_Y (int 3))
(def GLFW_GAMEPAD_BUTTON_LEFT_BUMPER (int 4))
(def GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER (int 5))
(def GLFW_GAMEPAD_BUTTON_BACK (int 6))
(def GLFW_GAMEPAD_BUTTON_START (int 7))
(def GLFW_GAMEPAD_BUTTON_GUIDE (int 8))
(def GLFW_GAMEPAD_BUTTON_LEFT_THUMB (int 9))
(def GLFW_GAMEPAD_BUTTON_RIGHT_THUMB   (int 10))
(def GLFW_GAMEPAD_BUTTON_DPAD_UP   (int 11))
(def GLFW_GAMEPAD_BUTTON_DPAD_RIGHT   (int 12))
(def GLFW_GAMEPAD_BUTTON_DPAD_DOWN   (int 13))
(def GLFW_GAMEPAD_BUTTON_DPAD_LEFT   (int 14))
(def GLFW_GAMEPAD_BUTTON_LAST (int GLFW_GAMEPAD_BUTTON_DPAD_LEFT))
(def GLFW_GAMEPAD_BUTTON_CROSS (int GLFW_GAMEPAD_BUTTON_A))
(def GLFW_GAMEPAD_BUTTON_CIRCLE (int GLFW_GAMEPAD_BUTTON_B))
(def GLFW_GAMEPAD_BUTTON_SQUARE (int GLFW_GAMEPAD_BUTTON_X))
(def GLFW_GAMEPAD_BUTTON_TRIANGLE (int GLFW_GAMEPAD_BUTTON_Y))

(defc glfwJoystickPresent glfw Integer/TYPE [i])
(defc glfwSetJoystickCallback glfw Void/TYPE [callback])
(defc glfwJoystickIsGamepad glfw Integer/TYPE [jid])
(defc glfwGetGamepadName glfw String [jid])
(defc glfwGetGamepadState glfw Integer/TYPE [jid game-pad-state])

(def GLFW_CONNECTED (int 0x00040001))
(def GLFW_DISCONNECTED (int 0x00040002))

(deftype Joystickcallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Integer/TYPE Integer/TYPE]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (handler window
             ;; joystick id
             (aget args 0)
             ;; event. either GLFW_CONNECTED or GLFW_DISCONNECTED
             (aget args 1))
    nil))

(deftype WindowCloseCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)]
        (handler window (aget args 0)))
      (catch Exception e
        (println e)))
    nil))

(defn- make-window-close-callback [window handler]
  (->WindowCloseCallback window handler))

(defn -window-close-callback [window window-handle]
  nil)

(defn- -reshape
  ([window window-handle width height]
   (reshape! window width height)))

(deftype ReshapeCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Integer/TYPE Integer/TYPE]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)]
        (handler window (aget args 0) (aget args 1) (aget args 2) ))
      (catch Exception e
        (println e)))
    nil))

(defn- make-reshape-callback [window handler]
  (->ReshapeCallback window handler))


(defc glfwSetCursorEnterCallback glfw Pointer [window, cursor_enter_callback])
(defn- -mouse-enter-callback [window window-handle entered]
  (let [entered? (not (zero? entered))]
    (ui/mouse-enter-global @(:ui window)
                           entered?)

    (repaint! window)))

(deftype MouseEnterCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Integer/TYPE]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)]
        (handler window (aget args 0) (aget args 1)))
      (catch Exception e
        (println e)))
    nil))
(defn- make-mouse-enter-callback [window handler]
  (MouseEnterCallback. window handler))

;; Not fully implemented yet.
(defc glfwSetWindowFocusCallback glfw Pointer [window, window_focus_callback])
(defn- -window-focus-callback [window window-handle focused]
  (let [focused? (not (zero? focused))]
    #_(ui/window-focus-global @(:ui window)
                           entered?)

    (repaint! window)))

(deftype WindowFocusCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Integer/TYPE]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)]
        (handler window (aget args 0) (aget args 1)))
      (catch Exception e
        (println e)))
    nil))



(defn- -mouse-button-callback [window window-handle button action mods]
  (try
    (mouse-event @(:ui window) @(:mouse-position window) button (= 1 action) mods)
    (catch Exception e
      (println e)))

  (repaint! window))

(deftype MouseButtonCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Integer/TYPE Integer/TYPE Integer/TYPE]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)]
        (handler window (aget args 0) (aget args 1) (aget args 2) (aget args 3)))
      (catch Exception e
        (println e)))
    nil))

(defn- make-mouse-button-callback [window handler]
  (MouseButtonCallback. window handler))



(defn- -scroll-callback [window window-handle offset-x offset-y]
  ;; a 2x multiplier felt better. I think it might have something to do with
  ;; retina display, but it's probably some other dumb thing
  (ui/scroll @(:ui window) [(* 2 offset-x) (* 2 offset-y)] @(:mouse-position window))

  (repaint! window))

(deftype ScrollCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Double/TYPE Double/TYPE]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)]
        (handler window (aget args 0) (aget args 1) (aget args 2)))
      (catch Exception e
        (println e)))

    nil))

(defn- make-scroll-callback [window handler]
  (ScrollCallback. window handler))


(defn- -window-refresh-callback [window window-handle]
  (repaint! window))

(deftype WindowRefreshCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)]
        (handler window (aget args 0)))
      (catch Exception e
        (println e)))
    nil))

(defn- make-window-refresh-callback [window handler]
  (WindowRefreshCallback. window handler))


(defn- -drop-callback [window window-handle paths]
  (try
    (ui/drop @(:ui window) (vec paths) @(:mouse-position window))
    (catch Exception e
      (println e)))

  (repaint! window))

(deftype DropCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Integer Pointer]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)]
        (let [num-paths (aget args 1)
              string-pointers (aget args 2)
              paths (.getStringArray ^Pointer string-pointers  0 num-paths "utf-8")]
          (handler window (aget args 0) paths)))
      (catch Exception e
        (println e)))
    nil))

(defn- make-drop-callback [window handler]
  (DropCallback. window handler))


(defn- -cursor-pos-callback [window window-handle x y]
  (try
    (doall (mouse-move @(:ui window) [x y]))
    (doall (mouse-move-global @(:ui window) [x y]))
    (catch Exception e
      (println e)))


  (reset! (:mouse-position window) [(double x)
                                    (double y)])

  (repaint! window)

  )

(deftype CursorPosCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Double/TYPE Double/TYPE]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]

    (try
        (binding [*image-cache* (:image-cache window)
                  *font-cache* (:font-cache window)
                  *draw-cache* (:draw-cache window)]
          (handler window (aget args 0) (aget args 1) (aget args 2)))
        (catch Exception e
          (println e)))
    nil))

(defn- make-cursor-pos-callback [window handler]
  (CursorPosCallback. window handler))


(def key-action-map
  {1 :press
   2 :repeat
   0 :release})
(defn- -key-callback [window window-handle key scancode action mods]
  (let [action (get key-action-map action :unknown)
        ui @(:ui window)]
    (ui/key-event ui key scancode action mods)
    (cond

      ;; paste
      (and (= key 86)
           (= action :press)
           (= mods 8))
      (when-let [s (glfw-call String glfwGetClipboardString window-handle)]
        (ui/clipboard-paste ui s))

      ;; cut
      (and (= key 88)
           (= action :press)
           (= mods 8))
      (ui/clipboard-cut ui)

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

  ;; (repaint! window)
  nil)

(deftype KeyCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Integer/TYPE Integer/TYPE Integer/TYPE Integer/TYPE ]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)]
        (handler window (aget args 0) (aget args 1) (aget args 2) (aget args 3) (aget args 4)))
      (catch Exception e
        (println e)))
    nil))

(defn- make-key-callback [window handler]
  (KeyCallback. window handler))

(defn- int->bytes [i]
  (-> (ByteBuffer/allocate 4)
      (.putInt i)
      (.array)))

(defn- -character-callback [window window-handle codepoint]
  (let [k (String. ^bytes (int->bytes codepoint) "utf-32")
        ui @(:ui window)]
    (try
      (ui/key-press ui k)
      (catch Exception e
        (println e))))

  ;;(repaint! window)
  )

(deftype CharacterCallback [window handler]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Integer/TYPE]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)]
        (handler window (aget args 0) (aget args 1) ))
      (catch Exception e
        (println e)))
    nil))

(defn- make-character-callback [window handler]
  (CharacterCallback. window handler))

(def quit? (atom false))
(defc skia_init membraneskialib com.sun.jna.Pointer [])
(defc skia_init_cpu membraneskialib Pointer [width height])
(defc skia_reshape membraneskialib Void/TYPE [skia-resource fb-width fb-height xscale yscale])
(defc skia_cleanup membraneskialib Void/TYPE [skia-resource])
(defc skia_clear membraneskialib Void/TYPE [skia-resource])
(defc skia_flush membraneskialib Void/TYPE [skia-resource])

(defmacro with-cpu-skia-resource [resource-sym size & body]
  `(let [size# ~size
         ~resource-sym (Skia/skia_init_cpu (int (first size#)) (int (second size#)))]
     (try
       ~@body
       (finally
         (Skia/skia_cleanup ~resource-sym)))))

(def image-formats
  ;; need to recompile skia to include other formats
  { ;; ::image-format-bmp  (int 1)
   ;; ::image-format-gif  (int 2)
   ;; ::image-format-ico  (int 3)
   ::image-format-jpeg (int 4)
   ::image-format-png  (int 5)
   ;; ::image-format-wbmp (int 6)
   ::image-format-webp (int 7)
   ;; ::image-format-pkm  (int 8)
   ;; ::image-format-ktx  (int 9)
   ;; ::image-format-astc (int 10)
   ;; ::image-format-dng  (int 11)
   ;; ::image-format-heif (int 12)
   })
(defn guess-image-format [path]
  (let [period-index (.lastIndexOf ^String path ".")]
    (if (= -1 period-index)
      (get image-formats ::image-format-png)
      (let [suffix (clojure.string/lower-case (subs path (inc period-index)))]
        (case suffix
          "bmp"  ::image-format-bmp
          "gif"  ::image-format-gif
          "ico"  ::image-format-ico
          "jpeg" ::image-format-jpeg
          "jpg"  ::image-format-jpeg
          "png"  ::image-format-png
          "wbmp" ::image-format-wbmp
          "webp" ::image-format-webp
          "pkm"  ::image-format-pkm
          "ktx"  ::image-format-ktx
          "astc" ::image-format-astc
          "dng"  ::image-format-dng
          "heif" ::image-format-heif
          ::image-format-png)))))

(defc skia_save_image membraneskialib Integer/TYPE [skia-resource format quality path])



(defn save-image
  "Creates an image of elem. Returns true on success, false otherwise.

  `dest`: the filename to write the image to
  `elem`: the graphical element to draw
  `size`: the width and height of the image. If size is nil, the bounds and origin of elem will be used.
  `image-format`: The image format to use. Should be one of
   :membrane.skia/image-format-jpeg
   :membrane.skia/image-format-png
   :membrane.skia/image-format-webp
  if `image-format` is nil, then it will be guessed based on the dest's file extension.
  `quality`: specifies the image quality to use for lossy image formats like jpeg. defaults to 100
  `clear?`: Specifies if the canvas should be cleared before drawing. defaults to true.

  note: `save-image` does not take into account the content scale of your monitor. ie. if you
  have a retina display, the image will be lower resolution. if you'd like the same resolution
  as your retina display, you can do use `scale` like the following:
  `(skia/save-image \"out@2x.png\" (ui/scale 2 2 (ui/label \"hello world\")))`

  "
  ([dest elem]
   (save-image dest elem nil))
  ([dest elem [w h :as size]]
   (save-image dest elem size nil 100 true))
  ([dest elem [w h :as size] image-format quality clear?]
   (let [size (if size
                size
                (let [[w h] (bounds elem)
                      [ox oy] (origin elem)]
                  [(+ w ox)
                   (+ h oy)]))
         _ (assert (and (pos? (first size))
                        (pos? (second size)))
                   "Size must be two positive numbers [w h]")
         image-format (if image-format
                        image-format
                        (guess-image-format dest))
         image-format-native (if-let [fmt (get image-formats image-format)]
                               fmt
                               (throw
                                (IllegalArgumentException.
                                 (str "Image format must be one of " (keys image-formats)))))]
    (with-cpu-skia-resource skia-resource size
      (binding [*skia-resource* skia-resource
                *image-cache* (atom {})
                *already-drawing* true]
        (when clear?
          (Skia/skia_clear skia-resource))
        (draw elem))
      (Skia/skia_save_image skia-resource
                       image-format-native
                       quality
                       dest)))))

(defn draw-to-image!
  "DEPRECATED: use `save-image` instead.

  Creates an image of elem. Returns true on success, false otherwise.

  `path`: the filename to write the image to
  `elem`: the graphical element to draw
  `size`: the width and height of the image. If size is nil, the bounds and origin of elem will be used.
  `image-format`: The image format to use. Should be one of
   :membrane.skia/image-format-jpeg
   :membrane.skia/image-format-png
   :membrane.skia/image-format-webp
  if `image-format` is nil, then it will be guessed based on the path's file extension.
  `quality`: specifies the image quality to use for lossy image formats like jpeg. defaults to 100
  `clear?`: Specifies if the canvas should be cleared before drawing. defaults to true.

  note: `draw-to-image!` does not take into account the content scale of your monitor. ie. if you
  have a retina display, the image will be lower resolution. if you'd like the same resolution
  as your retina display, you can do use `scale` like the following:
  `(skia/draw-to-image! \"out@2x.png\" (ui/scale 2 2 (ui/label \"hello world\")))`

  "
  ([path elem]
   (draw-to-image! path elem nil))
  ([path elem [w h :as size]]
   (draw-to-image! path elem size nil 100 true))
  ([path elem [w h :as size] image-format quality clear?]
   (let [size (if size
                size
                (let [[w h] (bounds elem)
                      [ox oy] (origin elem)]
                  [(+ w ox)
                   (+ h oy)]))
         _ (assert (and (pos? (first size))
                        (pos? (second size)))
                   "Size must be two positive numbers [w h]")
         image-format (if image-format
                        image-format
                        (guess-image-format path))
         image-format-native (if-let [fmt (get image-formats image-format)]
                               fmt
                               (throw
                                (IllegalArgumentException.
                                 (str "Image format must be one of " (keys image-formats)))))]
    (with-cpu-skia-resource skia-resource size
      (binding [*skia-resource* skia-resource
                *image-cache* (atom {})
                *already-drawing* true]
        (when clear?
          (Skia/skia_clear skia-resource))
        (draw elem))
      (Skia/skia_save_image skia-resource
                       image-format-native
                       quality
                       path)))))

(defrecord GlfwSkiaWindow [view-fn window handlers callbacks ui mouse-position skia-resource image-cache font-cache draw-cache window-content-scale window-start-width window-start-height window-start-x window-start-y window-title window-size]
  IWindow
  (init! [this]
    (let [window-width (int (or window-start-width 787))
          window-height (int (or window-start-height 1000))
          window-x (int (or window-start-x 0))
          window-y (int (or window-start-y 0))

          window-title (if window-title
                         (do
                           (assert (string? window-title) "If window title is provided, it must be a string")
                           window-title)
                         "Membrane")
          window (glfw-call Pointer
                            glfwCreateWindow
                            window-width
                            window-height
                            window-title
                            com.sun.jna.Pointer/NULL
                            com.sun.jna.Pointer/NULL)
          this
          (assoc this
                 :window window
                 :image-cache (atom {})
                 :font-cache (atom {})
                 :draw-cache (atom {})
                 :ui (atom nil)
                 :mouse-position (atom [0 0])
                 :window-content-scale (atom [1 1])
                 :window-size (atom nil)
                 :skia-resource (Skia/skia_init))
          drop-callback (make-drop-callback this (get handlers :drop -drop-callback))
          key-callback (make-key-callback this (get handlers :key -key-callback))
          character-callback (make-character-callback this (get handlers :char -character-callback))
          mouse-button-callback (make-mouse-button-callback this (get handlers :mouse-button -mouse-button-callback))
          reshape-callback (make-reshape-callback this (get handlers :reshape -reshape))
          scroll-callback (make-scroll-callback this (get handlers :scroll -scroll-callback))
          window-refresh-callback (make-window-refresh-callback this (get handlers :refresh -window-refresh-callback))
          cursor-pos-callback (make-cursor-pos-callback this (get handlers :cursor -cursor-pos-callback))
          window-close-callback (make-window-close-callback this (get handlers :window-close -window-close-callback))
          ;; mouse-enter-callback (make-mouse-enter-callback this (get handlers :mouse-enter -mouse-enter-callback))
          ]

      (let [m (Memory. 8)
            error (glfw-call Integer/TYPE glfwGetError m)]
        (when (not (zero? error))
          (let [s (.getPointer m 0)]
            (println "error description: " (.getString s 0) ))))

      (glfw-call Void/TYPE glfwMakeContextCurrent window)

      (glPixelStorei GL_UNPACK_ALIGNMENT, (int 1)) ;

      ;; Setting swap interval to 1 is probably the right thing, but currently, the way it blocks
      ;; the event thread messes everything up.
      ;; (glfw-call void glfwSwapInterval 1)

      (glfw-call Pointer glfwSetDropCallback window, drop-callback)
      (glfw-call Pointer glfwSetCursorPosCallback window, cursor-pos-callback)
      (glfw-call Pointer glfwSetKeyCallback window key-callback)
      (glfw-call Pointer glfwSetCharCallback window character-callback)
      (glfw-call Pointer glfwSetMouseButtonCallback window mouse-button-callback)
      (glfw-call Pointer glfwSetFramebufferSizeCallback window reshape-callback)
      (glfw-call Pointer glfwSetScrollCallback window scroll-callback)
      (glfw-call Pointer glfwSetWindowRefreshCallback window window-refresh-callback)
      (glfw-call Pointer glfwSetWindowCloseCallback window window-close-callback)
      ;; (glfw-call Pointer glfwSetCursorEnterCallback window mouse-enter-callback)

      (glfw-call void glfwSetWindowPos window window-x window-y)

      ;; reshape must be called before glfw show window
      ;; so that we have the right size buffers set up
      (reshape! this window-width window-height)
      (glfw-call void glfwShowWindow window)

      (doto (assoc this
                   ;; need to hang on to callbacks so they don't get garbage collected!
                   :callbacks
                   [key-callback
                    drop-callback
                    character-callback
                    mouse-button-callback
                    reshape-callback
                    scroll-callback
                    window-refresh-callback
                    cursor-pos-callback
                    window-close-callback
                    ;; mouse-enter-callback
                    ]))))

  (reshape! [_ width height]
    (glfw-call Void/TYPE glfwMakeContextCurrent window)
    
    (glViewport (int 0) (int 0) width height)
    (glClearStencil (int 0))
    (glClear (bit-or GL_COLOR_BUFFER_BIT
                     GL_STENCIL_BUFFER_BIT))

    ;; there's some issue with caching when drawing text that's offscreen
    ;; when using gpu renderer in skia.cpp.
    ;; currently using cpu renderer which fixes the issue.
    ;; it's unclear which method should be preferred or what the
    ;; performance implications are.
    ;;
    ;; simply resetting cache on reshape also fixes the issue,
    ;; but causes the window to be drawn black while a window
    ;; is being resized.
    ;; (reset! draw-cache {})

    (let [[xscale yscale :as content-scale] (get-window-content-scale-size window)
          [fb-width fb-height] (get-framebuffer-size window)]
      (reset! window-content-scale content-scale)
      (reset! window-size [(int (/ fb-width xscale))
                           (int (/ fb-height yscale))])
      ;; force repaint
      (reset! ui nil)
      (Skia/skia_reshape skia-resource fb-width fb-height xscale yscale))

    nil)
  
  (should-close? [_]
    (when window
      (glfw-call Boolean/TYPE glfwWindowShouldClose window)))
  (cleanup! [this]
    (Skia/skia_cleanup skia-resource)
    (glfw-call void glfwDestroyWindow window)
    (assoc this
           :window nil
           :callbacks nil
           :mouse-position nil
           :image-cache nil
           :font-cache nil
           :draw-cache nil
           :ui nil
           :window-content-scale nil
           :skia-resource nil))


  (repaint! [this]
    (binding [*image-cache* image-cache
              *font-cache* font-cache
              *window* this
              *draw-cache* draw-cache
              *skia-resource* skia-resource]
      (let [container-info {:container-size @window-size
                            :content-scale @window-content-scale
                            :container this}
            [last-view view] (reset-vals! ui
                                          (view-fn container-info))]

        ;; TODO: should try to implement
        ;; Yes, that's fine.  Another common approach is to record the entire scene normally as an SkPicture, and just play it back into each tile, clipped and translated as appropriate.
        ;; This approach works best if you use SkRTreeFactory when calling beginRecording()... that'll build an R-tree to help us skip issuing draws that fall outside each tile.

        (when (not= view last-view)
          (glfw-call Void/TYPE glfwMakeContextCurrent window)

          (Skia/skia_clear skia-resource)
          (draw view)
          (Skia/skia_flush skia-resource)
          (glfw-call Void/TYPE glfwSwapBuffers window))))))

(defonce window-chan (chan 1))

(defn run-sync
  "Open a window and call `view-fn` to draw. Returns when the window is closed.

  `view-fn` should be a 0 argument function that returns an object satisfying `IDraw`.
  `view-fn` will be called for every repaint. Repaints occur on every event. You can also trigger a repaint by calling `glfw-post-empty-event`.

  `options` is a map that can contain the following keys
  Optional parameters

  `window-start-width`: the starting width of the window
  `window-start-height`: the starting height of the window
  note: The window may be resized.

  `window-start-x`: the starting x coordinate of the top left corner of the window
  `window-start-y`: the starting y coordinate of the top left corner of the window
  note: The window may be moved.

  `handlers`: A map of callback backs for glfw events
  The events correspond to the available glfw events. If no `handlers` map is provided, then the defaults are used.
  If a handlers key is provided, it does not replace the defaults, but get merged into the defaults.

  available handler events
  :key args are [window window-handle key scancode action mods]. default is -key-callback.
  :char args are [window window-handle codepoint]. default is -character-callback.
  :mouse-button args are [window window-handle button action mods]. default is -mouse-button-callback.
  :reshape args are [window window-handle width height]. default is -reshape.
  :scroll args are [window window-handle offset-x offset-y]. default is -scroll-callback.
  :refresh args are [window window-handle]. default is -window-refresh-callback.
  :cursor args are [window window-handle x y]. default is -cursor-pos-callback.
  :mouse-enter args are [window window-handle entered]. default is -mouse-enter-callback.
  :window-close args are [window window-handle]. default is -window-close-callback.

  For each handler, `window` is the GlfwSkiaWindow and window-handle is a jna pointer to the glfw pointer.
  
  "
  ([view-fn]
   (run-sync view-fn {}))
  ([view-fn {:keys [window-start-width
                    window-start-height
                    window-start-x
                    window-start-y
                    handlers] :as options}]
   (assert glfw "Could not run because glfw could not be loaded.")
   (assert membraneskialib "Could not run because membraneskia could not be loaded.")

   (async/>!! window-chan (map->GlfwSkiaWindow (merge
                                                {:view-fn
                                                 (if (:include-container-info options)
                                                   view-fn
                                                   (fn [_] (view-fn)))}
                                                options)))

   (dispatch-sync!
       (fn []
         (try
           (run-helper window-chan
                       (:membrane.skia/on-main options))
           (catch Exception e
             (println e)))))))

(defn run
  "Open a window and call `view-fn` to draw. Returns a channel that is closed when the window is closed.

  `view-fn` should be a 0 argument function that returns an object satisfying `IDraw`.
  `view-fn` will be called for every repaint. Repaints occur on every event. You can also trigger a repaint by calling `glfw-post-empty-event`.

  `options` is a map that can contain the following keys
  Optional parameters

  `window-start-width`: the starting width of the window
  `window-start-height`: the starting height of the window
  note: The window may be resized.

  `window-start-x`: the starting x coordinate of the top left corner of the window
  `window-start-y`: the starting y coordinate of the top left corner of the window
  note: The window may be moved.

  `handlers`: A map of callback backs for glfw events
  The events correspond to the available glfw events. If no `handlers` map is provided, then the defaults are used.
  If a handlers key is provided, it does not replace the defaults, but get merged into the defaults.

  available handler events
  :key args are [window window-handle key scancode action mods]. default is -key-callback.
  :char args are [window window-handle codepoint]. default is -character-callback.
  :mouse-button args are [window window-handle button action mods]. default is -mouse-button-callback.
  :reshape args are [window window-handle width height]. default is -reshape.
  :scroll args are [window window-handle offset-x offset-y]. default is -scroll-callback.
  :refresh args are [window window-handle]. default is -window-refresh-callback.
  :cursor args are [window window-handle x y]. default is -cursor-pos-callback.

  For each handler, `window` is the GlfwSkiaWindow and window-handle is a jna pointer to the glfw pointer.
  
  "
  ([view-fn]
   (run view-fn {}))
  ([view-fn {:keys [window-start-width
                    window-start-height
                    window-start-x
                    window-start-y
                    handlers] :as options}]
   (assert glfw "Could not run because glfw could not be loaded.")
   (assert membraneskialib "Could not run because membraneskia could not be loaded.")

   (async/thread
     (run-sync view-fn options))

   {::repaint glfw-post-empty-event}))

(defn- run-helper [window-chan on-main]
  (with-local-vars [windows #{}]
    (letfn [(init []
              (if (not= 1 (glfw-call Integer/TYPE glfwInit))
                false
                (do
                  (.setContextClassLoader (Thread/currentThread) main-class-loader)
                  (fix-press-and-hold!)
                  ;; (glfw-call void glfwWindowHint GLFW_COCOA_RETINA_FRAMEBUFFER (int 0))

                  ;; only call on macosx
                  (when objlib
                    (glfw-call void glfwWindowHint GLFW_CONTEXT_VERSION_MAJOR (int 3))
                    (glfw-call void glfwWindowHint GLFW_CONTEXT_VERSION_MINOR (int 2))
                    ;; 3.2+ only
                    (glfw-call void glfwWindowHint GLFW_OPENGL_PROFILE GLFW_OPENGL_CORE_PROFILE)
                    ;; Required on Mac
                    (glfw-call void glfwWindowHint GLFW_OPENGL_FORWARD_COMPAT GL_TRUE))

                  (glfw-call void glfwWindowHint GLFW_VISIBLE (int 0))

                  true)))
            (add-windows! []
              (loop [window (async/poll! window-chan)]
                (when window
                  (var-set windows (conj (var-get windows) (init! window)))
                  (recur (async/poll! window-chan)))))
            (wait-events []
              (glfw-call void glfwWaitEventsTimeout (double 0.5))
              #_(glfw-call void glfwWaitEvents )
              #_(glfw-call void glfwPollEvents)
              #_(java.lang.Thread/sleep 30))
            (close-windows! []
              (let [ws (var-get windows)
                    to-close (filter should-close? ws)]
                (when (seq to-close)
                  (run! cleanup! to-close)
                  (var-set windows (reduce disj ws to-close)))))
            (cleanup []
              (glfw-call Void/TYPE glfwTerminate))
            ]

      (try
        (when (init)
          (add-windows!)

          (loop []
            (wait-events)

            ;; clear gl errors. :-/
            (glGetError)

            (add-windows!)

            (close-windows!)

            (when on-main
              (on-main))

            (run! repaint!
                  (var-get windows))

            (when (seq (var-get windows))
              (recur))))
        (catch Exception e
          (println e))

        (finally
          (cleanup)))))


  )

(defn bstr [num]
  ;;String.format("%16s", Integer.toBinaryString(1)).replace(' ', '0')
  (-> num
      (java.lang.Integer/toBinaryString)
      (->> (format "%16s"))
      (.replace " " "0"))
  
  
  )


(def toolkit
  (reify
    tk/IToolkit

    tk/IToolkitRun
    (run [toolkit view-fn]
      (run view-fn))
    (run [toolkit view-fn opts]
      (run view-fn opts))

    tk/IToolkitRunSync
    (run-sync [toolkit view-fn]
      (run-sync view-fn))
    (run-sync [toolkit view-fn opts]
      (run-sync view-fn opts))

    tk/IToolkitFontExists
    (font-exists? [toolkit font]
      (font-exists? font))

    tk/IToolkitFontMetrics
    (font-metrics [toolkit font]
      (font-metrics font))

    tk/IToolkitFontAdvanceX
    (font-advance-x [toolkit font s]
      (font-advance-x font s))

    tk/IToolkitFontLineHeight
    (font-line-height [toolkit font]
      (font-line-height font))

    tk/IToolkitLogicalFontFontFamily
    (logical-font->font-family [toolkit logical-font]
      (logical-font->font-family logical-font))

    tk/IToolkitSaveImage
    (save-image [toolkit dest elem]
      (save-image dest elem))
    (save-image [toolkit dest elem [w h :as size]]
       (save-image dest elem size))))

(comment
  (tk/run toolkit (constantly (ui/label "hello there")))
  ,)

(defn -main [& args]
  (run-sync #(test-skia)))


