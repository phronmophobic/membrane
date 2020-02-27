(ns membrane.opengl
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
                     -scroll
                     ]]
            [membrane.analyze :as analyze]
            clojure.tools.reader.reader-types
            [clojure.tools.reader.edn :as edn]
            )
  (:import com.sun.jna.Pointer
           com.sun.jna.Memory
           com.sun.jna.ptr.FloatByReference
           com.sun.jna.ptr.IntByReference
           com.sun.jna.IntegerType
           java.awt.image.BufferedImage
           org.ejml.simple.SimpleMatrix
           (java.time
            YearMonth
            LocalDate)
           java.time.format.DateTimeFormatter)
  (:import java.nio.ByteBuffer
           javax.imageio.ImageIO)
  (:gen-class))

(def GL_COLOR_BUFFER_BIT  (int 0x00004000))

(def GL_CURRENT_BIT (int 0x00000001))
(def GL_POINT_BIT (int 0x00000002))
(def GL_LINE_BIT (int 0x00000004))
(def GL_POLYGON_BIT (int 0x00000008))
(def GL_POLYGON_STIPPLE_BIT (int 0x00000010))
(def GL_PIXEL_MODE_BIT (int 0x00000020))
(def GL_LIGHTING_BIT (int 0x00000040))
(def GL_FOG_BIT (int 0x00000080))
(def GL_DEPTH_BUFFER_BIT (int 0x00000100))
(def GL_ACCUM_BUFFER_BIT (int 0x00000200))
(def GL_STENCIL_BUFFER_BIT (int 0x00000400))
(def GL_VIEWPORT_BIT (int 0x00000800))
(def GL_TRANSFORM_BIT (int 0x00001000))
(def GL_ENABLE_BIT (int 0x00002000))
(def GL_COLOR_BUFFER_BIT (int 0x00004000))
(def GL_HINT_BIT (int 0x00008000))
(def GL_EVAL_BIT (int 0x00010000))
(def GL_LIST_BIT (int 0x00020000))
(def GL_TEXTURE_BIT (int 0x00040000))
(def GL_SCISSOR_BIT (int 0x00080000))
(def GL_SCISSOR_TEST (int 0x0C11))
(def GL_ALL_ATTRIB_BITS (int -1))
(def GL_TEXTURE_2D (int 0x0DE1))
(def GL_POINTS (int 0x0000))
(def GL_LINES (int 0x0001))
(def GL_LINE_LOOP (int 0x0002))
(def GL_LINE_STRIP (int 0x0003))
(def GL_TRIANGLES (int 0x0004))
(def GL_TRIANGLE_STRIP (int 0x0005))
(def GL_TRIANGLE_FAN (int 0x0006))
(def GL_QUADS (int 0x0007))
(def GL_QUAD_STRIP (int 0x0008))
(def GL_POLYGON (int 0x0009))
(def GL_PROJECTION_STACK_DEPTH (int 0x0BA4))
(def GL_MODELVIEW_STACK_DEPTH (int 0x0BA3))
(def GLFW_STENCIL_BITS (int 0x00021006))

(def GL_ZERO (int 0))
(def GL_ONE (int 1))
(def GL_SRC_COLOR (int 0x0300))
(def GL_ONE_MINUS_SRC_COLOR (int 0x0301))
(def GL_SRC_ALPHA (int 0x0302))
(def GL_ONE_MINUS_SRC_ALPHA (int 0x0303))
(def GL_DST_ALPHA (int 0x0304))
(def GL_ONE_MINUS_DST_ALPHA (int 0x0305))

(def GL_BLEND (int 0x0BE2))

(def GL_TEXTURE0 (int 0x84C0))
(def GL_UNPACK_ALIGNMENT (int 0x0CF5) )
(def GL_MODELVIEW_MATRIX (int 0x0BA6))
(def GL_PROJECTION_MATRIX (int 0x0BA7))
(def GL_VIEWPORT (int 0x0BA2))

(def GL_MODELVIEW (int 0x1700) )
(def GL_PROJECTION (int 0x1701))
(def GLFW_COCOA_RETINA_FRAMEBUFFER (int 0x00023001))
(def GL_FRAMEBUFFER (int 0x8D40))
(def GLFW_VISIBLE (int 0x00020004))
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


(def native-keycodes
  {0x1D "0"
   0x12 "1"
   0x13 "2"
   0x14 "3"
   0x15 "4"
   0x17 "5"
   0x16 "6"
   0x1A "7"
   0x1C "8"
   0x19 "9"
   0x00 "A"
   0x0B "B"
   0x08 "C"
   0x02 "D"
   0x0E "E"
   0x03 "F"
   0x05 "G"
   0x04 "H"
   0x22 "I"
   0x26 "J"
   0x28 "K"
   0x25 "L"
   0x2E "M"
   0x2D "N"
   0x1F "O"
   0x23 "P"
   0x0C "Q"
   0x0F "R"
   0x01 "S"
   0x11 "T"
   0x20 "U"
   0x09 "V"
   0x0D "W"
   0x07 "X"
   0x10 "Y"
   0x06 "Z"

   0x27 "'"
   0x2a "\\"
   0x2b ","
   0x18 "="
   0x32 :grave_accent
   0x21 "["
   0x1b "-"
   0x2f "."
   0x1e "]"
   0x29 ";"
   0x2c "/"
   0x0a :world_1

   0x33 :backspace
   0x39 :caps_lock
   0x75 :delete
   0x7d :down
   0x77 :end
   0x24 :enter
   0x35 :escape
   0x7a :f1
   0x78 :f2
   0x63 :f3
   0x76 :f4
   0x60 :f5
   0x61 :f6
   0x62 :f7
   0x64 :f8
   0x65 :f9
   0x6d :f10
   0x67 :f11
   0x6f :f12
   0x69 :f13
   0x6b :f14
   0x71 :f15
   0x6a :f16
   0x40 :f17
   0x4f :f18
   0x50 :f19
   0x5a :f20
   0x73 :home
   0x72 :insert
   0x7b :left
   0x3a :left_alt
   0x3b :left_control
   0x38 :left_shift
   0x37 :left_super
   0x6e :menu
   0x47 :num_lock
   0x79 :page_down
   0x74 :page_up
   0x7c :right
   0x3d :right_alt
   0x3e :right_control
   0x3c :right_shift
   0x36 :right_super
   0x31 " "
   0x30 :tab
   0x7e :up

   0x52 :kp_0
   0x53 :kp_1
   0x54 :kp_2
   0x55 :kp_3
   0x56 :kp_4
   0x57 :kp_5
   0x58 :kp_6
   0x59 :kp_7
   0x5b :kp_8
   0x5c :kp_9
   0x45 :kp_add
   0x41 :kp_decimal
   0x4b :kp_divide
   0x4c :kp_enter
   0x51 :kp_equal
   0x43 :kp_multiply
   0x4e :kp_subtract
   })
