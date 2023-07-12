(ns membrane.skia.paragraph
  (:require [membrane.skia :as skia
             :refer [defc
                     ffi-buf]]
            [membrane.ui :as ui])
  (:import com.sun.jna.Pointer
           com.sun.jna.Memory
           com.sun.jna.Native
           com.sun.jna.ptr.IntByReference
           com.phronemophobic.membrane.Skia
           java.lang.ref.Cleaner))

(def ^:private void Void/TYPE)
(def cleaner (delay (Cleaner/create)))

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

(def membraneskialib @#'skia/membraneskialib)

(defn- pointer? [p]
  (instance? Pointer p))

(defc skia_SkRefCntBase_ref membraneskialib void [o])
;; `ref` is already taken
(defn- inc-ref [o]
  (skia_SkRefCntBase_ref o))
(defc skia_SkRefCntBase_unref membraneskialib void [o])
(defn- unref [o]
  (skia_SkRefCntBase_unref o))

(defmacro add-cleaner [type p]
  (if false ;; ('#{SkString} type)
    p
    (let [delete-sym (symbol (str "skia_" type "_delete"))]
      `(let [p# ~p
             ptr# (Pointer/nativeValue p#)]
         (.register ^Cleaner @cleaner p#
                    (fn []
                      (~delete-sym (Pointer. ptr#))))
         p#))))

(defn- ref-count [p name]
  (let [ptr (Pointer/nativeValue p)]
    (.register ^Cleaner @cleaner p
               (fn []
                 (skia_SkRefCntBase_unref (Pointer. ptr))))
    p))

(defc skia_SkString_delete membraneskialib void [sk-string])
;; SkString* skia_make_skstring_utf8(char *s, int len)
(defc skia_SkString_make_utf8 membraneskialib Pointer [buf len])
(defn- ->SkString [^String s]
  (let [buf (.getBytes s "utf8")
        len (alength buf)]
    (add-cleaner
     SkString
     (skia_SkString_make_utf8 buf len))))

;; SkColor skia_SkColor4f_make(float red, float green, float blue, float alpha)
(defc skia_SkColor4f_make membraneskialib Pointer [red green blue alpha])
(defn- skia-SkColor4f-make [r g b a]
  (skia_SkColor4f_make
   (float r)
   (float g)
   (float b)
   (float a)))

(defc skia_FontStyle_delete membraneskialib void [style])
(defc skia_FontStyle_make membraneskialib Pointer [make width slant])
(defn- skia-FontStyle-make [weight width slant]
  (let [weight (get skia/font-weights weight
                    (or weight -1))
        width (get skia/font-widths width
                   (or width -1))
        slant (get skia/font-slants slant
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

(defn- fill-buf-with-ptrs [buf ptrs]
  (loop [offset 0
         ptrs (seq ptrs)]
    (if ptrs
      (let [ptr (first ptrs)]
        (.setPointer ^Memory buf offset ptr)
        (recur (+ offset Native/POINTER_SIZE)
               (next ptrs)))))
  nil)



(defc skia_ParagraphBuilder_delete membraneskialib void [pb])
(defc skia_ParagraphBuilder_make membraneskialib Pointer [paragraph-style])
(defn- skia-ParagraphBuilder-make [paragraph-style]
  (assert (pointer? paragraph-style))
  
  (add-cleaner
   ParagraphBuilder
   (skia_ParagraphBuilder_make paragraph-style)))

(defc skia_ParagraphBuilder_pushStyle membraneskialib void [builder style])
(defn- skia-ParagraphBuilder-pushStyle [builder style]
  (assert (pointer? builder))
  (assert (pointer? style))
  (skia_ParagraphBuilder_pushStyle builder style)
  builder)

(defc skia_ParagraphBuilder_pop membraneskialib void [builder])
(defn- skia-ParagraphBuilder-pop [builder]
  (assert (pointer? builder))
  (skia_ParagraphBuilder_pop builder)
  builder)

(defc skia_ParagraphBuilder_addText membraneskialib void [builder text len])
(defn- skia-ParagraphBuilder-addText [builder ^String s]
  (assert (pointer? builder))
  (let [buf (.getBytes s "utf8")
        len (alength buf)]
    (skia_ParagraphBuilder_addText builder buf len)
    builder))

(defc skia_ParagraphBuilder_addPlaceholder membraneskialib void [builder style])
(defn- skia-ParagraphBuilder-addPlaceholder [builder style]
  (assert (pointer? builder))
  (assert (pointer? style))
  (skia_ParagraphBuilder_addPlaceholder builder style)
  builder)


(defc skia_Paragraph_delete membraneskialib void [p])
(defc skia_ParagraphBuilder_build membraneskialib Pointer [builder])
(defn- skia-ParagraphBuilder-build [builder]
  (assert (pointer? builder))
  (add-cleaner
   Paragraph
   (skia_ParagraphBuilder_build builder)))

(defc skia_ParagraphBuilder_reset membraneskialib void [builder])
(defn- skia-ParagraphBuilder-reset [builder]
  (assert (pointer? builder))
  (skia_ParagraphBuilder_reset builder)
  builder)


(defc skia_TextStyle_delete membraneskialib void [style])
(defc skia_TextStyle_make membraneskialib Pointer)
(defn- skia-TextStyle-make []
  (add-cleaner
   TextStyle
   (skia_TextStyle_make)))

(defc skia_TextStyle_setColor membraneskialib void [style color])
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

(defc skia_TextStyle_setForeground membraneskialib void [style foreground])
(defn- skia-TextStyle-setForeground [style foreground]
  (assert (pointer? style))
  (assert (pointer? foreground))
  (skia_TextStyle_setForeground style foreground)
  style)


(defc skia_TextStyle_clearForegroundColor membraneskialib void [style])
(defn- skia-TextStyle-clearForegroundColor [style]
  (assert (pointer? style))
  (skia_TextStyle_clearForegroundColor style)
  style)

(defc skia_TextStyle_setBackgroundColor membraneskialib void [style background])
(defn- skia-TextStyle-setBackgroundColor [style background]
  (assert (pointer? style))
  (skia_TextStyle_setBackgroundColor style background)
  style)

(defc skia_TextStyle_clearBackgroundColor membraneskialib void [style])
(defn- skia-TextStyle-clearBackgroundColor [style]
  (assert (pointer? style))
  (skia_TextStyle_clearBackgroundColor style)
  style)

(defc skia_TextStyle_setDecoration membraneskialib void [style decoration])
(defn- skia-TextStyle-setDecoration [style decoration]
  (assert (pointer? style))
  (skia_TextStyle_setDecoration style (text-decoration->int decoration))
  style)

(defc skia_TextStyle_setDecorationMode membraneskialib void [style mode])
(defn- skia-TextStyle-setDecorationMode [style mode]
  (assert (pointer? style))
  (skia_TextStyle_setDecorationMode
   style
   (case mode
     :text-decoration-mode/gaps 0
     :text-decoration-mode/through 1))
  style)

(defc skia_TextStyle_setDecorationStyle membraneskialib void [style td-style])
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

(defc skia_TextStyle_setDecorationColor membraneskialib void [style color])
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

(defc skia_TextStyle_setDecorationThicknessMultiplier membraneskialib void [style n])
(defn- skia-TextStyle-setDecorationThicknessMultiplier [style n]
  (assert (pointer? style))
  ;; skia can hard crash on values near zero
  (assert (>= n 0.1) (str n))
  (skia_TextStyle_setDecorationThicknessMultiplier style (float n))
  style)

(defc skia_TextStyle_setFontStyle membraneskialib void [style font-style])
(defn- skia-TextStyle-setFontStyle [style font-style]
  (assert (pointer? style))
  (skia_TextStyle_setFontStyle style
                               (->FontStyle font-style))
  style)

(defc skia_TextStyle_addShadow membraneskialib void [style shadow])
(defn- skia-TextStyle-addShadow [style shadow]
  (assert (pointer? style))
  (assert (pointer? shadow))
  (skia_TextStyle_addShadow style shadow)
  style)

(defc skia_TextStyle_resetShadows membraneskialib void [style])
(defn- skia-TextStyle-resetShadows [style]
  (assert (pointer? style))
  (skia_TextStyle_resetShadows style)
  style)

(defc skia_TextStyle_setFontSize membraneskialib void [style font-size])
(defn- skia-TextStyle-setFontSize [style font-size]
  (assert (pointer? style))
  (assert (>= font-size 0))
  (skia_TextStyle_setFontSize style (float font-size))
  style)

(defc skia_TextStyle_setFontFamilies membraneskialib void [style families num-families])
(defn- skia-TextStyle-setFontFamilies [style families]
  (assert (pointer? style))
  (let [sk-families (ffi-buf)
        sk-strings (into []
                         (map ->SkString)
                         families)]
    (fill-buf-with-ptrs sk-families sk-strings)
    (skia_TextStyle_setFontFamilies style
                                    sk-families
                                    (count sk-strings))
    ;; don't garbage collect me please
    (identity sk-strings))
  style)

(defc skia_TextStyle_setBaselineShift membraneskialib void [style shift])
(defn- skia-TextStyle-setBaselineShift [style shift]
  (assert (pointer? style))
  (assert (>= shift 0))
  (skia_TextStyle_setBaselineShift style (float shift))
  style)

(defc skia_TextStyle_setHeight membraneskialib void [style height])
(defn- skia-TextStyle-setHeight [style height]
  (assert (pointer? style))
  (skia_TextStyle_setHeight style (float height))
  style)

(defc skia_TextStyle_setHeightOverride membraneskialib void [style height-override])
(defn- skia-TextStyle-setHeightOverride [style height-override]
  (assert (pointer? style))
  (skia_TextStyle_setHeightOverride style (if height-override
                                            (int 1)
                                            (int 0)))
  style)

(defc skia_TextStyle_setHalfLeading membraneskialib void [style half-leading])
(defn- skia-TextStyle-setHalfLeading [style half-leading]
  (assert (pointer? style))
  (skia_TextStyle_setHalfLeading style (int half-leading))
  style)

(defc skia_TextStyle_setLetterSpacing membraneskialib void [style letter-spacing])
(defn- skia-TextStyle-setLetterSpacing [style letter-spacing]
  (assert (pointer? style))
  (skia_TextStyle_setLetterSpacing style (float letter-spacing))
  style)

(defc skia_TextStyle_setWordSpacing membraneskialib void [style word-spacing])
(defn- skia-TextStyle-setWordSpacing [style word-spacing]
  (assert (pointer? style))
  (skia_TextStyle_setWordSpacing style (float word-spacing))
  style)

(defc skia_TextStyle_setTypeface membraneskialib void [style face])
(defn- skia-TextStyle-setTypeface [style face]
  (assert (pointer? style))
  (assert (pointer? face))
  (skia_TextStyle_setTypeface style face)
  style)

(defc skia_TextStyle_setLocale membraneskialib void [style locale])
(defn- skia-TextStyle-setLocale [style locale]
  (skia_TextStyle_setLocale style (->SkString locale))
  style)

(defc skia_TextStyle_setTextBaseline membraneskialib void [style baseline])
(defn- skia-TextStyle-setTextBaseline [style baseline]
  (assert (pointer? style))
  (skia_TextStyle_setTextBaseline style (int baseline))
  style)

(defc skia_TextStyle_setPlaceholder membraneskialib void [style])
(defn- skia-TextStyle-setPlaceholder [style]
  (assert (pointer? style))
  (skia_TextStyle_setPlaceholder style)
  style)

(defc skia_ParagraphStyle_delete membraneskialib void [ps])
(defc skia_ParagraphStyle_make membraneskialib Pointer)
(defn- skia-ParagraphStyle-make []
  (add-cleaner
   ParagraphStyle
   (skia_ParagraphStyle_make)))

(defc skia_ParagraphStyle_turnHintingOff membraneskialib void [style])
(defn- skia-ParagraphStyle-turnHintingOff [style]
  (skia_ParagraphStyle_turnHintingOff style)
  style)

(defc skia_ParagraphStyle_setStrutStyle membraneskialib void [style strut-style])
(defn- skia-ParagraphStyle-setStrutStyle [style strut-style]
  (assert (pointer? style))
  (assert (pointer? strut-style))
  (skia_ParagraphStyle_setStrutStyle style strut-style)
  style)

(defc skia_ParagraphStyle_setTextStyle membraneskialib void [style text-style])
(defn- skia-ParagraphStyle-setTextStyle [style text-style]
  (assert (pointer? style))
  (assert (pointer? text-style))
  (skia_ParagraphStyle_setTextStyle style text-style)
  style)

(defc skia_ParagraphStyle_setTextDirection membraneskialib void [style direction])
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
(defc skia_ParagraphStyle_setTextAlign membraneskialib void [style align])
(defn- skia-ParagraphStyle-setTextAlign [style align]
  (assert (pointer? style))
  (let [align-int (get text-align-ints align)]
    (assert align-int)
    (skia_ParagraphStyle_setTextAlign style align-int))
  style)

(defc skia_ParagraphStyle_setMaxLines membraneskialib void [style max-lines])
(defn- skia-ParagraphStyle-setMaxLines [style max-lines]
  (assert (pointer? style))
  (assert #(>= max-lines 0))
  (skia_ParagraphStyle_setMaxLines style (int max-lines))
  style)

(defc skia_ParagraphStyle_setEllipsis membraneskialib void [style ellipsis])
(defn- skia-ParagraphStyle-setEllipsis [style ellipsis]
  (assert (pointer? style))
  (skia_ParagraphStyle_setEllipsis style (->SkString ellipsis))
  style)

(defc skia_ParagraphStyle_setHeight membraneskialib void [style height])
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
(defc skia_ParagraphStyle_setTextHeightBehavior membraneskialib void [style text-height-behavior])
(defn- skia-ParagraphStyle-setTextHeightBehavior [style text-height-behavior]
  (assert (pointer? style))
  (let [text-height-behavior-int (get text-height-behavior-ints text-height-behavior)]
    (assert text-height-behavior-int)
    (skia_ParagraphStyle_setTextHeightBehavior style text-height-behavior-int))
  style)

(defc skia_ParagraphStyle_setReplaceTabCharacters membraneskialib void [style value])
(defn- skia-ParagraphStyle-setReplaceTabCharacters [style value]
  (assert (pointer? style))
  (skia_ParagraphStyle_setReplaceTabCharacters style
                                               (if value
                                                 (int 1)
                                                 (int 0)))
  style)

(defc skia_Paragraph_getMaxWidth membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getMaxWidth [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getMaxWidth paragraph))

(defc skia_Paragraph_getHeight membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getHeight [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getHeight paragraph))

(defc skia_Paragraph_getMinIntrinsicWidth membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getMinIntrinsicWidth [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getMinIntrinsicWidth paragraph))

(defc skia_Paragraph_getMaxIntrinsicWidth membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getMaxIntrinsicWidth [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getMaxIntrinsicWidth paragraph))

(defc skia_Paragraph_getAlphabeticBaseline membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getAlphabeticBaseline [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getAlphabeticBaseline paragraph))

(defc skia_Paragraph_getIdeographicBaseline membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getIdeographicBaseline [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getIdeographicBaseline paragraph))

(defc skia_Paragraph_getLongestLine membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getLongestLine [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getLongestLine paragraph))

(defc skia_Paragraph_didExceedMaxLines membraneskialib Integer/TYPE [paragraph])
(defn- skia-Paragraph-didExceedMaxLines [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_didExceedMaxLines paragraph))

(defc skia_Paragraph_layout membraneskialib void [paragraph width])
(defn- skia-Paragraph-layout [paragraph width]
  (assert (pointer? paragraph))
  (skia_Paragraph_layout paragraph (float width))
  paragraph)

(defc skia_Paragraph_paint membraneskialib void [paragraph resource x y])
(defn- skia-Paragraph-paint [paragraph resource x y]
  (assert (pointer? paragraph))
  (assert (pointer? resource))
  (skia_Paragraph_paint paragraph resource (float x) (float y))
  paragraph)
;; virtual void paint(ParagraphPainter* painter, SkScalar x, SkScalar y) = 0;

;; // Returns a vector of bounding boxes that enclose all text between
;; // start and end glyph indexes, including start and excluding end
;; virtual std::vector<TextBox> getRectsForRange(unsigned start,
;;                                               unsigned end,
;;                                               RectHeightStyle rectHeightStyle,
;;                                               RectWidthStyle rectWidthStyle) = 0;
#_#_(defc skia_Paragraph_getRectsForRange membraneskialib Pointer [paragraph start end rect-style-height rect-style-width])
(defn- skia-Paragraph-getRectsForRange [paragraph start end rect-style-height rect-style-width]
  (assert (pointer? paragraph))
  (skia_Paragraph_getRectsForRange paragraph))
;; virtual std::vector<TextBox> getRectsForPlaceholders() = 0;
#_#_(defc skia_Paragraph_getRectsForPlaceHolders membraneskialib Pointer [paragraph])
(defn- skia-Paragraph-getRectsForPlaceHolders [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getRectsForPlaceHolders paragraph))
;; // Returns the index of the glyph that corresponds to the provided coordinate,
;; // with the top left corner as the origin, and +y direction as down
;; virtual PositionWithAffinity getGlyphPositionAtCoordinate(SkScalar dx, SkScalar dy) = 0;
(defc skia_Paragraph_getGlyphPositionAtCoordinate membraneskialib void [paragraph dx dy *pos *affinity])
(defn- skia-Paragraph-getGlyphPositionAtCoordinate [paragraph dx dy]
  (assert (pointer? paragraph))
  (let [*pos (IntByReference.)
        *affinity (IntByReference.)]
   (skia_Paragraph_getGlyphPositionAtCoordinate paragraph
                                                (float dx)
                                                (float dy)
                                                *pos
                                                *affinity)
   [(.getValue *pos)
    (.getValue *affinity)]))
;; // Finds the first and last glyphs that define a word containing
;; // the glyph at index offset
;; virtual SkRange<size_t> getWordBoundary(unsigned offset) = 0;
#_#_(defc skia_Paragraph_getWordBoundary membraneskialib Pointer [paragraph offset])
(defn- skia-Paragraph-getWordBoundary [paragraph offset]
  (assert (pointer? paragraph))
  (skia_Paragraph_getWordBoundary paragraph))
;; virtual void getLineMetrics(std::vector<LineMetrics>&) = 0;
#_#_(defc skia_Paragraph_getLineMetrics membraneskialib void [paragraph metrics])
(defn- skia-Paragraph-getLineMetrics [paragraph metrics]
  (assert (pointer? paragraph))
  (skia_Paragraph_getLineMetrics paragraph))


(defc skia_count_font_families membraneskialib Integer/TYPE [])
(defc skia_get_family_name membraneskialib void [family-name len index])
(defn available-font-families []
  (let [num (skia_count_font_families)
        buf (ffi-buf)
        buf-size (.size buf)]
    (into []
          (map (fn [index]
                 (skia_get_family_name buf buf-size
                                       index)
                 (.getString ^Memory buf 0 "utf-8")))
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
                 :text-style/background-color style

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
  (if-let [style (:style chunk)]
      (doto pb
        (skia-ParagraphBuilder-pushStyle (->TextStyle style))
        (skia-ParagraphBuilder-addText (:text chunk))
        (skia-ParagraphBuilder-pop))
      (doto pb
        (skia-ParagraphBuilder-addText (:text chunk)))))

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

(defrecord Paragraph [paragraph width paragraph-style]
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

  skia/IDraw
  (draw [this]
    (let [paragraph (make-paragraph paragraph width paragraph-style)]
      (skia-Paragraph-paint paragraph skia/*skia-resource* 0 0))))

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

