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
                     IDraw
                     draw
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
                     defcomponent
                     IBounds
                     bounds
                     IOrigin
                     origin
                     translate
                     mouse-event
                     mouse-move
                     mouse-move-global
                     IScroll
                     -scroll]])
  (:import com.sun.jna.Pointer
           com.sun.jna.Memory
           com.sun.jna.ptr.FloatByReference
           com.sun.jna.ptr.IntByReference
           com.sun.jna.IntegerType
           java.awt.image.BufferedImage)
  (:import java.nio.ByteBuffer
           javax.imageio.ImageIO)
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

(def void Void/TYPE)
(def main-class-loader @clojure.lang.Compiler/LOADER)

(def opengl (try
              (com.sun.jna.NativeLibrary/getInstance "opengl")
              (catch java.lang.UnsatisfiedLinkError e
                (com.sun.jna.NativeLibrary/getInstance "GL"))))
(def objlib (try
              (com.sun.jna.NativeLibrary/getInstance "CoreFoundation")
              (catch java.lang.UnsatisfiedLinkError e
                nil)))
(def glfw (com.sun.jna.NativeLibrary/getInstance "glfw"))
(def membraneskialib (com.sun.jna.NativeLibrary/getInstance "membraneskia"))


(def ^:dynamic *paint* {})

(defmacro defc
  ([fn-name lib ret]
   `(defc ~fn-name ~lib ~ret []))
  ([fn-name lib ret args]
   (let [cfn-sym (with-meta (gensym "cfn") {:tag 'com.sun.jna.Function})]
     `(let [~cfn-sym (.getFunction ~(with-meta lib {:tag 'com.sun.jna.NativeLibrary})
                                   ~(name fn-name))]
        (defn ~fn-name [~@args]
          (.invoke ~cfn-sym
                   ~ret (to-array [~@args])))))))

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
   "\t" 258
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

;; (defc demo_main freetype Integer/TYPE [argc argv])
(defc skia_load_image membraneskialib Pointer [path])
(defc skia_load_image_from_memory membraneskialib Pointer [buf buf-length])
(defc skia_draw_image membraneskialib void [skia-resource image-texture])
(defc skia_draw_image_rect membraneskialib void [skia-resource image-texture w h])

(def ^:dynamic *image-cache* nil)
(def ^:dynamic *font-cache* (atom {}))
(def ^:dynamic *draw-cache* nil)
(def ^:dynamic *skia-resource* nil)
(def ^:dynamic *window* nil)

(def DEFAULT-COLOR [0.13 0.15 0.16 1])
(declare render-text)
(declare text-bounds)
(declare load-font)


(defmacro push-matrix [& args]
  `(try
     (glPushMatrix)
     ~@args
     (finally
       (glPopMatrix))))

(defc skia_save membraneskialib Void/TYPE [skia-resource])
(defc skia_restore membraneskialib Void/TYPE [skia-resource])
(defc skia_translate membraneskialib Void/TYPE [skia-resource x y])

(defn test-skia []
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
     (skia_save *skia-resource*)
     ~@args
     (finally
       (skia_restore *skia-resource*))))

(defc skia_push_paint membraneskialib Void/TYPE [skia-resource])
(defc skia_pop_paint membraneskialib Void/TYPE [skia-resource])
(defmacro push-paint [& args]
  `(try
     (skia_push_paint *skia-resource*)
     ~@args
     (finally
       (skia_pop_paint *skia-resource*))))

(def skia-style {:membrane.ui/style-fill (byte 0)
                 :membrane.ui/style-stroke (byte 1)
                 :membrane.ui/style-stroke-and-fill (byte 2)})

(defc skia_set_style membraneskialib Void/TYPE [skia-resource style])
(defn skia-set-style [skia-resource style]
  (let [style-arg (skia-style style)]
    (assert style-arg (str "Invalid Style: " style "."))
    (skia_set_style skia-resource style-arg)))

(defc skia_set_stroke_width membraneskialib Void/TYPE [skia-resource width])
(defn skia-set-stroke-width [skia-resource width]
  (skia_set_stroke_width skia-resource (float width)))

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
(defn skia-set-color [skia-resource [r g b a]]
  (skia_set_color skia-resource (float r) (float g) (float b) (if a
                                                                (float a)
                                                                (float 1))))

(defc skia_set_alpha membraneskialib Void/TYPE [skia-resource alpha])
(defn skia-set-alpha [skia-resource alpha]
  (skia_set_alpha skia-resource (unchecked-byte (* alpha 255))))


(def font-dir "/System/Library/Fonts/")
(defn get-font [font]
  (let [font-ptr
        (if-let [font-ptr (get @*font-cache* font)]
          font-ptr
          (let [font-name (or (:name font)
                              (:name ui/default-font))
                font-path (if (.startsWith ^String font-name "/")
                            font-name
                            (str font-dir font-name))
                font-path (if (.exists (clojure.java.io/file font-path))
                            font-path
                            (do
                              (println font-path " does not exist!")
                              (:name ui/default-font)))]
            (let [font-size (or (:size font)
                                (:size ui/default-font))
                  font-ptr (load-font font-path font-size)]
              (swap! *font-cache* assoc font font-ptr)
              font-ptr)))]
    font-ptr))

(defc glGetError opengl Integer/TYPE)
(defc skia_render_line membraneskialib Void/TYPE [resource font-ptr line text-length x y])
(defc skia_next_line membraneskialib Void/TYPE [resource font-ptr])
(def byte-array-class (type (byte-array 0)))
(defn label-draw [{:keys [text font] :as label}]
  (let [lines (clojure.string/split-lines text)
        lines-arr (into-array byte-array-class
                              (for [line lines]
                                (.getBytes text "utf-8")))
        cnts-arr (into-array Integer
                             (for [arr lines-arr]
                               (alength arr)))
        font-ptr (get-font font)
        ;; text-bytes (.getBytes text "utf-8")
        ]
    (save-canvas
     (doseq [line lines]
       (skia_next_line *skia-resource* font-ptr)
       (skia_render_line *skia-resource* font-ptr line (count line) (float 0) (float 0))))))




(defn draw-button [text hover?]
  (let [btn-text (ui/label text)
        [tw th] (bounds btn-text)
        padding-x 13
        padding-y 2]
    (translate 0 0.5
               [(when hover?
                  (let [gray 0.94]
                    (ui/with-color [gray gray gray]
                                  (ui/rounded-rectangle (+ (* 2 padding-x) tw) (+ (* 2 padding-y) th) 3))))
                (ui/with-style ::ui/style-stroke
                              (let [gray 0.75]
                                (ui/with-color [gray gray gray]
                                              [(ui/rounded-rectangle (+ (* 2 padding-x) tw) (+ (* 2 padding-y) th 1) 3 )
                                               (ui/rounded-rectangle (+ (* 2 padding-x) tw) (+ (* 2 padding-y) th) 3)])))
                (translate (- padding-x 1) (- padding-y 0)
                           btn-text)])))
(defcomponent Button [text hover?]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        (bounds (draw-button text hover?)))

    IDraw
    (draw [this]
        (draw (draw-button text hover?)))
    IChildren
    (-children [this]
        [(draw-button text hover?)]))