(def native-keymap (into {} (map (comp vec reverse) native-keycodes)))

(def void Void/TYPE)
(def main-class-loader @clojure.lang.Compiler/LOADER)

(def opengl (com.sun.jna.NativeLibrary/getInstance "opengl"))
(def objlib (com.sun.jna.NativeLibrary/getInstance "CoreFoundation"))
;; (def freetype (com.sun.jna.NativeLibrary/getInstance "freetype"))
(def glfw (com.sun.jna.NativeLibrary/getInstance "glfw"))
(def membranelib (com.sun.jna.NativeLibrary/getInstance "membrane"))

;; (def appkit (com.sun.jna.NativeLibrary/getInstance "AppKit"))

#_(nsnumber->int
 (jna/invoke Pointer CoreFoundation/CFBundleGetValueForInfoDictionaryKey
             (jna/invoke Pointer CoreFoundation/CFBundleGetMainBundle)
             (nsstring "NSHighResolutionCapable")))
;; (java.lang.System/getProperty "java.library.path")
;; (java.lang.System/setProperty
;;  "java.library.path"
;;  (str (java.lang.System/getProperty "java.library.path")
;;       ":"
;;       "/System/Library/Frameworks/OpenGL.framework/Versions/A/Libraries"))

;; (java.lang.System/loadLibrary "gl")

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

;; (defn log [& ss]
;;   (spit "/var/tmp/membrane.log" (str (clojure.string/join " " ss) "\n") :append true))

