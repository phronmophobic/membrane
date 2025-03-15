(ns membrane.skia.paragraph
  (:require[membrane.skia.impl.paint :as paint]
           [tech.v3.datatype :as dtype]
           [tech.v3.datatype.struct :as dt-struct]
           [tech.v3.datatype.ffi :as dt-ffi]
           [tech.v3.datatype.native-buffer :as native-buffer]
           [membrane.ui :as ui])
  (:import java.util.function.Supplier
           java.lang.ref.Cleaner))

(set! *warn-on-reflection* true)

(if (= "true" (System/getProperty "membrane.ios"))
  (require '[membrane.ios :as backend])
  (require '[membrane.skia :as backend]))

(def ^:private void Void/TYPE)
(def cleaner (delay (Cleaner/create)))

(def ffi-buf*
  (ThreadLocal/withInitial
   (reify
     Supplier
     (get [_]
       (native-buffer/malloc 4096
                             {:uninitialized? true
                              :resource-type :auto})))))
(defmacro ffi-buf []
  ` ^tech.v3.datatype.native_buffer.NativeBuffer
  (.get  ^ThreadLocal ffi-buf*))
(defmacro ffi-buf-size []
  `(.size (ffi-buf)))


(def paragraph-fns
  {
   :skia_SkRefCntBase_ref {:rettype :void :argtypes '[[o :pointer]]}
   :skia_SkRefCntBase_unref {:rettype :void :argtypes '[[o :pointer]]}
   :skia_SkString_delete {:rettype :void :argtypes '[[sk-string :pointer]]}
   :skia_SkString_make_utf8 {:rettype :pointer? :argtypes '[[buf :pointer] [len :int32]]}
   :skia_SkColor4f_make {:rettype :int32 :argtypes '[[red :float32] [green :float32] [blue :float32] [alpha :float32] ]}
   :skia_FontStyle_delete {:rettype :void :argtypes '[[style :pointer]]}
   :skia_FontStyle_make {:rettype :pointer? :argtypes '[[make :int32] [width :int32] [slant :int32] ]}
   :skia_ParagraphBuilder_delete {:rettype :void :argtypes '[[pb :pointer]]}
   :skia_ParagraphBuilder_make {:rettype :pointer? :argtypes '[[paragraph-style :pointer]]}
   :skia_ParagraphBuilder_pushStyle {:rettype :void :argtypes '[[builder :pointer] [style :pointer]]}
   :skia_ParagraphBuilder_pop {:rettype :void :argtypes '[[builder :pointer]]}
   :skia_ParagraphBuilder_addText {:rettype :void :argtypes '[[builder :pointer] [text :pointer] [len :int32]]}
   :skia_ParagraphBuilder_addPlaceholder {:rettype :void :argtypes '[[builder :pointer] [style :pointer]]}
   :skia_ParagraphBuilder_addPlaceholder2 {:rettype :void :argtypes '[[builder :pointer] [width :float32] [height :float32] [alignment :int32] [baseline :int32] [offset :float32]]}
   :skia_Paragraph_delete {:rettype :void :argtypes '[[p :pointer]]}
   :skia_ParagraphBuilder_build {:rettype :pointer? :argtypes '[[builder :pointer]]}
   :skia_ParagraphBuilder_reset {:rettype :void :argtypes '[[builder :pointer]]}
   :skia_TextStyle_delete {:rettype :void :argtypes '[[style :pointer]]}
   :skia_TextStyle_make {:rettype :pointer}
   :skia_TextStyle_setColor {:rettype :pointer? :argtypes '[[style :pointer] [color :int32]]}
   :skia_TextStyle_setForeground {:rettype :void :argtypes '[[style :pointer] [foreground :pointer]]}
   :skia_TextStyle_clearForegroundColor {:rettype :void :argtypes '[[style :pointer]]}
   :skia_TextStyle_setBackgroundColor {:rettype :void :argtypes '[[style :pointer] [background :pointer]]}
   :skia_TextStyle_clearBackgroundColor {:rettype :void :argtypes '[[style :pointer]]}
   :skia_TextStyle_setDecoration {:rettype :void :argtypes '[[style :pointer] [decoration :int32]]}
   :skia_TextStyle_setDecorationMode {:rettype :void :argtypes '[[style :pointer] [mode :int32]]}
   :skia_TextStyle_setDecorationStyle {:rettype :void :argtypes '[[style :pointer] [td-style :int32]]}
   :skia_TextStyle_setDecorationColor {:rettype :void :argtypes '[[style :pointer] [color :int32]]}
   :skia_TextStyle_setDecorationThicknessMultiplier {:rettype :void :argtypes '[[style :pointer] [n :float32]]}
   :skia_TextStyle_setFontStyle {:rettype :void :argtypes '[[style :pointer] [font-style :pointer]]}
   :skia_TextStyle_addShadow {:rettype :void :argtypes '[[style :pointer] [shadow :pointer]]}
   :skia_TextStyle_resetShadows {:rettype :void :argtypes '[[style :pointer]]}
   :skia_TextStyle_setFontSize {:rettype :void :argtypes '[[style :pointer] [font-size :float32]]}
   :skia_TextStyle_setFontFamilies {:rettype :void :argtypes '[[style :pointer] [families :pointer] [num-families :int32]]}
   :skia_TextStyle_setBaselineShift {:rettype :void :argtypes '[[style :pointer] [shift :float32]]}
   :skia_TextStyle_setHeight {:rettype :void :argtypes '[[style :pointer] [height :float32]]}
   :skia_TextStyle_setHeightOverride {:rettype :void :argtypes '[[style :pointer] [height-override :int32]]}
   :skia_TextStyle_setHalfLeading {:rettype :void :argtypes '[[style :pointer] [half-leading :int32]]}
   :skia_TextStyle_setLetterSpacing {:rettype :void :argtypes '[[style :pointer] [letter-spacing :float32]]}
   :skia_TextStyle_setWordSpacing {:rettype :void :argtypes '[[style :pointer] [word-spacing :float32]]}
   :skia_TextStyle_setTypeface {:rettype :void :argtypes '[[style :pointer] [face :pointer]]}
   :skia_TextStyle_setLocale {:rettype :void :argtypes '[[style :pointer] [locale :pointer]]}
   :skia_TextStyle_setTextBaseline {:rettype :void :argtypes '[[style :pointer] [baseline :int32]]}
   :skia_TextStyle_setPlaceholder {:rettype :void :argtypes '[[style :pointer]]}
   :skia_ParagraphStyle_delete {:rettype :void :argtypes '[[ps :pointer]]}
   :skia_ParagraphStyle_make {:rettype :pointer?}
   :skia_ParagraphStyle_turnHintingOff {:rettype  :void :argtypes '[[style :pointer]]}
   :skia_ParagraphStyle_setStrutStyle {:rettype :void :argtypes '[[style :pointer] [strut-style :pointer]]}
   :skia_ParagraphStyle_setTextStyle {:rettype :void :argtypes '[[style :pointer] [text-style :pointer]]}
   :skia_ParagraphStyle_setTextDirection {:rettype :void :argtypes '[[style :pointer] [direction :int32]]}
   :skia_ParagraphStyle_setTextAlign {:rettype :void :argtypes '[[style :pointer] [align :int32]]}
   :skia_ParagraphStyle_setMaxLines {:rettype :void :argtypes '[[style :pointer] [max-lines :int32]]}
   :skia_ParagraphStyle_setEllipsis {:rettype :void :argtypes '[[style :pointer] [ellipsis :pointer]]}
   :skia_ParagraphStyle_setHeight {:rettype :void :argtypes '[[style :pointer] [height :float32]]}
   :skia_ParagraphStyle_setTextHeightBehavior {:rettype :void :argtypes '[[style :pointer] [text-height-behavior :int32]]}
   :skia_ParagraphStyle_setReplaceTabCharacters {:rettype :void :argtypes '[[style :pointer] [value :int32]]}
   :skia_Paragraph_getMaxWidth {:rettype :float32 :argtypes '[[paragraph :pointer]]}
   :skia_Paragraph_getHeight {:rettype :float32 :argtypes '[[paragraph :pointer]]}
   :skia_Paragraph_getMinIntrinsicWidth {:rettype :float32 :argtypes '[[paragraph :pointer]]}
   :skia_Paragraph_getMaxIntrinsicWidth {:rettype :float32 :argtypes '[[paragraph :pointer]]}
   :skia_Paragraph_getAlphabeticBaseline {:rettype :float32 :argtypes '[[paragraph :pointer]]}
   :skia_Paragraph_getIdeographicBaseline {:rettype :float32 :argtypes '[[paragraph :pointer]]}
   :skia_Paragraph_getLongestLine {:rettype :float32 :argtypes '[[paragraph :pointer]]}
   :skia_Paragraph_didExceedMaxLines {:rettype :int32 :argtypes '[[paragraph :pointer]]}
   :skia_Paragraph_layout {:rettype :void :argtypes '[[paragraph :pointer] [width :float32]]}
   :skia_Paragraph_paint {:rettype :void :argtypes '[[paragraph :pointer] [resource :pointer] [x :float32] [y :float32]]}
   :skia_Paragraph_getRectsForRange {:rettype :int32 :argtypes '[[paragraph :pointer] [start :int32] [end :int32] [rect-style-height :int32] [rect-style-width :int32] [buf :pointer] [max :int32]]}
   :skia_Paragraph_getRectsForPlaceholders {:rettype :int32 :argtypes '[[paragraph :pointer] [buf :pointer] [max :int32]]}
   :skia_Paragraph_getGlyphPositionAtCoordinate {:rettype :void :argtypes '[[paragraph :pointer] [dx :float32] [dy :float32] [*pos :pointer] [*affinity :pointer]]}
   :skia_count_font_families {:rettype :int32 :argtypes '[]}
   :skia_get_family_name {:rettype :void :argtypes '[[family-name :pointer] [len :int64] [index :int32]]} 

   ,})

(dt-ffi/define-library-interface
  paragraph-fns)



(def
  ^:private
  text-decoration-ints
  {:text-decoration/no-decoration 0
   :text-decoration/underline 0x1
   :text-decoration/overline 0x2
   :text-decoration/line-through 0x4})

(defn- text-decoration->int [dec]
  (reduce
   (fn [n k]
     (bit-or n (text-decoration-ints k)))
   (int 0)
   dec))

(defn- pointer? [p]
  (dt-ffi/convertible-to-pointer? p))

;; `ref` is already taken
(defn- inc-ref [o]
  (skia_SkRefCntBase_ref o))
(defn- unref [o]
  (skia_SkRefCntBase_unref o))

(defmacro add-cleaner [type p]
  (if false ;; ('#{SkString} type)
    p
    (let [delete-sym (symbol (str "skia_" type "_delete"))]
      `(let [p# ~p
             ptr# (dt-ffi/pointer->address p#)]
         (.register ^Cleaner @cleaner p#
                    (fn []
                      (~delete-sym (dt-ffi/->pointer ptr#))))
         p#))))

(defn- ref-count [p name]
  (let [ptr (dt-ffi/pointer->address p)]
    (.register ^Cleaner @cleaner p
               (fn []
                 (skia_SkRefCntBase_unref (dt-ffi/->pointer ptr))))
    p))

;; SkString* skia_make_skstring_utf8(char *s, int len)
(defn- ->SkString [^String s]
  (let [buf (dt-ffi/string->c s)
        len (dec (count buf))]
    (add-cleaner
     SkString
     (skia_SkString_make_utf8 buf len))))


;; SkColor skia_SkColor4f_make(float red, float green, float blue, float alpha)
(defn- skia-SkColor4f-make [r g b a]
  (skia_SkColor4f_make
   (float r)
   (float g)
   (float b)
   (float a)))

;; copied from membrane.skia to avoid requiring membrane.skia
;; avoiding requiring membrane.skia so that paragraphs can be used
;; by ios.
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


(defn- skia-FontStyle-make [weight width slant]
  (let [weight (get font-weights weight
                    (or weight -1))
        width (get font-widths width
                   (or width -1))
        slant (get font-slants slant
                   (or slant -1))]
    (assert (or (= -1 weight)
                (>= weight 0)))
    (assert (or (= -1 width)
                (#{1 2 3 4 5 6 7 8 9} width)))
    (assert (or (= -1 slant)
                (#{1 2 3} slant)))
    (add-cleaner
     FontStyle
     (skia_FontStyle_make (int weight)
                          (int width)
                          (int slant)))))

(defn- ->FontStyle [{:font-style/keys [weight width slant]}]
  (skia-FontStyle-make weight width slant))

#_(defn- fill-buf-with-ptrs [buf ptrs]
    (loop [offset 0
           ptrs (seq ptrs)]
      (if ptrs
        (let [ptr (first ptrs)]
          (.setPointer ^Memory buf offset ptr)
          (recur (+ offset Native/POINTER_SIZE)
                 (next ptrs)))))
    nil)

(defn- skia-ParagraphBuilder-make [paragraph-style]
  (assert (pointer? paragraph-style))
  
  (add-cleaner
   ParagraphBuilder
   (skia_ParagraphBuilder_make paragraph-style)))

(defn- skia-ParagraphBuilder-pushStyle [builder style]
  (assert (pointer? builder))
  (assert (pointer? style))
  (skia_ParagraphBuilder_pushStyle builder style)
  builder)

(defn- skia-ParagraphBuilder-pop [builder]
  (assert (pointer? builder))
  (skia_ParagraphBuilder_pop builder)
  builder)

(defn- skia-ParagraphBuilder-addText [builder ^String s]
  (assert (pointer? builder))
  (let [buf (dt-ffi/string->c s)
        len (dec (count buf))]
    (skia_ParagraphBuilder_addText builder buf len)

    builder))

(defn- skia-ParagraphBuilder-addPlaceholder [builder style]
  (assert (pointer? builder))
  (assert (pointer? style))
  (skia_ParagraphBuilder_addPlaceholder builder style)
  builder)


(def
  ^:private
  placeholder-alignments
  [ ;; Match the baseline of the placeholder with the baseline.
   :baseline
   ;; Align the bottom edge of the placeholder with the baseline such that the
   ;; placeholder sits on top of the baseline.
   :above-baseline
   
   ;; Align the top edge of the placeholder with the baseline specified in
   ;; such that the placeholder hangs below the baseline.
   :below-baseline

   ;; Align the top edge of the placeholder with the top edge of the font.
   ;; When the placeholder is very tall, the extra space will hang from
   ;; the top and extend through the bottom of the line.
   :top

   ;; Align the bottom edge of the placeholder with the top edge of the font.
   ;; When the placeholder is very tall, the extra space will rise from
   ;; the bottom and extend through the top of the line.
   :bottom

   ;; Align the middle of the placeholder with the middle of the text. When the
   ;; placeholder is very tall, the extra space will grow equally from
   ;; the top and bottom of the line.
   :middle])

(def ^:private ->placeholder-alignment
  (into {}
        (map-indexed
         (fn [i kw]
           [kw (int i)]))
        placeholder-alignments))

(def ^:private
  ->text-baseline
  {:alphabetic 0 
   :ideographic 1})

;; void skia_ParagraphBuilder_addPlaceholder2(ParagraphBuilder *pb, float width, float height, int alignment, int baseline, float offset){
(defn- skia-ParagraphBuilder-addPlaceholder2 [builder placeholder]
  (assert (pointer? builder))
  (let [width (float (:width placeholder))
        height (float (:height placeholder))
        alignment (or (->placeholder-alignment (:alignment placeholder))
                      (int 0))
        _ (assert alignment)
        baseline (or (->text-baseline (:baseline placeholder))
                     0)
        offset (float (or (:offset placeholder)
                          0))]
    (skia_ParagraphBuilder_addPlaceholder2 builder width height alignment baseline offset))
  builder)


(defn- skia-ParagraphBuilder-build [builder]
  (assert (pointer? builder))
  (add-cleaner
   Paragraph
   (skia_ParagraphBuilder_build builder)))

(defn- skia-ParagraphBuilder-reset [builder]
  (assert (pointer? builder))
  (skia_ParagraphBuilder_reset builder)
  builder)


(defn- skia-TextStyle-make []
  (add-cleaner
   TextStyle
   (skia_TextStyle_make)))

(defn- skia-TextStyle-setColor [style [r g b a]]
  (let [color (skia_SkColor4f_make (float r)
                                   (float g)
                                   (float b)
                                   (float
                                    (if a
                                      a
                                      1)))]
    (skia_TextStyle_setColor style color)
    style))

(defn- skia-TextStyle-setForeground [style foreground]
  (assert (pointer? style))
  (assert (pointer? foreground))
  (skia_TextStyle_setForeground style foreground)
  style)


(defn- skia-TextStyle-clearForegroundColor [style]
  (assert (pointer? style))
  (skia_TextStyle_clearForegroundColor style)
  style)

(defn- skia-TextStyle-setBackgroundColor [style background]
  (assert (pointer? style))
  (skia_TextStyle_setBackgroundColor style background)
  style)

(defn- skia-TextStyle-clearBackgroundColor [style]
  (assert (pointer? style))
  (skia_TextStyle_clearBackgroundColor style)
  style)

(defn- skia-TextStyle-setDecoration [style decoration]
  (assert (pointer? style))
  (skia_TextStyle_setDecoration style (text-decoration->int decoration))
  style)

(defn- skia-TextStyle-setDecorationMode [style mode]
  (assert (pointer? style))
  (skia_TextStyle_setDecorationMode
   style
   (case mode
     :text-decoration-mode/gaps 0
     :text-decoration-mode/through 1))
  style)

(defn- skia-TextStyle-setDecorationStyle [style td-style]
  (assert (pointer? style))
  (skia_TextStyle_setDecorationStyle
   style
   (case td-style
     :text-decoration-style/solid 0
     :text-decoration-style/double 1
     :text-decoration-style/dotted 2
     :text-decoration-style/dashed 3
     :text-decoration-style/wavy 4))
  style)

(defn- skia-TextStyle-setDecorationColor [style [r g b a]]
  (assert (pointer? style))
  (let [color (skia_SkColor4f_make (float r)
                                   (float g)
                                   (float b)
                                   (float
                                    (if a
                                      a
                                      1)))]
    (skia_TextStyle_setDecorationColor style color))
  style)

(defn- skia-TextStyle-setDecorationThicknessMultiplier [style n]
  (assert (pointer? style))
  ;; skia can hard crash on values near zero
  (assert (>= n 0.1) (str n))
  (skia_TextStyle_setDecorationThicknessMultiplier style (float n))
  style)

(defn- skia-TextStyle-setFontStyle [style font-style]
  (assert (pointer? style))
  (skia_TextStyle_setFontStyle style
                               (->FontStyle font-style))
  style)

(defn- skia-TextStyle-addShadow [style shadow]
  (assert (pointer? style))
  (assert (pointer? shadow))
  (skia_TextStyle_addShadow style shadow)
  style)

(defn- skia-TextStyle-resetShadows [style]
  (assert (pointer? style))
  (skia_TextStyle_resetShadows style)
  style)

(defn- skia-TextStyle-setFontSize [style font-size]
  (assert (pointer? style))
  (assert (>= font-size 0))
  (skia_TextStyle_setFontSize style (float font-size))
  style)

(defn- skia-TextStyle-setFontFamilies [style families]
  (assert (pointer? style))
  (let [sk-strings (into []
                         (map ->SkString)
                         families)
        sk-families (dtype/make-container
                     :native-heap
                     :int64
                     (into []
                           (map #(dt-ffi/pointer->address %))
                           sk-strings))]
    #_(fill-buf-with-ptrs sk-families sk-strings)
    (skia_TextStyle_setFontFamilies style
                                    sk-families
                                    (count sk-strings))
    ;; don't garbage collect me please
    (identity sk-strings)
    (identity sk-families))
  style)

(defn- skia-TextStyle-setBaselineShift [style shift]
  (assert (pointer? style))
  (assert (>= shift 0))
  (skia_TextStyle_setBaselineShift style (float shift))
  style)

(defn- skia-TextStyle-setHeight [style height]
  (assert (pointer? style))
  (skia_TextStyle_setHeight style (float height))
  style)

(defn- skia-TextStyle-setHeightOverride [style height-override]
  (assert (pointer? style))
  (skia_TextStyle_setHeightOverride style (if height-override
                                            (int 1)
                                            (int 0)))
  style)

(defn- skia-TextStyle-setHalfLeading [style half-leading]
  (assert (pointer? style))
  (skia_TextStyle_setHalfLeading style (int half-leading))
  style)

(defn- skia-TextStyle-setLetterSpacing [style letter-spacing]
  (assert (pointer? style))
  (skia_TextStyle_setLetterSpacing style (float letter-spacing))
  style)

(defn- skia-TextStyle-setWordSpacing [style word-spacing]
  (assert (pointer? style))
  (skia_TextStyle_setWordSpacing style (float word-spacing))
  style)

(defn- skia-TextStyle-setTypeface [style face]
  (assert (pointer? style))
  (assert (pointer? face))
  (skia_TextStyle_setTypeface style face)
  style)

(defn- skia-TextStyle-setLocale [style locale]
  (skia_TextStyle_setLocale style (->SkString locale))
  style)

(defn- skia-TextStyle-setTextBaseline [style baseline]
  (assert (pointer? style))
  (skia_TextStyle_setTextBaseline style (int baseline))
  style)

(defn- skia-TextStyle-setPlaceholder [style]
  (assert (pointer? style))
  (skia_TextStyle_setPlaceholder style)
  style)

(defn- skia-ParagraphStyle-make []
  (add-cleaner
   ParagraphStyle
   (skia_ParagraphStyle_make)))

(defn- skia-ParagraphStyle-turnHintingOff [style]
  (skia_ParagraphStyle_turnHintingOff style)
  style)

(defn- skia-ParagraphStyle-setStrutStyle [style strut-style]
  (assert (pointer? style))
  (assert (pointer? strut-style))
  (skia_ParagraphStyle_setStrutStyle style strut-style)
  style)

(defn- skia-ParagraphStyle-setTextStyle [style text-style]
  (assert (pointer? style))
  (assert (pointer? text-style))
  (skia_ParagraphStyle_setTextStyle style text-style)
  style)

(defn- skia-ParagraphStyle-setTextDirection [style direction]
  (assert (pointer? style))
  (skia_ParagraphStyle_setTextDirection style
                                        (case direction
                                          :text-direction/right-to-left (int 0)
                                          :text-direction/left-to-right (int 1)
                                          ;; else
                                          (throw (ex-info "Invalid text direction"
                                                          {:text-direction direction}))))
  style)

(def ^:private
  text-align-ints
  {:text-align/left (int 0)
   :text-align/right (int 1)
   :text-align/center (int 2)
   :text-align/justify (int 3)
   :text-align/start (int 4)
   :text-align/end (int 5)})
(defn- skia-ParagraphStyle-setTextAlign [style align]
  (assert (pointer? style))
  (let [align-int (get text-align-ints align)]
    (assert align-int)
    (skia_ParagraphStyle_setTextAlign style align-int))
  style)

(defn- skia-ParagraphStyle-setMaxLines [style max-lines]
  (assert (pointer? style))
  (assert #(>= max-lines 0))
  (skia_ParagraphStyle_setMaxLines style (int max-lines))
  style)

(defn- skia-ParagraphStyle-setEllipsis [style ellipsis]
  (assert (pointer? style))
  (skia_ParagraphStyle_setEllipsis style (->SkString ellipsis))
  style)

(defn- skia-ParagraphStyle-setHeight [style height]
  (assert (pointer? style))
  (assert #(>= height 0))
  (skia_ParagraphStyle_setHeight style (float height))
  style)

(def ^:private
  text-height-behavior-ints
  {:text-height-behavior/all (int 0)
   :text-height-behavior/disable-first-ascent (int 1)
   :text-height-behavior/disable-last-ascent (int 2)
   :text-height-behavior/disable-all (int 3)})
(defn- skia-ParagraphStyle-setTextHeightBehavior [style text-height-behavior]
  (assert (pointer? style))
  (let [text-height-behavior-int (get text-height-behavior-ints text-height-behavior)]
    (assert text-height-behavior-int)
    (skia_ParagraphStyle_setTextHeightBehavior style text-height-behavior-int))
  style)

(defn- skia-ParagraphStyle-setReplaceTabCharacters [style value]
  (assert (pointer? style))
  (skia_ParagraphStyle_setReplaceTabCharacters style
                                               (if value
                                                 (int 1)
                                                 (int 0)))
  style)

(defn- skia-Paragraph-getMaxWidth [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getMaxWidth paragraph))

(defn- skia-Paragraph-getHeight [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getHeight paragraph))

(defn- skia-Paragraph-getMinIntrinsicWidth [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getMinIntrinsicWidth paragraph))

(defn- skia-Paragraph-getMaxIntrinsicWidth [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getMaxIntrinsicWidth paragraph))

(defn- skia-Paragraph-getAlphabeticBaseline [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getAlphabeticBaseline paragraph))

(defn- skia-Paragraph-getIdeographicBaseline [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getIdeographicBaseline paragraph))

(defn- skia-Paragraph-getLongestLine [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getLongestLine paragraph))

(defn- skia-Paragraph-didExceedMaxLines [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_didExceedMaxLines paragraph))

(defn- skia-Paragraph-layout [paragraph width]
  (assert (pointer? paragraph))
  (skia_Paragraph_layout paragraph (float width))
  paragraph)

(defn- skia-Paragraph-paint [paragraph resource x y]
  (assert (pointer? paragraph))
  (assert (pointer? resource))
  (skia_Paragraph_paint paragraph resource (float x) (float y))
  paragraph)
;; virtual void paint(ParagraphPainter* painter, SkScalar x, SkScalar y) = 0;

(def rect-height-styles
  [ ;; Provide tight bounding boxes that fit heights per run.
   :tight

   ;; The height of the boxes will be the maximum height of all runs in the
   ;; line. All rects in the same line will be the same height.
   :max

   ;; Extends the top and/or bottom edge of the bounds to fully cover any line
   ;; spacing. The top edge of each line should be the same as the bottom edge
   ;; of the line above. There should be no gaps in vertical coverage given any
   ;; ParagraphStyle line_height.
   ;;
   ;; The top and bottom of each rect will cover half of the
   ;; space above and half of the space below the line.
   :including-line-spacing-middle
   ;; The line spacing will be added to the top of the rect.
   :include-line-spacing-top
   ;; The line spacing will be added to the bottom of the rect.
   :include-line-spacing-bottom
   ;;
   :strut
   ])
(def ^:private
  ->rect-height-style
  (into {}
        (map-indexed (fn [i k]
                       [k (int i)]))
        rect-height-styles))

(def rect-width-styles
  [;; Provide tight bounding boxes that fit widths to the runs of each line
   ;; independently.
   :tight

   ;; Extends the width of the last rect of each line to match the position of
   ;; the widest rect over all the lines.
   :max])

(def ^:private
  ->rect-width-style
  (into {}
        (map-indexed (fn [i k]
                       [k (int i)]))
        rect-width-styles))

;; // Returns a vector of bounding boxes that enclose all text between
;; // start and end glyph indexes, including start and excluding end
;; virtual std::vector<TextBox> getRectsForRange(unsigned start,
;;                                               unsigned end,
;;                                               RectHeightStyle rectHeightStyle,
;;                                               RectWidthStyle rectWidthStyle) = 0;
(defn- skia-Paragraph-getRectsForRange [paragraph start end rect-style-height rect-style-width]
  (assert (pointer? paragraph))
  (let [buf (ffi-buf)
        rect-size (* 4 4)
        max (quot (ffi-buf-size)
                  rect-size)

        n (skia_Paragraph_getRectsForRange paragraph start end
                                           (or (->rect-height-style rect-style-height)
                                               (int 0))
                                           (or (->rect-width-style rect-style-width)
                                               (int 0))
                                           buf max)]
    (into []
          (map (fn [i]
                 {:x       (native-buffer/read-float buf  (+  (*  4  0)  (*  rect-size  i)))
                  :y       (native-buffer/read-float buf  (+  (*  4  1)  (*  rect-size  i)))
                  :width   (native-buffer/read-float buf  (+  (*  4  2)  (*  rect-size  i)))
                  :height  (native-buffer/read-float buf  (+  (*  4  3)  (*  rect-size  i)))}))
          (range n))))
;; virtual std::vector<TextBox> getRectsForPlaceholders() = 0;

(defn- skia-Paragraph-getRectsForPlaceholders [paragraph]
  (assert (pointer? paragraph))
  (let [buf (ffi-buf)
        rect-size (* 4 4)
        max (quot (ffi-buf-size)
                  rect-size)
        n (skia_Paragraph_getRectsForPlaceholders paragraph buf max )]
    (into []
          (map (fn [i]
                 {:x       (native-buffer/read-float buf  (+  (*  4  0)  (*  rect-size  i)))
                  :y       (native-buffer/read-float buf  (+  (*  4  1)  (*  rect-size  i)))
                  :width   (native-buffer/read-float buf  (+  (*  4  2)  (*  rect-size  i)))
                  :height  (native-buffer/read-float buf  (+  (*  4  3)  (*  rect-size  i)))}))
          (range n))))


;; // Returns the index of the glyph that corresponds to the provided coordinate,
;; // with the top left corner as the origin, and +y direction as down
;; virtual PositionWithAffinity getGlyphPositionAtCoordinate(SkScalar dx, SkScalar dy) = 0;
(defn- skia-Paragraph-getGlyphPositionAtCoordinate [paragraph dx dy]
  (assert (pointer? paragraph))
  (let [*pos (-> (native-buffer/malloc 4
                                       {:uninitialized? true
                                        :resource-type :auto})
                 (native-buffer/set-native-datatype :int32))
        *affinity (-> (native-buffer/malloc 4
                                            {:uninitialized? true
                                             :resource-type :auto})
                      (native-buffer/set-native-datatype :int32))]
    (skia_Paragraph_getGlyphPositionAtCoordinate paragraph
                                                 (float dx)
                                                 (float dy)
                                                 *pos
                                                 *affinity)
    [(nth *pos 0)
     (nth *affinity 0)]))
;; // Finds the first and last glyphs that define a word containing
;; // the glyph at index offset
;; virtual SkRange<size_t> getWordBoundary(unsigned offset) = 0;
(defn- skia-Paragraph-getWordBoundary [paragraph offset]
  (assert (pointer? paragraph))
  (skia_Paragraph_getWordBoundary paragraph))
;; virtual void getLineMetrics(std::vector<LineMetrics>&) = 0;

;; (defn- skia-Paragraph-getLineMetrics [paragraph]
;;   (assert (pointer? paragraph))
;;   (skia_Paragraph_getLineMetrics paragraph))


(defn available-font-families []
  (let [num (skia_count_font_families)
        buf (ffi-buf)
        buf-size (ffi-buf-size)]
    (into []
          (map (fn [index]
                 (skia_get_family_name buf buf-size
                                       index)
                 (dt-ffi/c->string buf)))
          (range num))))


(defn- ->TextStyle [s]
  (reduce-kv (fn [style k v]
               (case k
                 :text-style/baseline-shift (skia-TextStyle-setBaselineShift style v)
                 :text-style/color (skia-TextStyle-setColor style v)
                 :text-style/decoration (skia-TextStyle-setDecoration style v)
                 :text-style/decoration-style (skia-TextStyle-setDecorationStyle style v)
                 :text-style/decoration-mode (skia-TextStyle-setDecorationMode style v)
                 :text-style/decoration-color (skia-TextStyle-setDecorationColor style v)
                 :text-style/decoration-thickness-multiplier (skia-TextStyle-setDecorationThicknessMultiplier style v)
                 :text-style/font-families (skia-TextStyle-setFontFamilies style v)
                 :text-style/font-size (skia-TextStyle-setFontSize style v)
                 :text-style/font-style (skia-TextStyle-setFontStyle style v)

                 :text-style/half-leading (skia-TextStyle-setHalfLeading style v)
                 :text-style/height (skia-TextStyle-setHeight style v)
                 :text-style/height-override (skia-TextStyle-setHeightOverride style v)
                 :text-style/letter-spacing (skia-TextStyle-setLetterSpacing style v)
                 :text-style/locale (skia-TextStyle-setLocale style v)
                 :text-style/placeholder? (if v
                                            (skia-TextStyle-setPlaceholder style)
                                            style)
                 :text-style/text-baseline (skia-TextStyle-setTextBaseline style v)
                 :text-style/word-spacing (skia-TextStyle-setWordSpacing style v)

                 :text-style/typeface style
                 :text-style/foreground style
                 :text-style/shadows style
                 :text-style/background-color (skia-TextStyle-setBackgroundColor style (paint/->SkPaint v))

                 ;; else
                 style))
             (skia-TextStyle-make)
             (if (or (:text-style/foreground s)
                     (:text-style/color s))
               s
               ;; default color is black
               (assoc s :text-style/color [0 0 0]))))

(defn ->ParagraphStyle [ps]
  (reduce-kv (fn [ps k v]
               (case k
                 :paragraph-style/hinting? (if v
                                             ps
                                             (skia-ParagraphStyle-turnHintingOff ps))
                 :paragraph-style/text-style (skia-ParagraphStyle-setTextStyle ps (->TextStyle v))
                 :paragraph-style/text-direction (skia-ParagraphStyle-setTextDirection ps v)
                 :paragraph-style/text-align (skia-ParagraphStyle-setTextAlign ps v)
                 :paragraph-style/max-lines (skia-ParagraphStyle-setMaxLines ps v)
                 :paragraph-style/ellipsis (skia-ParagraphStyle-setEllipsis ps v)
                 :paragraph-style/height (skia-ParagraphStyle-setHeight ps v)
                 :paragraph-style/text-behavior (skia-ParagraphStyle-setTextHeightBehavior ps v)
                 :paragraph-style/replace-tab-characters? (skia-ParagraphStyle-setReplaceTabCharacters ps v)

                 ;; else
                 ps))
             (skia-ParagraphStyle-make)
             ps))

(defmulti add-text (fn [builder text]
                     (class text)))

(defmethod add-text String [builder s]
  (skia-ParagraphBuilder-addText builder s))

(defmethod add-text :default [builder xs]
  (reduce add-text builder xs))

(defmethod add-text clojure.lang.IPersistentMap [pb chunk]
  (if-let [text (:text chunk)]
    (if-let [style (:style chunk)]
      (doto pb
        (skia-ParagraphBuilder-pushStyle (->TextStyle style))
        (skia-ParagraphBuilder-addText text)
        (skia-ParagraphBuilder-pop))
      (doto pb
        (skia-ParagraphBuilder-addText text)))
    (when-let [placeholder (:placeholder chunk)]
      (skia-ParagraphBuilder-addPlaceholder2 pb placeholder))))

(defn- default-paragraph-style []
  (let [text-style (doto (skia-TextStyle-make)
                     (skia-TextStyle-setColor [0 0 0]))]
    (doto (skia-ParagraphStyle-make)
      (skia-ParagraphStyle-setTextStyle text-style))))

(defn- make-paragraph*
  ([text]
   (make-paragraph* text Float/POSITIVE_INFINITY))
  ([text width]
   (make-paragraph* text width nil))
  ([text width paragraph-style]
   (assert (or (nil? width)
               (>= width 0)))
   (let [width (or width Float/POSITIVE_INFINITY)
         paragraph-style (if paragraph-style
                           (->ParagraphStyle paragraph-style)
                           (default-paragraph-style))
         pb (skia-ParagraphBuilder-make paragraph-style)
         pb (add-text pb text)
         paragraph (doto (skia-ParagraphBuilder-build pb)
                     (skia-Paragraph-layout width))]
     paragraph)))

(def ^:private make-paragraph (memoize make-paragraph*))

(defprotocol IParagraph
  (get-rects-for-placeholders [para])
  (get-rects-for-range [para start end height-style width-style])
  (glyph-position-at-coordinate [para x y]))

(defrecord Paragraph [paragraph width paragraph-style]
  IParagraph
  (get-rects-for-range [_ start end height-style width-style]
    (skia-Paragraph-getRectsForRange
     (make-paragraph paragraph width paragraph-style)
     start end height-style width-style))
  (get-rects-for-placeholders [_]
    (skia-Paragraph-getRectsForPlaceholders (make-paragraph paragraph width paragraph-style)))
  (glyph-position-at-coordinate
    [_ x y]
    (skia-Paragraph-getGlyphPositionAtCoordinate
     (make-paragraph paragraph width paragraph-style)
     x y))

  ui/IOrigin
  (-origin [this]
    [0 0])

  ui/IBounds
  (-bounds [this]
    (let [para (make-paragraph paragraph width paragraph-style)
          width (if (or (nil? width)
                        (= ##Inf width))
                  (skia-Paragraph-getMaxIntrinsicWidth para)
                  width)
          height (skia-Paragraph-getHeight para)]
      [width height]))

  backend/IDraw
  (draw [this]
    (let [paragraph (make-paragraph paragraph width paragraph-style)]
      (skia-Paragraph-paint paragraph backend/*skia-resource* 0 0))))

(defn intrinsic-width [para]
  (let [{:keys [paragraph width paragraph-style]} para
        para (make-paragraph paragraph width paragraph-style)]
    (skia-Paragraph-getMaxIntrinsicWidth para)))

(defn paragraph
  "Returns a view that represents a paragraph of text.

  `text` can be:
  - a string which is treated as unstyled text.
  - a map with a required `:text` key and optional `:style` key.
    `:text` must be a string.
    `:style` is a map. See the `:styled-text/style` spec in `membrane.skia.paragraph.spec`.
  - a sequence of strings or maps.

  An optional `width` can be provided as a second argument. If `width` is provided, then text
  will be wrapped using the provided width. `width` can also be `nil` to get the same
  behavior as the 1-arity implementation of `paragraph`."
  ([text]
   (->Paragraph text nil nil))
  ([text width]
   (->Paragraph text width nil))
  ([text width paragraph-style]
   (->Paragraph text width paragraph-style)))