(defn button [text hover?]
  (Button. text hover?))


(defcomponent LabelRaw [text font]
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
    (ui/draw (->Cached (LabelRaw. (:text this)
                                  (:font this))))))







(defprotocol ImageFactory
  "gets or creates an opengl image texture given some various types"
  (get-image-texture [x]))

(extend-type String
  ImageFactory
  (get-image-texture [image-path]
    (if-let [image (get @*image-cache* image-path)]
      image
      (if (.exists (clojure.java.io/file image-path))
        (let [image (skia_load_image image-path)]
          (swap! *image-cache* assoc image-path image)
          image)
        (do
          (println image-path " does not exist!")
          nil)))))

(extend-type Pointer
  ImageFactory
  (get-image-texture [image-pointer]
    image-pointer))

(defn slurp-bytes
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
      (let [bytes (slurp-bytes image-url)]
        (let [image (skia_load_image_from_memory bytes (alength bytes))]
          (swap! *image-cache* assoc image-url image)
          image)))))



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

(defn image-draw [{:keys [image-path size opacity] :as image}]
  (when-let [image-texture (get-image-texture image-path)]
    (let [[w h] size]
      (push-paint
       (when opacity
         (skia-set-alpha *skia-resource* opacity))
       (skia_draw_image_rect *skia-resource* image-texture (float w) (float h))))))


(extend-type membrane.ui.Image
  IDraw
  (draw [this]
    (image-draw this)))




(extend-type membrane.ui.Translate
  IDraw
  (draw [this]
    (save-canvas
     (skia_translate *skia-resource* (float (:x this)) (float (:y this)))
     (draw (:drawable this)))))