#_(defmacro defgl
  ([fn-name ret]
   `(defgl ~fn-name ~ret []))
  ([fn-name ret args]
   `(defn ~fn-name [~@args]
      ;; (println ~(name fn-name))
      (let [cfn# (.getFunction ~'opengl
                               ~(name fn-name))
            ret#  (.invoke cfn#
                           ~ret (to-array [~@args]))
            err# (glGetError)]
        (when (not (zero? err#))
          (println "glError on " ~(name fn-name) err#))
        ret#))))



;; (defc demo_main freetype Integer/TYPE [argc argv])
(defc loadImage membranelib Integer/TYPE [path])
(defc loadImageFromMemory membranelib Integer/TYPE [buf buf-length])
(defc drawImage membranelib void [image-texture])

(defc init_resources membranelib Integer/TYPE [vert-source frag-source selection-frag-source])
;; (defc display membranelib void [width height])
;; (defc render_text membranelib void [text x y sx sy])
;; (defn run-demo [& args]
;;   (.execute (.getBlockingMainQueueExecutor (com.apple.concurrent.Dispatch/getInstance))
;;             (fn []
;;               (demo_main 0 nil)))
;;   )

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


(def ^:dynamic *image-cache* nil)
(def ^:dynamic *font-cache* (atom {}))
(def ^:dynamic *draw-cache* nil)
(def ^:dynamic *window* nil)

(def DEFAULT-COLOR [0.13 0.15 0.16 1])
(declare main-font)
(declare render-text)
(declare text-bounds)
(declare push-matrix translate)
(declare load-font)
(declare set-font-size)

(defgl glPushMatrix void)
(defgl glPopMatrix void)
(defgl glTranslated void [x y z])
(defgl glRotated void [a x y z])
(defgl glScissor void [x y width height])
(defgl glGetFloatv void [pname params])
(defgl glGetIntegerv void [pname params])
(defgl glClear void [bits])
(defgl glUseProgram void [shader])
;; (defgl glGetError Integer/TYPE)



(defmacro push-matrix [& args]
  `(try
     (glPushMatrix)
     ~@args
     (finally
       (glPopMatrix))))

(def font-dir "/Users/adrian/workspace/membrane/fonts/")
(defn get-font [font-name font-size]
  (let [font
        (if (instance? Pointer font-name)
          font-name
          (if-let [font (get @*font-cache* [font-name font-size])]
            font
            (let [font-path (if (.startsWith ^String font-name "/")
                              font-name
                              (str font-dir font-name))]
              (if (.exists (clojure.java.io/file font-path))
                (let [font (load-font font-path font-size)]
                  (swap! *font-cache* assoc [font-name font-size] font)
                  font)
                (do
                  (println font-name " does not exist!")
                  main-font)))))]
    ;; (set-font-size font font-size)
    font))
(def main-font-filepath "/System/Library/Fonts/Menlo.ttc")


(defgl glPushAttrib void [mask])
(defgl glPopAttrib void [])
(defgl glColor4d void [r g b a])
(defgl glColor3d void [r g b])
(defn glColor
  ([[r g b & [a] :as color]]
   (if a
     (glColor4d (double r) (double g) (double b) (double a))
     (glColor3d (double r) (double g) (double b)))))

(defmacro with-color
  ([color & body]
   `(try
      (glPushAttrib GL_CURRENT_BIT)
      (glColor ~color)
      ~@body
      (finally
        (glPopAttrib)))))


(defn label-draw [{:keys [text options] :as label}]
  (let [font-color (:font-color options)]
    (try
     (when font-color
       (glPushAttrib GL_CURRENT_BIT)
       (glColor font-color))
     (render-text (get-font (get options :font main-font-filepath) (get options :font-size 14)) text [0 0])
     (finally
       (when font-color
         (glPopAttrib))))))



(defcomponent LabelRaw [text options]
    IBounds
    (-bounds [_]
        (let [[minx miny maxx maxy] (text-bounds (get-font (get options :font main-font-filepath)
                                                           (get options :font-size 14))
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
    (let [[minx miny maxx maxy] (text-bounds (get-font (get (:options this) :font main-font-filepath)
                                                       (get (:options this) :font-size 14))
                                             (:text this))
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
      [maxx maxy]))
  IDraw
  (draw [this]
    (ui/draw (->Cached (LabelRaw. (:text this)
                                  (:options this))))))

(defc render_selection membranelib Void/TYPE [font text selection-start selection-end])







(defc delete_framebuffer membranelib void [fbo])
(defn delete-framebuffer [fbo]
  (delete_framebuffer fbo))

(defc create_framebuffer membranelib Integer/TYPE [width height fbo tex])
(let [fb-counter (atom 0)]
  (defn create-frame-buffer [width height]
    (let [fbo (Memory. 4)
          tex (Memory. 4)
          success? (create_framebuffer (int width) (int height) fbo tex)]
      (assert (= 1 success?) (str "Count not create frame buffer " width ", " height))
      [
       (.getInt tex 0)
       (.getInt fbo 0)
       ;;
       ])
    ))




(defprotocol ImageFactory
  "gets or creates an opengl image texture given some various types"
  (get-image-texture [x]))

(extend-type String
  ImageFactory
  (get-image-texture [image-path]
    (if-let [image (get @*image-cache* image-path)]
      image
      (if (.exists (clojure.java.io/file image-path))
        (let [image (loadImage image-path)]
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
        (let [image (loadImageFromMemory bytes (alength bytes))]
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
(intern (the-ns 'membrane.ui) 'image-size image-size)

(declare draw-image)
(defn image-draw [{:keys [image-path size opacity] :as image}]
  (when-let [image-texture (get-image-texture image-path)]
    (let [[width height] size]
      (draw-image image-texture 0 0 width height opacity))))




(extend-type membrane.ui.Image
  IDraw
  (draw [this]
    (image-draw this)))


(defgl glScaled void [x y z])
(defn scale [x y z]
  (glScaled (double x) (double y) (double z)))
(defn translate [x y z]
  (glTranslated (double x) (double y) (double z)))

(defn rotate [angle x y z]
  (glRotated (double angle) (double x) (double y) (double z)))

(extend-type membrane.ui.Translate
  IDraw
  (draw [this]
      (push-matrix
       (translate (:x this) (:y this) 0)
       (draw (:drawable this)))))


(extend-type membrane.ui.Rotate
  IDraw
  (draw [this]
      (push-matrix
       (rotate (:degrees this) 0 0 1)
       (draw (:drawable this)))))

(defgl glVertex3d void [x y z])
;; (let*
;;  [cfn8540 (.getFunction opengl "glVertex3d")]
;;  (clojure.core/defn
;;   glVertex3d
;;    [x y z]
;;    ;; (println "glVertex3d")
;;    (.invoke cfn8540 void (clojure.core/to-array [x y z]))))
(defgl glTexCoord2d void [x y])
;; (clojure.core/defn
;;   glTexCoord2d
;;   [x y]
;;   (clojure.core/let
;;       [cfn__192__auto__
;;        (.getFunction opengl "glTexCoord2d")
;;        ret__193__auto__
;;        (.invoke cfn__192__auto__ void (clojure.core/to-array [x y]))]
;;     ret__193__auto__))
(defn vertex
  ([x y]
   (glVertex3d (double x) (double y) (double 0)))
  ([x y z]
   (glVertex3d (double x) (double y) (double z))))

(defn texcoord [x y]
  (glTexCoord2d (double x) (double y)))

(defgl glEnable void [feature])
(defgl glDisable void [feature])
(defgl glBlendFunc void [sfactor dfactor])
(defgl glActiveTexture void [active-texture])
(defgl glPixelStorei void [pname param])
;; (defgl glBegin void [mode])
;; (defc glBegin opengl void [mode])
(let*
    [cfn8537 (.getFunction ^com.sun.jna.NativeLibrary opengl "glBegin")]
  (clojure.core/defn
    glBegin
    [mode]
    (.invoke cfn8537 void (clojure.core/to-array [mode]))))
(defgl glEnd void)
(defmacro draw-lines [& body]
  `(try
     (glBegin GL_LINES)
     (do
       ~@body)
     (finally
       (glEnd))))

(defmacro draw-quads [& body]
  `(try
     (glBegin GL_QUADS)
     (do
       ~@body)
     (finally
       (glEnd))))

(defmacro draw-polygon [& body]
  `(try
     (glBegin GL_POLYGON)
     (do
       ~@body)
     (finally
       (glEnd))))

(defmacro draw-line-strip [& body]
  `(try
     (glBegin GL_LINE_STRIP)
     (do
       ~@body)
     (finally
       (glEnd)))
  )


(defgl glBindTexture void [texture-2d texture])
(defn draw-image [texture x y width height opacity]
  (with-color [1.0 1.0 1.0 opacity]
    (glBindTexture  GL_TEXTURE_2D texture)
    (draw-quads
     (texcoord 0.0 0.0) (vertex x y)
     (texcoord 1.0 0.0) (vertex (+ x width) y)
     (texcoord 1.0 1.0) (vertex (+ x width) (+ y height))
     (texcoord 0.0 1.0) (vertex x (+ y height)))
    (glBindTexture  GL_TEXTURE_2D 0)))


(defn text-selection-draw [{:keys [text options]
                            [selection-start selection-end] :selection
                            :as text-selection}]

  (with-color [0.6980392156862745
                      0.8431372549019608
                      1]
    (render_selection (get-font (get options :font main-font) (get options :font-size 14)) text selection-start selection-end)))

(extend-type membrane.ui.TextSelection
  IBounds
  (-bounds [this]
    (let [[minx miny maxx maxy] (text-bounds (get-font (get (:options this) :font main-font)
                                                       (get (:options this) :font-size 14))
                                             (:text this))
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
      [maxx maxy]))

  IDraw
  (draw [this]
    (text-selection-draw this)))

(defn text-cursor-draw [{:keys [text options cursor]
                         :as text-cursor}]
  (let [cursor (min (count text)
                    cursor)]
   (render_selection (get-font (get options :font main-font) (get options :font-size 14))
                     (str (subs text 0 cursor)
                          "8") cursor (inc cursor))))

(extend-type membrane.ui.TextCursor
  IBounds
  (-bounds [this]
    (let [[minx miny maxx maxy] (text-bounds (get-font (get (:options this) :font main-font)
                                                       (get (:options this) :font-size 14))
                                             (:text this))
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
      [maxx maxy]))

  IDraw
  (draw [this]
    (text-cursor-draw this)))

(extend-type membrane.ui.Path
  IDraw
  (draw [this]
    (push-matrix
     (draw-lines
      (doseq [[[x1 y1] [x2 y2]] (map vector (:points this) (rest (:points this)))]
        (vertex x1 y1)
        (vertex x2 y2))))))



;; (gl-import- glPushAttrib gl-push-attrib)
;; (gl-import- glPopAttrib gl-pop-attrib)
(extend-type membrane.ui.Polygon
  IDraw
  (draw [this]
    (push-matrix
     (try
       (glPushAttrib GL_CURRENT_BIT)
       (when (:color this)
         (glColor (:color this)))
       (draw-polygon
        (doseq [[x y] (:points this)]
          (vertex x y)))
       (finally
         (glPopAttrib))))))

(extend-type membrane.ui.UseColor
  IDraw
  (draw [this]
    (with-color (:color this)
      (doseq [drawable (:drawables this)]
        (draw drawable)))))


(extend-type membrane.ui.UseScale
  IDraw
  (draw [this]
    (push-matrix
     (apply scale (:scalars this))
     (doseq [drawable (:drawables this)]
       (draw drawable)))))


(extend-type membrane.ui.Arc
  IDraw
  (draw [this]
    (let [arc-length (- (:rad-end this) (:rad-start this))]
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


(defmacro with-enabled [enable-flag & body]
  `(try
     (glPushAttrib enable-flag)
     (glEnable ~enable-flag)
     ~@body
     (finally
       (glPopAttrib)))
  
  (throw (Exception. "not implemented")))

(defn scissor-draw [scissor-view]
  (try
    (glPushAttrib GL_SCISSOR_BIT)
    (let [[ox oy] (:offset scissor-view)
          
          ;; [gx gy _] *origin*
          ;; [_ _ view-width view-height] *view*
          [width height] (:bounds scissor-view)
          ;; calculate view coordinates
          buffer (Memory. (* 4 16))
          _ (glGetFloatv GL_MODELVIEW_MATRIX buffer)
          matrix-buffer (vec
                         (for [i (range 4)]
                           (vec
                            (for [j (range 4)]
                              (.getFloat buffer (* (+ (* 4 i)
                                                             j)
                                                          4))))))          

          modelview-matrix (.transpose
                            (SimpleMatrix.
                             ^"[[D"
                             (into-array
                              (map double-array matrix-buffer))))
          _ (glGetFloatv GL_PROJECTION_MATRIX  buffer)
          matrix-buffer (vec
                         (for [i (range 4)]
                           (vec
                            (for [j (range 4)]
                              (.getFloat buffer (* (+ (* 4 i)
                                                              j)
                                                           4))))))
          projection-matrix (.transpose
                             (SimpleMatrix.
                              ^"[[D"
                              (into-array
                               (map double-array matrix-buffer))))
          matrix (.mult projection-matrix modelview-matrix)

          _ (glGetIntegerv GL_VIEWPORT buffer)
          viewport-x (.getInt buffer 0)
          viewport-y (.getInt buffer 4)
          viewport-width (.getInt buffer 8)
          viewport-height (.getInt buffer 12)
          
          scissor-origin-matrix (.mult matrix
                                       (SimpleMatrix.
                                        ^"[[D"
                                        (into-array
                                         [(double-array [ox])
                                          (double-array [oy])
                                          (double-array [0])
                                          (double-array [1])])))
          scissor-x1 (.get scissor-origin-matrix 0)
          scissor-x1 (+ (* (+ scissor-x1 1)
                          (/ viewport-width
                             2))
                       viewport-x)
          scissor-y1 (.get scissor-origin-matrix 1)
          scissor-y1 (+ (* (+ scissor-y1 1)
                          (/ viewport-height
                             2))
                       viewport-y)
          
          ;; [scissor-x scissor-y _ _] (core-matrix/to-nested-vectors
          ;;                            (core-matrix/mmul
          ;;                             [ox oy 0 1]
          ;;                             inverse-matrix
          ;;                             ))
          scissor-bounds-matrix (.mult matrix
                                       (SimpleMatrix.
                                        ^"[[D"
                                        (into-array
                                         [(double-array [(+ ox width)])
                                          (double-array [(+ oy height)])
                                          (double-array [0])
                                          (double-array [1])])))
          scissor-x2 (.get scissor-bounds-matrix 0)
          scissor-x2 (+ (* (+ scissor-x2 1)
                              (/ viewport-width
                                 2))
                           viewport-x)
          
          scissor-y2 (.get scissor-bounds-matrix 1)
          scissor-y2 (+ (* (+ scissor-y2 1)
                              (/ viewport-height
                                 2))
                        viewport-y)

          scissor-x (int (max 0 (min scissor-x1 scissor-x2)))
          scissor-y (int (max 0 (min scissor-y1 scissor-y2)))
          scissor-width (- (min viewport-width (int (max scissor-x1 scissor-x2)))
                           scissor-x)
          scissor-height (- (min viewport-height (int (max scissor-y1 scissor-y2)))
                            scissor-y)
          ;; [scissor-width scissor-height _ _] (core-matrix/to-nested-vectors
          ;;                                     (core-matrix/mmul
          ;;                                      [(+ ox width) (+ oy height) 0 1]
          ;;                                      inverse-matrix
          ;;                                      ))
          
          ]
      ;; (println "updated " [ox oy scissor-x scissor-y scissor-width scissor-height])

      (glEnable GL_SCISSOR_TEST)
      ;; not sure why adding 2 makes this work. need a better understanding
      ;; and fix
      (glScissor (+ scissor-x 2)
                 (+ scissor-y 2)
                 scissor-width
                 scissor-height)
      (draw (:drawable scissor-view)))
    (catch Exception e
      (println e))
    (finally
      (glPopAttrib)))

  ;; GLfloat matrix[16]; 
  ;; glGetFloatv (GL_MODELVIEW_MATRIX, matrix); 
  )

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


(def ^com.sun.jna.Function getClass (.getFunction ^com.sun.jna.NativeLibrary objlib "objc_getClass"))
(def ^com.sun.jna.Function argv (.getFunction ^com.sun.jna.NativeLibrary objlib "_NSGetArgv"))
(def ^com.sun.jna.Function argc (.getFunction ^com.sun.jna.NativeLibrary objlib "_NSGetArgc"))

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

(def-objc-class NSUserDefaults)
(def-objc-class NSNumber)
(def-objc-class NSDictionary)

;; (objc-call standard-user-defaults Pointer "objectForKey:" (nsstring "ApplePressAndHoldEnabled"))
(defn fix-press-and-hold! []
  (let [defaults (objc-call NSDictionary Pointer "dictionaryWithObject:forKey:"
                            (objc-call NSNumber Pointer "numberWithBool:" (char 0))
                            (nsstring "ApplePressAndHoldEnabled"))
        standard-user-defaults (objc-call NSUserDefaults Pointer "standardUserDefaults")]
    (objc-call standard-user-defaults void "registerDefaults:" defaults)))




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

(def width (int 787))
(def height (int 1000))

(defmacro gl
  ([fn-name]
   `(gl ~fn-name []))
  ([fn-name args]
   `(jna/invoke void ~(symbol "opengl" (name fn-name)) ~@args)))


(declare sx sy)

(defc text_bounds membranelib void [minx miny maxx maxy font text])
(defn text-bounds [font text]
  (assert font "Can't get font size of nil font")
  (assert text "Can't get font size of nil text")
  (let [minx (FloatByReference.)
        miny (FloatByReference.)
        maxx (FloatByReference.)
        maxy (FloatByReference.)]
    (text_bounds minx miny maxx maxy font text)
    [(.getValue minx)
     (.getValue miny)
     (.getValue maxx)
     (.getValue maxy)
     ]))

(defc render_text membranelib void [font text x y])

(defn render-text
  ([font text [x y]]
   (render_text font text (float x) (float y)))
  ([font text]
   (render_text font text (float 0) (float 0))))

(defc index_for_position membranelib Integer/TYPE [font text px py])
(defn index-for-position [font text px py]
  (assert (some? text) "can't find index for nil text")
  (let [font (if (instance? Pointer font)
               font
               (get-font (:name font) (:size font)))]
    (assert font)
    (index_for_position font text (float px) (float py))))

(intern (the-ns 'membrane.ui) 'index-for-position index-for-position)


(defc set_font_size membranelib Integer/TYPE [font font-size])
(defn set-font-size [font font-size]
  (set_font_size font (int font-size)))

(defc set_font_color membranelib void [color])
(defn set-font-color [color]
  (set_font_color (into-array java.lang.Float/TYPE color)))

(defc load_font membranelib Pointer [font-path font-size])
(defn load-font [font-path font-size]
  (load_font font-path (float font-size)))

(def ^:dynamic *already-drawing* nil)
(declare get-screen-size draw-image ortho-matrix glPushAttrib glLoadIdentity glMultMatrixd glClearColor)
(defgl glBindFramebuffer void [c fbo])
(defn cached-draw [{:keys [drawable] :as this}]
  (if *already-drawing*
    (draw drawable)
    (let [[width height] (bounds drawable)
          max-tex-size 2048]
      (when (and (pos? width)
                 (pos? height))
        (let [texes (if-let [texes (get @*draw-cache* drawable)]
                      texes
                      (do
                        (when *already-drawing*
                          (println "already drawing!!!" drawable))
                        (binding [*already-drawing* true]
                          (let [texes (vec
                                       (for [x-offset (range 0 width max-tex-size)
                                             y-offset (range 0 height max-tex-size)
                                             :let [tex-width (int (min 2048 (Math/ceil (- width x-offset))))
                                                   tex-height (int (min 2048 (Math/ceil (- height y-offset))))]]
                                         (let [[tex fbo] (create-frame-buffer tex-width tex-height)

                                               projection-matrix
                                               (doto (Memory. (* 8 16))
                                                 (.write 0 ^doubles (ortho-matrix 0 (* 1 tex-width) 0 (* 1 tex-height) -1 1)
                                                         0 16))]

                                           (try

                                             (glPushAttrib GL_ALL_ATTRIB_BITS)

                                             (glDisable GL_SCISSOR_TEST)
                                             (jna/invoke void opengl/glViewport (int 0) (int 0) tex-width tex-height)

                                             (gl glMatrixMode (GL_PROJECTION))
                                             (glPushMatrix)

                                             (gl glLoadIdentity)
                                             (gl glMultMatrixd (projection-matrix))

                                             (gl glMatrixMode (GL_MODELVIEW))
                                             (glPushMatrix)

                                             (gl glClearColor ((float 0.0) (float 0.0) (float 0.0) (float 0.0)))
                                             (jna/invoke Void/TYPE opengl/glClear GL_COLOR_BUFFER_BIT)
                                             (gl glLoadIdentity)
                                             ;; (glColor [0.1 0.5 0.5 1])
                                             (glColor DEFAULT-COLOR)
                                             (glBindTexture  GL_TEXTURE_2D 0)

                                             ;; (glBlendFunc GL_SRC_ALPHA GL_SRC_ALPHA)

                                             (translate (- x-offset) (- y-offset) 0)

                                             (draw drawable)

                                             (finally

                                               (gl glMatrixMode (GL_PROJECTION))
                                               (glPopMatrix)

                                               (gl glMatrixMode (GL_MODELVIEW))
                                               (glPopMatrix)

                                               (glPopAttrib)

                                               ;; need to do this correctly at some point
                                               ;; (delete-framebuffer fbo)

                                               (glBindFramebuffer GL_FRAMEBUFFER 0)
                                               #_(let [[width height] (get-screen-size (:window *window*))]
                                                   (jna/invoke void opengl/glViewport (int 0) (int 0) width height))

                                               (delete-framebuffer fbo)))
                                           [x-offset y-offset tex-width tex-height tex])))]
                            (swap! *draw-cache* assoc drawable texes)
                            texes))))]
          (doseq [[x-offset y-offset tex-width tex-height tex] texes]
            (draw-image tex x-offset y-offset tex-width tex-height 1)))))))

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
      (cached-draw this)

    )
  )

(declare main-font)




(defn transpose [m]
  (apply concat (apply map vector m)))
(defn ortho-matrix [left right bottom top near far]
  (let [[left right bottom top near far]
        (map double [left right bottom top near far])
        a (/ 2.0 (- right left))
        b (/ 2.0 (- top bottom))
        c (/ -2.0 (- far near))
        tx (- (/ (+ right left)
                 (- right left)))
        ty (- (/ (+ top bottom)
                 (- top bottom)))
        tz (- (/ (+ far near)
                 (- far near)))
        mtx (transpose
             [[a 0 0 tx]
              [0 b 0 ty]
              [0 0 c tz]
              [0 0 0 1]])]
    (into-array Double/TYPE
                (map double mtx))))

(defn get-screen-size [window-handle]
  (let [pix-width (IntByReference.)
        pix-height (IntByReference.)]
    (glfw-call void glfwGetFramebufferSize window-handle pix-width pix-height)
    [(.getValue pix-width)
     (.getValue pix-height)]))

(defprotocol IWindow
  (init! [_])
  (reshape! [_ width height])
  (should-close? [_])
  (cleanup! [_])
  (repaint! [_]))


(defn -reshape
  ([window window-handle]
   (let [[width height] (get-screen-size window-handle) ]
     (-reshape window window-handle
               width height)))
  ([window window-handle width height]
   (reshape! window width height)))

(declare main-thread-chan sleep)
(deftype ReshapeCallback [window]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Integer/TYPE Integer/TYPE]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (try
      (binding [*image-cache* (:image-cache window)
                *font-cache* (:font-cache window)
                *draw-cache* (:draw-cache window)
                ]
        (apply -reshape window args))
      (catch Exception e
        (println e)))))

(defn make-reshape-callback [window]
  (->ReshapeCallback window))

(declare email-image)

(defmacro defcallback [name params & body]
  (let [ret-type (-> name
                     meta
                     :tag)
        param-types (map #(-> % meta :tag) params)
        substitutions {'double Double/TYPE
                       'int Integer/TYPE
                       'void Void/TYPE}
       param-types (map #(get substitutions % %) param-types)]
    `(do
       ;;rewrite so we can redef body
       (reify
         com.sun.jna.CallbackProxy
         (getParameterTypes [this#]
           (into-array Class  [~@param-types]))
         (getReturnType [this#]
           ~ret-type)
         (callback ^void [this# args#]
           (let [[~params] args#]
             ~@body))))
    ))


(defn find-first-under
  ([root [x y] [sx sy] test?]
   (let [[ox oy] (origin root)
         [width height] (bounds root)
         local-x (- x (+ sx ox))
         local-y (- y (+ sy oy))]
     (if (and (test? root)
              (< local-x
                 width)
              (>= local-x 0)
              (< local-y
                 height)
              (>= local-y 0))
       [[local-x local-y] root]
       (some #(find-first-under % [x y] [(+ sx ox)
                                         (+ sy oy)]
                                test?)
             (children root))))))

(defn find-all-under
  ([root [x y] [sx sy] test?]
   (let [[ox oy] (origin root)
         [width height] (bounds root)
         local-x (- x (+ sx ox))
         local-y (- y (+ sy oy))]
     (if (and (test? root)
              (< local-x
                 width)
              (>= local-x 0)
              (< local-y
                 height)
              (>= local-y 0))
       (into [[[local-x local-y] root]]
             (apply
              concat
              (map #(find-all-under % [x y] [(+ sx ox)
                                             (+ sy oy)]
                                    test?)
                   (children root))))
       (apply concat
              (map #(find-all-under % [x y] [(+ sx ox)
                                           (+ sy oy)]
                                      test?)
                   (children root)))))))


(defn -mouse-button-callback [window window-handle button action mods]
  (try
    (mouse-event @(:ui window) @(:mouse-position window) button (= 1 action) mods)
    (catch Exception e
      (println e)))

  #_(when (zero? button)
    (case action
      1 ;; mouse-down
      (do
        (try
          (mouse-down @(:ui window) @(:mouse-position window))
          (catch Exception e
            (println e))))

      0 ;; mouse-up
      (do
        (try
          (mouse-up @(:ui window) @(:mouse-position window))
          (catch Exception e
            (println e))))))
  (repaint! window))

(deftype MouseButtonCallback [window]
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
        (doall (apply -mouse-button-callback window args)))
      (catch Exception e
        (println e)))
    ))

(defn make-mouse-button-callback [window]
  (MouseButtonCallback. window))



(defn -scroll-callback [window window-handle offset-x offset-y]
  ;; a 2x multiplier felt better. I think it might have something to do with
  ;; retina display, but it's probably some other dumb thing
  (ui/scroll @(:ui window) [(* 2 offset-x) (* 2 offset-y)])

  (repaint! window))

(deftype ScrollCallback [window]
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
        (doall (apply -scroll-callback window args)))
      (catch Exception e
        (println e)))

    nil))

(defn make-scroll-callback [window]
  (ScrollCallback. window))


(defn -window-refresh-callback [window window-handle]
  (repaint! window))

(deftype WindowRefreshCallback [window]
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
        (apply -window-refresh-callback window args))
      (catch Exception e
        (println e)))
    ))

(defn make-window-refresh-callback [window]
  (WindowRefreshCallback. window))





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

(deftype CursorPosCallback [window]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer Double/TYPE Double/TYPE]))
  (getReturnType [_]
    void)
  (callback ^void [_ [window-handle x y]]

    (try
        (binding [*image-cache* (:image-cache window)
                  *font-cache* (:font-cache window)
                  *draw-cache* (:draw-cache window)]
          (doall (-cursor-pos-callback window window-handle x y)))
        (catch Exception e
          (println e)))
    nil))

(defn make-cursor-pos-callback [window]
  (CursorPosCallback. window))


(defn -key-callback [window window-handle key scancode action mods]
  #_(when-let [k (get keymap key)]
      (when (string? k)
        (let [native-keycode (get native-keycodes k)]
          (case action
            1
            (do
              (browser_send_key_event native-keycode (int 1) (.charAt k 0))
              (browser_send_key_event native-keycode (int 2) (.charAt k 0)))
            0
            (browser_send_key_event native-keycode (int 0) (.charAt k 0))))))
  (let [ui @(:ui window)]
    (ui/key-event ui key scancode action mods)
    (cond

      ;; paste
      (and (= key 86)
           (= action 1)
           (= mods 8))
      (let [nodes (->> (tree-seq (fn [n]
                                   true)
                                 children
                                 ui)
                       (filter #(satisfies? IClipboardPaste %)))]
        (when-let [s (glfw-call String glfwGetClipboardString window-handle)]
          (doseq [node nodes
                  ;; :when (not (satisfies? IFocus node))
                  ]
            (-clipboard-paste node s))))

      ;; cut
      (and (= key 88)
           (= action 1)
           (= mods 8))
      (let [node (->> (tree-seq (fn [n]
                                  true)
                                children
                                ui)
                      (filter #(satisfies? IClipboardCut %))
                      ;; maybe should be last?
                      first)]
        (when-let [s (-clipboard-cut node)]
          (println "settings clipboard" s)
          (glfw-call void glfwSetClipboardString window-handle s)))

      ;; copy
      (and (= key 67)
           (= action 1)
           (= mods 8))
      (ui/clipboard-copy ui)

      ;; special keys
      (or (= 1 action)
          (= 2 action))
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

(deftype KeyCallback [window]
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
        (doall (apply -key-callback window args)))
      (catch Exception e
        (println e)))
    ))

(defn make-key-callback [window]
  (KeyCallback. window))

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

(deftype CharacterCallback [window]
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
        (doall (apply -character-callback window args)))
      (catch Exception e
        (println e)))
    ))

(defn make-character-callback [window]
  (CharacterCallback. window))

(def quit? (atom false))
(def window-title "Promethian")
(defn init-resources! []
  "for development only"
  (.execute (.getBlockingMainQueueExecutor (com.apple.concurrent.Dispatch/getInstance))
            (fn []
              (.setContextClassLoader (Thread/currentThread) main-class-loader)
              (def init-ret (glfw-call Integer/TYPE glfwInit))
              (glfw-call void glfwWindowHint GLFW_VISIBLE (int 0))
              (def window (glfw-call Pointer glfwCreateWindow width height window-title nil nil))
              (glfw-call Void/TYPE glfwMakeContextCurrent window)

              (glEnable GL_TEXTURE_2D)
              (glPixelStorei GL_UNPACK_ALIGNMENT, 1) ;
              (defonce initialized-resources (init_resources))

              #_(defonce main-font (load-font "/System/Library/Fonts/Menlo.ttc" 22)))))


(defrecord GlfwWindow [render window callbacks projection-matrix ui mouse-position image-cache font-cache draw-cache]
  IWindow
  (init! [this]
    (let [window (glfw-call Pointer
                            glfwCreateWindow
                            width
                            height
                            window-title
                            com.sun.jna.Pointer/NULL
                            com.sun.jna.Pointer/NULL)
          this
          (assoc this
                 :window window
                 :projection-matrix (doto (Memory. (* 8 16))
                                      (.write 0 ^doubles (ortho-matrix 0 (* 1 width) (* 1 height) 0 -1 1)
                                              0 16))
                 :image-cache (atom {})
                 :font-cache (atom {})
                 :draw-cache (atom {})
                 :ui (atom nil)
                 :mouse-position (atom [0 0]))
          key-callback (make-key-callback this)
          character-callback (make-character-callback this)
          mouse-button-callback (make-mouse-button-callback this)
          reshape-callback (make-reshape-callback this)
          scroll-callback (make-scroll-callback this)
          window-refresh-callback (make-window-refresh-callback this)
          cursor-pos-callback (make-cursor-pos-callback this)]

      (let [m (Memory. 8)
            error (glfw-call Integer/TYPE glfwGetError m)]
        (when (not (zero? error))
          (let [s (.getPointer m 0)]
            (println "error description: " (.getString s 0) ))))

      (glfw-call Void/TYPE glfwMakeContextCurrent window)
      (glEnable GL_TEXTURE_2D)
      (glEnable GL_BLEND)
      (glBlendFunc GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA ) 

      (glPixelStorei GL_UNPACK_ALIGNMENT, (int 1)) ;

      (when (zero? (init_resources
                    (slurp (clojure.java.io/resource "shaders/v3f-t2f-c4f.vert"))
                    (slurp (clojure.java.io/resource "shaders/v3f-t2f-c4f.frag"))
                    (slurp (clojure.java.io/resource "shaders/selection.frag"))))
        (throw (Exception. "Error initializing resources!!")))
      (defonce main-font (load-font "/System/Library/Fonts/Menlo.ttc" 14))

      (glfw-call Pointer glfwSetCursorPosCallback window, cursor-pos-callback)
      (glfw-call Pointer glfwSetKeyCallback window key-callback)
      (glfw-call Pointer glfwSetCharCallback window character-callback)
      (glfw-call Pointer glfwSetMouseButtonCallback window mouse-button-callback)
      (glfw-call Pointer glfwSetFramebufferSizeCallback window reshape-callback)
      (glfw-call Pointer glfwSetScrollCallback window scroll-callback)
      (glfw-call Pointer glfwSetWindowRefreshCallback window window-refresh-callback)

      (glfw-call void glfwSetWindowPos window (int 850) (int 0))
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
                    cursor-pos-callback])
        (reshape! width height))))

  (reshape! [_ width height]
    (glfw-call Void/TYPE glfwMakeContextCurrent window)
    
    (jna/invoke void opengl/glViewport (int 0) (int 0) width height)

    (doto ^Memory projection-matrix
      (.write 0 ^doubles (ortho-matrix 0 width  height 0 -1 1)
              0 16))

    nil)
  
  (should-close? [_]
    (when window
      (glfw-call Boolean/TYPE glfwWindowShouldClose window)))
  (cleanup! [this]
    (glfw-call void glfwDestroyWindow window)
    (assoc this
           :window nil
           :callbacks nil
           :mouse-position nil
           :image-cache nil
           :font-cache nil
           :draw-cache nil
           :ui nil
           :projection-matrix nil))


  (repaint! [this]
    

    (glfw-call Void/TYPE glfwMakeContextCurrent window)

    (gl glMatrixMode (GL_PROJECTION)) ; // Switch to the projection matrix so that we can manipulate how our scene is viewed
    
    (gl glLoadIdentity)
    (gl glMultMatrixd (projection-matrix))

    (gl glMatrixMode (GL_MODELVIEW))

    (gl glClearColor ((float 1.0) (float 1.0) (float 1.0) (float 1.0)))
    (glClear GL_COLOR_BUFFER_BIT)
    (gl glLoadIdentity)
    ;; (glColor [0.1 0.5 0.5 1])
    (glColor DEFAULT-COLOR)

    (binding [*image-cache* image-cache
              *font-cache* font-cache
              *window* this
              *draw-cache* draw-cache]
      (let [to-render (swap! ui (fn [_]
                                   (render)))]
        (do
          (draw to-render))))
    (glfw-call Void/TYPE glfwSwapBuffers window)))

(declare run-helper)
(defonce window-chan (chan 1))
(defonce main-thread-chan (chan (dropping-buffer 100)))
(defn run [make-ui]
  (async/>!! window-chan (map->GlfwWindow {:render make-ui}))
  (.execute (.getNonBlockingMainQueueExecutor (com.apple.concurrent.Dispatch/getInstance))
            (fn []
              (try
                (run-helper window-chan)
                (catch Exception e
                  (println e))))))

(intern (the-ns 'membrane.ui) 'run run)



(defn run-sync [make-ui]
  ;; need to initialize swing/awt stuff for some reason
  (java.awt.Toolkit/getDefaultToolkit)

  (async/>!! window-chan (map->GlfwWindow {:render make-ui}))
  (.execute (.getBlockingMainQueueExecutor (com.apple.concurrent.Dispatch/getInstance))
            (fn []
              (try
                (run-helper window-chan)
                (catch Exception e
                  (println e))))))

(intern (the-ns 'membrane.ui) 'run-sync run-sync)


(defn run-helper [window-chan]
  (with-local-vars [windows #{}]
    (letfn [(init []
              (if (not= 1 (glfw-call Integer/TYPE glfwInit))
                false
                (do
                  (.setContextClassLoader (Thread/currentThread) main-class-loader)
                  (fix-press-and-hold!)
                  (glfw-call void glfwWindowHint GLFW_COCOA_RETINA_FRAMEBUFFER (int 0))
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
          (println "initialized")
          (add-windows!)

          (loop []
            (wait)

            #_(binding [*window* (first (var-get windows))]
              (loop [work (async/poll! main-thread-chan)]
                (when work
                  (work)
                  (recur (async/poll! main-thread-chan)))))

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
          (println "cleaning up")
          (cleanup)))))


  )

(defn bstr [num]
  ;;String.format("%16s", Integer.toBinaryString(1)).replace(' ', '0')
  (-> num
      (java.lang.Integer/toBinaryString)
      (->> (format "%16s"))
      (.replace " " "0"))
  
  
  )


(defn sleep [secs]
  (java.lang.Thread/sleep (long (* secs 1000))))

(defn -main [& args]
  (run-sync #(ui/label "hello world!")))
