(ns membrane.ios
  (:require [membrane.ui :as ui
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
            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype :as dtype]
            tech.v3.datatype.ffi.graalvm-runtime
            [tech.v3.datatype.native-buffer :as native-buffer]

            [membrane.example.todo :as td]
            [membrane.component :as component]

            )
  (:import tech.v3.datatype.native_buffer.NativeBuffer
           java.nio.ByteBuffer)
  
  (:gen-class))

(set! *warn-on-reflection* true)

(def membraneskialib-fns
  {

   :skia_init {:rettype :pointer}


   :skia_init_cpu {:rettype :pointer
                   :argtypes [['width :int32]
                              ['height :int32]]}

   :skia_clear {:rettype :void
                :argtypes [['resources :pointer]]}

   :skia_flush {:rettype :void
                :argtypes [['resources :pointer]]}

   :skia_cleanup {:rettype :void
                  :argtypes [['resources :pointer]]}

   :skia_set_scale {:rettype :void
                    :argtypes [['resource :pointer]
                               ['sx :float32]
                               ['sy :float32]]}

   :skia_render_line {:rettype :void
                      :argtypes [['resource :pointer]
                                 ['font :pointer]
                                 ['text :pointer]
                                 ['text_length :int32]
                                 ['x :float32]
                                 ['y :float32]]}

   ;; void skia_next_line(SkiaResource* resource, SkFont* font);
   :skia_next_line {:rettype :void
                    :argtypes [['resource :pointer]
                               ['font :pointer]]}
   ;;     float skia_line_height(SkFont* font);
   :skia_line_height {:rettype :float32
                      :argtypes [['font :pointer]]}
   

   ;; float skia_advance_x(SkFont* font, const char* text, int text_length);
   :skia_advance_x {:rettype :float32
                    :argtypes [['font :pointer]
                               ['text :pointer]
                               ['length :int32]]}
   
   ;; void skia_render_cursor(SkiaResource* resource, SkFont * font, const char* text, int text_length , int cursor);
   :skia_render_cursor {:rettype :void
                        :argtypes [['resource :pointer]
                                   ['font :pointer]
                                   ['text :pointer]
                                   ['text_length :int32]
                                   ['cursor :int32]]}

   ;; void skia_render_selection(SkiaResource* resource, SkFont * font, const char* text, int text_length , int selection_start, int selection_end);
   :skia_render_selection {:rettype :void
                           :argtypes [['resource :pointer]
                                      ['font :pointer]
                                      ['text :pointer]
                                      ['text_length :int32]
                                      ['selection_start :int32]
                                      ['selection_end :int32]]}

   ;;     int skia_index_for_position(SkFont* font, const char* text, int text_length, float px);
   :skia_index_for_position {:rettype :int32
                             :argtypes [['font :pointer]
                                        ['text :pointer]
                                        ['text_length :int32]
                                        ['px :float32]]}

   ;;     void skia_text_bounds(SkFont* font, const char* text, int text_length, float* ox, float* oy, float* width, float* height);
   :skia_text_bounds {:rettype :void
                      :argtypes [['font :pointer?]
                                 ['text :pointer]
                                 ['text_length :int32]
                                 ['ox :pointer]
                                 ['oy :pointer]
                                 ['width :pointer]
                                 ['height :pointer]]}

   :skia_save {:rettype :void
               :argtypes [['resource :pointer]]}

   :skia_restore {:rettype :void
                  :argtypes [['resource :pointer]]}

   :skia_translate {:rettype :void
                    :argtypes [['resource :pointer]
                               ['tx :float32]
                               ['ty :float32]]}

   :skia_clip_rect {:rettype :void
                    :argtypes [['resource :pointer]
                               ['ox :float32]
                               ['oy :float32]
                               ['width :float32]
                               ['height :float32]]}

   :skia_load_image {:rettype :pointer
                     :argtypes [['path :pointer]]}

   :skia_load_image_from_memory {:rettype :pointer
                                 :argtypes [['buffer :pointer]
                                            ['buffer_length :int32]]}

   :skia_draw_image {:rettype :void
                     :argtypes [['resource :pointer]
                                ['image :pointer]]}

   :skia_draw_image_rect {:rettype :void
                          :argtypes [['resource :pointer]
                                     ['image :pointer]
                                     ['w :float32]
                                     ['h :float32]]}
   :skia_image_bounds {:rettype :void
                       :argtypes [['image :pointer]
                                  ['width :pointer]
                                  ['height :pointer]]}

   :skia_draw_path {:rettype :void
                    :argtypes [['resource :pointer]
                               ['points :pointer]
                               ['count :int32]]}

   :skia_draw_polygon {:rettype :void
                       :argtypes [['resource :pointer]
                                  ['points :pointer]
                                  ['count :int32]]}

   :skia_draw_rounded_rect {:rettype :void
                            :argtypes [['resource :pointer]
                                       ['width :float32]
                                       ['height :float32]
                                       ['radius :float32]]}

   :skia_load_font2 {:rettype :pointer
                     :argtypes [['fontfilename :pointer?]
                                ['fontsize :float32]
                                ['weight :int32]
                                ['width :int32]
                                ['slant :int32]]}


   :skia_push_paint {:rettype :void
                     :argtypes [['resource :pointer]]}

   :skia_pop_paint {:rettype :void
                    :argtypes [['resource :pointer]]}

   :skia_set_color {:rettype :void
                    :argtypes [['resource :pointer]
                               ['r :float32]
                               ['g :float32]
                               ['b :float32]
                               ['a :float32]]}

   :skia_set_style {:rettype :void
                    :argtypes [['resource :pointer]
                               ['style :int8]]}

   :skia_set_stroke_width {:rettype :void
                           :argtypes [['resource :pointer]
                                      ['stroke_width :float32]]}

   :skia_set_alpha {:rettype :void
                    :argtypes [['resource :pointer]
                               ['a :int16]]}
   :skia_offscreen_buffer {:rettype :pointer
                           :argtypes [['resource :pointer]
                                      ['width :int32]
                                      ['height :int32]]}

   :skia_offscreen_image {:rettype :pointer
                          :argtypes [['resource :pointer]]}

   ,})

(dt-ffi/define-library-interface
  membraneskialib-fns)

;; (def skia-buf (native-buffer/malloc 4096))
(def ^:dynamic *paint* {})

(defprotocol IDraw
  :extend-via-metadata true
  (draw [this]))

(ui/add-default-draw-impls! IDraw #'draw)


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

;; (def GL_UNPACK_ALIGNMENT (int 0x0CF5) )
;; (def GL_COLOR_BUFFER_BIT (int 0x00004000))
;; (def GL_STENCIL_BUFFER_BIT (int 0x00000400))
;; (def GLFW_VISIBLE (int 0x00020004))


;; (def GLFW_MOD_SHIFT 0x0001)
;; (def GLFW_MOD_CONTROL 0x0002)
;; (def GLFW_MOD_ALT 0x0004)
;; (def GLFW_MOD_SUPER 0x0008)
;; (def GLFW_MOD_CAPS_LOCK 0x0010)
;; (def GLFW_MOD_NUM_LOCK 0x0020)


(def ^:dynamic *image-cache* nil)
(def ^:dynamic *font-cache* (atom {}))
(def ^:dynamic *draw-cache* nil)
(def ^:dynamic *skia-resource* nil)
(def ^:dynamic *window* nil)

(def DEFAULT-COLOR [0.13 0.15 0.16 1])
(declare render-text)
(declare text-bounds)
(declare load-font)


(comment
  (run #'test-skia))

(defmacro save-canvas [& args]
  `(try
     (skia_save *skia-resource*)
     ~@args
     (finally
       (skia_restore *skia-resource*))))

(defmacro push-paint [& args]
  `(try
     (skia_push_paint *skia-resource*)
     ~@args
     (finally
       (skia_pop_paint *skia-resource*))))

(def skia-style {:membrane.ui/style-fill (byte 0)
                 :membrane.ui/style-stroke (byte 1)
                 :membrane.ui/style-stroke-and-fill (byte 2)})

(defn- skia-set-style [skia-resource style]
  (let [style-arg (skia-style style)]
    (assert style-arg (str "Invalid Style: " style "."))
    (skia_set_style skia-resource style-arg)))

(defn- skia-set-stroke-width [skia-resource width]
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


(defn- skia-set-color [skia-resource [r g b a]]
  (skia_set_color skia-resource (float r) (float g) (float b) (if a
                                                                (float a)
                                                                (float 1))))

(defn- skia-set-alpha [skia-resource alpha]
  (skia_set_alpha skia-resource (unchecked-byte (* alpha 255))))


(def font-dir "/System/Library/Fonts/")
(defn- get-font [font]
  (let [font-ptr
        (if-let [font-ptr (get @*font-cache* font)]
          font-ptr
          (let [font-name (or (:name font)
                              (:name ui/default-font))
                font-path (when font-name
                            (if (.startsWith ^String font-name "/")
                              font-name
                              (str font-dir font-name)))
                font-path (when font-path
                            (if (.exists (clojure.java.io/file font-path))
                              font-path
                              (do
                                (println font-path " does not exist!")
                                (:name ui/default-font))))]
            (let [font-size (or (:size font)
                                (:size ui/default-font))
                  font-ptr (load-font font-path font-size)]
              (swap! *font-cache* assoc font font-ptr)
              font-ptr)))]
    font-ptr))



(def byte-array-class (type (byte-array 0)))
(defn- label-draw [{:keys [text font] :as label}]
  (let [lines (clojure.string/split-lines text)
        font-ptr (get-font font)]
    (save-canvas
     (doseq [line lines
             :let [buf (dt-ffi/string->c line)
                   buf-length (native-buffer/native-buffer-byte-len buf)]]
       ;; (.write ^Memory skia-buf 0 line-bytes 0 (alength ^bytes line-bytes))
       
       (skia_next_line *skia-resource* font-ptr)
       (skia_render_line *skia-resource* font-ptr buf buf-length (float 0) (float 0))))))


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
        (let [image (skia_load_image image-path)]
          (swap! *image-cache* assoc image-path image)
          image)
        (do
          (println image-path " does not exist!")
          nil)))))

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
            image (skia_load_image_from_memory
                   (dtype/make-container :native-heap :int8
                                         bytes)
                   (alength ^bytes bytes))]
        (swap! *image-cache* assoc image-url image)
        image))))


(extend-protocol ImageFactory
  (Class/forName "[B")
  (get-image-texture [bytes]
    (if-let [image (get @*image-cache* bytes)]
      image
      (let [image (skia_load_image_from_memory
                   (dtype/make-container :native-heap :int8
                                         bytes)
                   (alength ^bytes bytes))]
        (swap! *image-cache* assoc bytes image)
        image))))

(defn- image-draw [{:keys [image-path size opacity] :as image}]
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

(declare image-cache)
(defn image-size [img-source]
  (let [width (dt-ffi/make-ptr :int32 0)
        height (dt-ffi/make-ptr :int32 0)
        image (if *image-cache*
                (get-image-texture img-source)
                (binding [*image-cache* image-cache]
                  (get-image-texture img-source)))]
    (when image
      (skia_image_bounds image width height)
      [(nth width 0) (nth height 0)])))

(reset! membrane.ui/image-size* (memoize image-size))

(extend-type membrane.ui.Translate
  IDraw
  (draw [this]
    (save-canvas
     (skia_translate  *skia-resource* (float (:x this)) (float (:y this)))
     (draw (:drawable this)))))



(defn- skia-line-height [font]
  (skia_line_height (get-font font)))

(defn- skia-advance-x [font text]
  (let [str-buf (dt-ffi/string->c text)]
    ;; (.write ^Memory skia-buf 0 line-bytes 0 (alength ^bytes line-bytes))
    (skia_advance_x (get-font font) str-buf (count str-buf))))

(defn- text-selection-draw [{:keys [text font]
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
               line-count (count line)]
           (when (< selection-start (count line))
             (let [str-buf (dt-ffi/string->c line)]
               (skia_render_selection *skia-resource* font-ptr str-buf (count str-buf) (int (max 0 selection-start)) (int (min selection-end
                                                                                                                               line-count)))))
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

(defn- text-cursor-draw [{:keys [text font cursor]
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
               ;;line-bytes (.getBytes ^String line "utf-8")
               line-count (count line)
               ]
           ;;(.write ^Memory skia-buf 0 line-bytes 0 (alength ^bytes line-bytes))
           (when (< cursor (inc line-count))
             (let [line (first lines)
                   str-buf (dt-ffi/string->c line)]
               (skia_render_cursor *skia-resource* font-ptr str-buf (count str-buf) (int (max 0 cursor)))))
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

(extend-type membrane.ui.Path
  IDraw
  (draw [this]
    (let [points (:points this)
          num-points (* 2 (count points))
          points-buf (dtype/make-container :native-heap :float32
                                           (into
                                            []
                                            cat
                                            points))
          #_#_points-buf (reduce (fn [buf [x y]]
                               (-> ^ByteBuffer buf
                                   (.putFloat x)
                                   (.putFloat y)))
                             (ByteBuffer/allocateDirect (* 4 num-points))
                             points)]
      (push-paint
       (skia_draw_path *skia-resource* points-buf (int num-points))))))

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


(defn- scissor-draw [scissor-view]
  (save-canvas
   (let [[ox oy] (:offset scissor-view)
         [w h] (:bounds scissor-view)]
     (skia_clip_rect *skia-resource* (float ox) (float oy) (float w) (float h))
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

(defn- text-bounds [font-ptr text]
  (assert font-ptr)
  (assert text "Can't get font size of nil text")
  
  (let [x (native-buffer/malloc 4)
        y (native-buffer/malloc 4)
        width (native-buffer/malloc 4)
        height (native-buffer/malloc 4)
        text-buf (dt-ffi/string->c text)
        text-buf-length (native-buffer/native-buffer-byte-len text-buf)]
    (skia_text_bounds font-ptr text-buf text-buf-length x y width height)
    [(native-buffer/read-float x)
     (native-buffer/read-float y)
     (native-buffer/read-float width)
     (native-buffer/read-float height)]))


(defn- index-for-position [font text px py]
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
      (let [line (nth lines line-no)
            str-buf (dt-ffi/string->c line)]
        (apply +
               line-no
               (skia_index_for_position font-ptr str-buf (count str-buf) (float px))
               (map count (take line-no lines)))))))

;; (intern (the-ns 'membrane.ui) 'index-for-position index-for-position)
(reset! membrane.ui/index-for-position* index-for-position)

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

(defn- load-font
  ([path size]
   (load-font path size nil nil nil))
  ([path size weight width slant]
   (assert (or (string? path)
               (nil? path)))
   (let [weight (get font-weights weight
                     (or weight -1))
         width (get font-widths width
                    (or width -1))
         slant (get font-slants slant
                    (or slant -1))
         font-ptr (skia_load_font2 path (float size) (int weight) (int width) (int slant))]
     (assert font-ptr (str "unable to load font: " path " " size))

     font-ptr)))

(def ^:dynamic *already-drawing* nil)


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
                      resource (skia_offscreen_buffer *skia-resource*
                                                           (int (* xscale img-width))
                                                           (int (* yscale img-height)))
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
         (skia_translate  *skia-resource* (float (- padding)) (float (- padding)))
         (skia_draw_image_rect *skia-resource* img (float img-width) (float img-height)))))))

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

(defprotocol IWindow
  (init! [_])
  (reshape! [_ width height])
  (should-close? [_])
  (cleanup! [_])
  (repaint! [_]))


(defn- -reshape
  ([window window-handle width height]
   (reshape! window width height)))

(defn- -mouse-button-callback [window window-handle button action mods]
  (try
    (mouse-event @(:ui window) @(:mouse-position window) button (= 1 action) mods)
    (catch Exception e
      (println e)))

  (repaint! window))


(defn- -scroll-callback [window window-handle offset-x offset-y]
  ;; a 2x multiplier felt better. I think it might have something to do with
  ;; retina display,
  ;; but it's probably some other dumb thing
  (ui/scroll @(:ui window) [(* 2 offset-x) (* 2 offset-y)] @(:mouse-position window))

  (repaint! window))


(defn- -window-refresh-callback [window window-handle]
  (repaint! window))

(defn- -drop-callback [window window-handle paths]
  (try
    (ui/drop @(:ui window) (vec paths) @(:mouse-position window))
    (catch Exception e
      (println e)))

  (repaint! window))

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

(def key-action-map
  {1 :press
   2 :repeat
   0 :release})
(defn- -key-callback [window window-handle key scancode action mods]
  (let [action (get key-action-map action :unknown)
        ui @(:ui window)]
    (ui/key-event ui key scancode action mods)
    (cond

      ;; ;; paste
      ;; (and (= key 86)
      ;;      (= action :press)
      ;;      (= mods 8))
      ;; (let [nodes (->> (tree-seq (fn [n]
      ;;                              true)
      ;;                            children
      ;;                            ui)
      ;;                  (filter #(satisfies? IClipboardPaste %)))]
      ;;   (when-let [s (glfw-call String glfwGetClipboardString window-handle)]
      ;;     (doseq [node nodes]
      ;;       (-clipboard-paste node s))))

      ;; ;; cut
      ;; (and (= key 88)
      ;;      (= action :press)
      ;;      (= mods 8))
      ;; (let [node (->> (tree-seq (fn [n]
      ;;                             true)
      ;;                           children
      ;;                           ui)
      ;;                 (filter #(satisfies? IClipboardCut %))
      ;;                 ;; maybe should be last?
      ;;                 first)]
      ;;   (when-let [s (-clipboard-cut node)]
      ;;     (glfw-call void glfwSetClipboardString window-handle s)))

      ;; ;; copy
      ;; (and (= key 67)
      ;;      (= action :press)
      ;;      (= mods 8))
      ;; (ui/clipboard-copy ui)

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

(defn- -character-callback [window window-handle codepoint]
  #_(let [k (String. ^bytes (int->bytes codepoint) "utf-32")
        ui @(:ui window)]
    (try
      (ui/key-press ui k)
      (catch Exception e
        (println e))))

  (repaint! window))

(def quit? (atom false))

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

(def counter1 (atom 0))
(def counter2 (atom 0))
(def counter3 (atom 0))

(def main-font (ui/font nil 32))

(def image-cache (atom {}))
(def font-cache (atom {}))
(def window {:window-content-scale (atom [2 2])})
(def draw-cache (atom {}))

;; (def ui (atom nil))


;; (def membrane-state (atom {:todos
;;                            [{:complete? false
;;                              :description "first"}
;;                             {:complete? false
;;                              :description "second"}
;;                             {:complete? true
;;                              :description "third"}]}))
;; (component/defui main-app [{:keys [todos]}]
;;   (ui/translate 10 50
;;                 (td/todo-app {:todos todos})))

;; (def membrane-view (component/make-app #'main-app membrane-state))

#_(defn membrane-view []
  (into []
        (map (fn [[x y]]
               (ui/translate (* 50 x) (* 50 y)
                (ui/with-style :membrane.ui/style-stroke
                  (ui/rectangle 50 50)))))
        (for [x (range 40)
              y (range 40)]
          [x y])))

#_(defn membrane_draw [skia-resource]
  (let [view (reset! ui (membrane-view))]
    (binding [*skia-resource* skia-resource
              *image-cache* image-cache
              *font-cache* font-cache
              *window* window
              *draw-cache* draw-cache
              ;; turn off draw cache to avoid memory leaks for now
              *already-drawing* true]
      (draw view))))

(defn draw! [ctx view]
  (binding [*skia-resource* ctx
            *image-cache* image-cache
            *font-cache* font-cache
            *window* window
            *draw-cache* draw-cache
            ;; turn off draw cache to avoid memory leaks for now
            *already-drawing* true]
    (draw view)))

;; (defn membrane_touch_ended [x y]
;;   (try

;;     (ui/mouse-down @ui [x y])

    
;;     (catch Exception e
;;       (println e)))

;;   (let [focus (-> @membrane-state
;;                   :membrane.component/context
;;                   :focus)]
;;     (if focus
;;       1
;;       0)))

;; (defn membrane_insert_text [ptr]
;;   (let [s (dt-ffi/c->string ptr)]
;;     (ui/key-press @ui s)))

;; (defn membrane_delete_backward []
;;   (ui/key-press @ui :backspace))



(comment
  (defn expose-ios-functions []
    (with-bindings {#'*compile-path* "library/classes"}
      ((requiring-resolve 'tech.v3.datatype.ffi.graalvm/expose-clojure-functions)
       {#'membrane_start {:rettype :void}
        #'membrane_draw {:rettype :void
                         :argtypes [['skia-resource :pointer]]}
        #'membrane_touch_ended {:rettype :int32
                                :argtypes [['x :float64]
                                           ['y :float64]]}
        #'membrane_delete_backward {:rettype :void}
        #'membrane_insert_text {:rettype :void
                                :argtypes [['s :pointer]]}}
     
     
       'com.phronemophobic.membrane.ios.interface nil))))