(defgl glPixelStorei void [pname param])

(defc skia_render_selection membraneskialib Void/TYPE [skia-resource font-ptr text text-length selection-start selection-end])
(defc skia_line_height membraneskialib Float/TYPE [font-ptr])

(defn text-selection-draw [{:keys [text font]
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
               line-bytes (.getBytes line "utf-8")
               line-count (count line)]
           (when (< selection-start line-count)
             (skia_render_selection *skia-resource* font-ptr line-bytes (alength line-bytes) (int (max 0 selection-start)) (int (min selection-end
                                                                                                                                 line-count))))
           (skia_next_line *skia-resource* font-ptr)
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
(defn text-cursor-draw [{:keys [text font cursor]
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
               line-bytes (.getBytes line "utf-8")
               line-count (count line)]
           (when (< cursor (inc line-count))
             (skia_render_cursor *skia-resource* font-ptr line-bytes (alength line-bytes) (int (max 0 cursor))))
           (skia_next_line *skia-resource* font-ptr)

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
    (let [points (into-array Float/TYPE (apply concat (:points this)))]
      (push-paint
       (skia_draw_path *skia-resource* points (alength points))))))

(defc skia_draw_rounded_rect membraneskialib Void/TYPE [skia-resource w h radius])
(extend-type membrane.ui.RoundedRectangle
  IDraw
  (draw [this]
    (skia_draw_rounded_rect *skia-resource*
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
       (skia_set_scale *skia-resource* (float sx) (float sy))
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

(def ^:dynamic *origin* [0 0 0])
(def ^:dynamic *view* nil )

(defc skia_clip_rect membraneskialib Void/TYPE [skia-resource ox oy w h])

(defn scissor-draw [scissor-view]
  (save-canvas
   (let [[ox oy] (:offset scissor-view)
         [w h] (:bounds scissor-view)]
     (skia_clip_rect *skia-resource* (float ox) (float oy) (float w) (float h))
     (draw (:drawable scissor-view)))))

(extend-type membrane.ui.ScissorView
  IDraw
  (draw [this]
      (scissor-draw this)))


(defn scrollview-draw [scrollview]
  (draw
   (ui/->ScissorView [0 0]
                  (:bounds scrollview)
                  (let [[mx my] (:offset scrollview)]
                    (translate mx my (:drawable scrollview))))))

(extend-type membrane.ui.ScrollView
  IDraw
  (draw [this]
      (scrollview-draw this)))


(defn wrap-text [text n]
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
    nil))

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
    (com.sun.jna.Native/detach true)))

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

(defn dispatch-sync! [f]
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

(defn nsstring [s]
  (let [NSString (jna/invoke Pointer CoreFoundation/objc_getClass "NSString")
        sel (jna/invoke Pointer CoreFoundation/sel_registerName "stringWithUTF8String:")]
    (jna/invoke Pointer CoreFoundation/objc_msgSend NSString sel s)))

(defn nsstring->str [nsstring]
  (let [sel (jna/invoke Pointer CoreFoundation/sel_registerName "UTF8String")]
    (jna/invoke String CoreFoundation/objc_msgSend nsstring sel)))

(defn nsnumber->int [nsnumber]
  (let [sel (jna/invoke Pointer CoreFoundation/sel_registerName "intValue")]
    (jna/invoke Integer/TYPE CoreFoundation/objc_msgSend nsnumber sel))
  )

(defn objc-selector [sel]
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
(defn fix-press-and-hold! []
  (when objlib
    (let [defaults (objc-call NSDictionary Pointer "dictionaryWithObject:forKey:"
                              (objc-call NSNumber Pointer "numberWithBool:" (char 0))
                              (nsstring "ApplePressAndHoldEnabled"))
          standard-user-defaults (objc-call NSUserDefaults Pointer "standardUserDefaults")]
      (objc-call standard-user-defaults void "registerDefaults:" defaults))))




(defn get-main-st []
  (let [threads (into-array Thread
                            (repeat  (.activeCount (.getThreadGroup (Thread/currentThread) )) nil))]
    (-> (.getThreadGroup (Thread/currentThread) )
        (.enumerate threads))
    (.getStackTrace (get threads 0))
    ))


(def messages (atom []))
(declare run-helper
         make-ui)






(defmacro glfw-call [ret fn-name & args]
  `(.invoke ^com.sun.jna.Function
            (.getFunction ^com.sun.jna.NativeLibrary glfw ~(name fn-name))
            ~ret
            (to-array (vector ~@args))))

(defn glfw-post-empty-event []
  (glfw-call void glfwPostEmptyEvent))

(defmacro gl
  ([fn-name]
   `(gl ~fn-name []))
  ([fn-name args]
   `(jna/invoke void ~(symbol "opengl" (name fn-name)) ~@args)))


(declare sx sy)

(defc skia_text_bounds membraneskialib void [font-ptr text length minx miny maxx maxy])
(defn text-bounds [font-ptr text]
  (assert (instance? Pointer font-ptr))
  (assert text "Can't get font size of nil text")
  
  (let [text-bytes (.getBytes text "utf-8")
        x (FloatByReference.)
        y (FloatByReference.)
        width (FloatByReference.)
        height (FloatByReference.)]
    (skia_text_bounds font-ptr text-bytes (alength text-bytes) x y width height)
    [(.getValue x)
     (.getValue y)
     (.getValue width)
     (.getValue height)
     ]))

(defc skia_index_for_position membraneskialib Integer/TYPE [font-ptr text text-length px])

(defn index-for-position [font text px py]
  (assert (some? text) "can't find index for nil text")
  (let [font-ptr (get-font font)
        line-height (skia_line_height font-ptr)
        line-no (loop [py py
                       line-no 0]
               (if (> py line-height)
                 (recur (- py line-height)
                        (inc line-no))
                 line-no))
        lines (clojure.string/split-lines text)]
    (if (>= line-no (count lines))
      (count text)
      (let [line (.getBytes (nth lines line-no) "utf-8")]
        (apply +
               line-no
               (skia_index_for_position font-ptr line (int (alength line)) (float px))
               (map count (take line-no lines)))))))


(intern (the-ns 'membrane.ui) 'index-for-position index-for-position)

(defn copy-to-clipboard [s]
  (let [glfw-window *window*
        window-handle (:window glfw-window)]
    ;; window-handle may be null
    (glfw-call void glfwSetClipboardString window-handle s)))
(intern (the-ns 'membrane.ui) 'copy-to-clipboard copy-to-clipboard)


(defc skia_load_font membraneskialib Pointer [font-path font-size])
(defn load-font [font-path font-size]
  (assert (string? font-path))
  (let [font-ptr (skia_load_font font-path (float font-size))]
    (assert font-ptr (str "unable to load font: " font-path " " font-size))
    font-ptr))

(def ^:dynamic *already-drawing* nil)


(defc skia_offscreen_buffer membraneskialib Pointer [skia-resource width height xscale yscale])
(defc skia_offscreen_image membraneskialib Pointer [skia-resource])
(defn cached-draw [drawable]
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
                      resource (skia_offscreen_buffer *skia-resource*
                                                      (int (* xscale img-width))
                                                      (int (* yscale img-height))
                                                      (float xscale)
                                                      (float yscale))
                      img (binding [*skia-resource* resource
                                    *already-drawing* true]
                            (when (and (not= xscale 1)
                                       (not= yscale 1))
                              (skia_set_scale *skia-resource* (float xscale) (float yscale)))
                            (skia_translate *skia-resource* padding padding)
                            (draw drawable)
                            ;; todo: fix memory leak!
                            (skia_offscreen_image *skia-resource*))
                      img-info [img img-width img-height]]
                  (swap! *draw-cache* assoc [drawable content-scale *paint*] img-info)
                  img-info)))]
        (save-canvas
         (skia_translate *skia-resource* (float (- padding)) (float (- padding)))
         (skia_draw_image_rect *skia-resource* img (float img-width) (float img-height)))))))

(defcomponent Cached [drawable]
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

(defn get-framebuffer-size [window-handle]
  (let [pix-width (IntByReference.)
        pix-height (IntByReference.)]
    (glfw-call void glfwGetFramebufferSize window-handle pix-width pix-height)
    [(.getValue pix-width)
     (.getValue pix-height)]))

(defn get-window-content-scale-size [window-handle]
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


(defn -reshape
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
        (println e)))))

(defn make-reshape-callback [window handler]
  (->ReshapeCallback window handler))

(declare email-image)

(defn -mouse-button-callback [window window-handle button action mods]
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
    ))

(defn make-mouse-button-callback [window handler]
  (MouseButtonCallback. window handler))



(defn -scroll-callback [window window-handle offset-x offset-y]
  ;; a 2x multiplier felt better. I think it might have something to do with
  ;; retina display, but it's probably some other dumb thing
  (ui/scroll @(:ui window) [(* 2 offset-x) (* 2 offset-y)])

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

(defn make-scroll-callback [window handler]
  (ScrollCallback. window handler))


(defn -window-refresh-callback [window window-handle]
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
    ))

(defn make-window-refresh-callback [window handler]
  (WindowRefreshCallback. window handler))



(defn -cursor-pos-callback [window window-handle x y]
  (try
    (doall (mouse-move @(:ui window) [x y]))
    (doall (mouse-move-global @(:ui window) [x y]))
    (catch Exception e
      (println e)
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

(defn make-cursor-pos-callback [window handler]
  (CursorPosCallback. window handler))


(def key-action-map
  {1 :press
   2 :repeat
   3 :release})
(defn -key-callback [window window-handle key scancode action mods]
  (let [action (get key-action-map action :unknown)
        ui @(:ui window)]
    (ui/key-event ui key scancode action mods)
    (cond

      ;; paste
      (and (= key 86)
           (= action :press)
           (= mods 8))
      (let [nodes (->> (tree-seq (fn [n]
                                   true)
                                 children
                                 ui)
                       (filter #(satisfies? IClipboardPaste %)))]
        (when-let [s (glfw-call String glfwGetClipboardString window-handle)]
          (doseq [node nodes]
            (-clipboard-paste node s))))

      ;; cut
      (and (= key 88)
           (= action :press)
           (= mods 8))
      (let [node (->> (tree-seq (fn [n]
                                  true)
                                children
                                ui)
                      (filter #(satisfies? IClipboardCut %))
                      ;; maybe should be last?
                      first)]
        (when-let [s (-clipboard-cut node)]
          (glfw-call void glfwSetClipboardString window-handle s)))

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

  (repaint! window)
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
    ))

(defn make-key-callback [window handler]
  (KeyCallback. window handler))

(defn int->bytes [i]
  (-> (ByteBuffer/allocate 4)
      (.putInt i)
      (.array)))

(defn -character-callback [window window-handle codepoint]
  (let [k (String. ^bytes (int->bytes codepoint) "utf-32")
        ui @(:ui window)]
    (try
      (ui/key-press ui k)
      (catch Exception e
        (println e))))

  (repaint! window))

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
    ))

(defn make-character-callback [window handler]
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
         ~resource-sym (skia_init_cpu (int (first size#)) (int (second size#)))]
     (try
       ~@body
       (finally
         (skia_cleanup ~resource-sym)))))

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
  (let [period-index (.lastIndexOf path ".")]
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

(defn draw-to-image!
  "Creates an image of elem. Returns true on success, false otherwise.

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

  note: `draw-to-image` does not take into account the content scale of your monitor. ie. if you
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
                *already-drawing* true]
        (when clear?
          (skia_clear skia-resource))
        (draw elem))
      (skia_save_image skia-resource
                       image-format-native
                       quality
                       path)))))

(defrecord GlfwSkiaWindow [render window handlers callbacks ui mouse-position skia-resource image-cache font-cache draw-cache window-content-scale window-start-width window-start-height window-start-x window-start-y window-title]
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
                 :skia-resource (skia_init))
          key-callback (make-key-callback this (get handlers :key -key-callback))
          character-callback (make-character-callback this (get handlers :char -character-callback))
          mouse-button-callback (make-mouse-button-callback this (get handlers :mouse-button -mouse-button-callback))
          reshape-callback (make-reshape-callback this (get handlers :reshape -reshape))
          scroll-callback (make-scroll-callback this (get handlers :scroll -scroll-callback))
          window-refresh-callback (make-window-refresh-callback this (get handlers :refresh -window-refresh-callback))
          cursor-pos-callback (make-cursor-pos-callback this (get handlers :cursor -cursor-pos-callback))]

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

      (glfw-call Pointer glfwSetCursorPosCallback window, cursor-pos-callback)
      (glfw-call Pointer glfwSetKeyCallback window key-callback)
      (glfw-call Pointer glfwSetCharCallback window character-callback)
      (glfw-call Pointer glfwSetMouseButtonCallback window mouse-button-callback)
      (glfw-call Pointer glfwSetFramebufferSizeCallback window reshape-callback)
      (glfw-call Pointer glfwSetScrollCallback window scroll-callback)
      (glfw-call Pointer glfwSetWindowRefreshCallback window window-refresh-callback)

      (glfw-call void glfwSetWindowPos window window-x window-y)

      ;; reshape must be called before glfw show window
      ;; so that we have the right size buffers set up
      (reshape! this window-width window-height)
      (glfw-call void glfwShowWindow window)

      (doto (assoc this
                   ;; need to hang on to callbacks so they don't get garbage collected!
                   :callbacks
                   [key-callback
                    character-callback
                    mouse-button-callback
                    reshape-callback
                    scroll-callback
                    window-refresh-callback
                    cursor-pos-callback]))))

  (reshape! [_ width height]
    (glfw-call Void/TYPE glfwMakeContextCurrent window)
    
    (glViewport (int 0) (int 0) width height)
    (glClearStencil (int 0))
    (glClear (bit-or GL_COLOR_BUFFER_BIT
                     GL_STENCIL_BUFFER_BIT))


    (let [[xscale yscale :as content-scale] (get-window-content-scale-size window)
          [fb-width fb-height] (get-framebuffer-size window)]
      (reset! window-content-scale content-scale)
      (skia_reshape skia-resource fb-width fb-height xscale yscale))

    nil)
  
  (should-close? [_]
    (when window
      (glfw-call Boolean/TYPE glfwWindowShouldClose window)))
  (cleanup! [this]
    (skia_cleanup skia-resource)
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
    

    (glfw-call Void/TYPE glfwMakeContextCurrent window)

    (skia_clear skia-resource)

    (binding [*image-cache* image-cache
              *font-cache* font-cache
              *window* this
              *draw-cache* draw-cache
              *skia-resource* skia-resource]
      (let [to-render (swap! ui (fn [_]
                                  (render)))]
        (do
          (draw to-render))))
    (skia_flush skia-resource)
    (glfw-call Void/TYPE glfwSwapBuffers window)))

(declare run-helper)
(defonce window-chan (chan 1))

(defn run-sync
  "Open a window and call `make-ui` to draw. Returns when the window is closed.

  `make-ui` should be a 0 argument function that returns an object satisfying `IDraw`.
  `make-ui` will be called for every repaint. Repaints occur on every event. You can also trigger a repaint by calling `glfw-post-empty-event`.

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
  ([make-ui]
   (run-sync make-ui {}))
  ([make-ui {:keys [window-start-width
                    window-start-height
                    window-start-x
                    window-start-y
                    handlers] :as options}]

   (async/>!! window-chan (map->GlfwSkiaWindow (merge
                                                {:render make-ui}
                                                options)))

   (dispatch-sync!
       (fn []
         (try
           (run-helper window-chan)
           (catch Exception e
             (println e)))))))

(defn run
  "Open a window and call `make-ui` to draw. Returns a channel that is closed when the window is closed.

  `make-ui` should be a 0 argument function that returns an object satisfying `IDraw`.
  `make-ui` will be called for every repaint. Repaints occur on every event. You can also trigger a repaint by calling `glfw-post-empty-event`.

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
  ([make-ui]
   (run make-ui {}))
  ([make-ui {:keys [window-start-width
                    window-start-height
                    window-start-x
                    window-start-y
                    handlers] :as options}]

   (async/thread
     (run-sync make-ui options))))

(intern (the-ns 'membrane.ui) 'run run)
(intern (the-ns 'membrane.ui) 'run-sync run-sync)

(defn run-helper [window-chan]
  (with-local-vars [windows #{}]
    (letfn [(init []
              (if (not= 1 (glfw-call Integer/TYPE glfwInit))
                false
                (do
                  (.setContextClassLoader (Thread/currentThread) main-class-loader)
                  (fix-press-and-hold!)
                  ;; (glfw-call void glfwWindowHint GLFW_COCOA_RETINA_FRAMEBUFFER (int 0))
                  (glfw-call void glfwWindowHint GLFW_VISIBLE (int 0))

                  true)))
            (add-windows! []
              (loop [window (async/poll! window-chan)]
                (when window
                  (var-set windows (conj (var-get windows) (init! window)))
                  (recur (async/poll! window-chan)))))
            (wait []
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
            (wait)

            ;; clear gl errors. :-/
            (glGetError)

            (add-windows!)

            (close-windows!)

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




(defn -main [& args]
  (run-sync #(test-skia)))


