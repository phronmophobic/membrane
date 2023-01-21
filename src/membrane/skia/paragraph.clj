(ns membrane.skia.paragraph
  (:require [membrane.skia :as skia
             :refer [defc
                     ffi-buf
                     membraneskialib]]
            [membrane.ui :as ui])
  (:import com.sun.jna.Pointer
           com.sun.jna.Memory
           com.sun.jna.Native
           com.phronemophobic.membrane.Skia))

(def ^:private void Void/TYPE)
;; reuse skia buffer

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
  (instance? Pointer p))

;; SkString* skia_make_skstring_utf8(char *s, int len)
(defc skia_skstring_make_utf8 membraneskialib Pointer [buf len])
(defn- ->SkString [^String s]
  (let [buf (.getBytes s "utf8")
        len (alength buf)]
   (skia_skstring_make_utf8 buf len)))

;; SkColor skia_SkColor4f_make(float red, float green, float blue, float alpha)
(defc skia_SkColor4f_make membraneskialib Pointer [red green blue alpha])
(defn- skia-SkColor4f-make [r g b a]
  (skia_SkColor4f_make
   (float r)
   (float g)
   (float b)
   (float a)))

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
    (skia_FontStyle_make (int weight)
                         (int width)
                         (int slant))))
(defn- ->FontStyle [{:font-style/keys [weight width slant]}]
  (skia-FontStyle-make weight width slant))

(defn- ->FontFamilies [buf font-families]
  (loop [offset 0
         n 0
         font-families (seq font-families)]
    (if font-families
      (let [family (first font-families)]
        (.setPointer ^Memory buf offset (->SkString family))
        (recur (+ offset Native/POINTER_SIZE)
               (inc n)
               (next font-families)))
      ;; else
      n)))



;; ParagraphBuilder* skia_ParagraphBuilder_make(ParagraphStyle* paragraphStyle)
(defc skia_ParagraphBuilder_make membraneskialib Pointer [paragraph-style])
(defn- skia-ParagraphBuilder-make [paragraph-style]
  (assert (pointer? paragraph-style))
  (skia_ParagraphBuilder_make paragraph-style))

;; ParagraphBuilder* skia_ParagraphBuilder_pushStyle(ParagraphBuilder *pb, TextStyle* style)
(defc skia_ParagraphBuilder_pushStyle membraneskialib Pointer [builder style])
(defn- skia-ParagraphBuilder-pushStyle [builder style]
  (assert (pointer? builder))
  (assert (pointer? style))
  (skia_ParagraphBuilder_pushStyle builder style))

;; ParagraphBuilder* skia_ParagraphBuilder_pop(ParagraphBuilder *pb)
(defc skia_ParagraphBuilder_pop membraneskialib Pointer [builder])
(defn- skia-ParagraphBuilder-pop [builder]
  (assert (pointer? builder))
  (skia_ParagraphBuilder_pop builder))

;; ParagraphBuilder* skia_ParagraphBuilder_addText(ParagraphBuilder *pb, char* text, int len)
(defc skia_ParagraphBuilder_addText membraneskialib Pointer [builder text len])
(defn- skia-ParagraphBuilder-addText [builder ^String s]
  (assert (pointer? builder))
  (let [buf (.getBytes s "utf8")
        len (alength buf)]
    (skia_ParagraphBuilder_addText builder buf len)))

;; ParagraphBuilder* skia_ParagraphBuilder_addPlaceholder(ParagraphBuilder *pb, PlaceholderStyle* placeholderStyle)
(defc skia_ParagraphBuilder_addPlaceholder membraneskialib Pointer [builder style])
(defn- skia-ParagraphBuilder-addPlaceholder [builder style]
  (assert (pointer? builder))
  (assert (pointer? style))
  (skia_ParagraphBuilder_addPlaceholder builder style))

;; Paragraph* skia_ParagraphBuilder_build(ParagraphBuilder *pb)
(defc skia_ParagraphBuilder_build membraneskialib Pointer [builder])
(defn- skia-ParagraphBuilder-build [builder]
  (assert (pointer? builder))
  (skia_ParagraphBuilder_build builder))

;; ParagraphBuilder* skia_ParagraphBuilder_reset(ParagraphBuilder *pb)
(defc skia_ParagraphBuilder_reset membraneskialib Pointer [builder])
(defn- skia-ParagraphBuilder-reset [builder]
  (assert (pointer? builder))
  (skia_ParagraphBuilder_reset builder))

;; TextStyle* skia_TextStyle_make()
(defc skia_TextStyle_make membraneskialib Pointer)
(defn- skia-TextStyle-make []
  (skia_TextStyle_make))

;; TextStyle* skia_TextStyle_setColor(TextStyle* style, uint32_t color )
(defc skia_TextStyle_setColor membraneskialib Pointer [style color])
(defn- skia-TextStyle-setColor [style [r g b a]]
  (let [color (skia_SkColor4f_make (float r)
                                   (float g)
                                   (float b)
                                   (float
                                    (if a
                                      a
                                      1)))]
    (skia_TextStyle_setColor style color)))

;; TextStyle* skia_TextStyle_setForeground(TextStyle* style, SkPaint* foregroundColor)
(defc skia_TextStyle_setForeground membraneskialib Pointer [style foreground])
(defn- skia-TextStyle-setForeground [style foreground]
  (assert (pointer? style))
  (assert (pointer? foreground))
  (skia_TextStyle_setForeground style foreground))

;; TextStyle* skia_TextStyle_clearForegroundColor(TextStyle* style)
(defc skia_TextStyle_clearForegroundColor membraneskialib Pointer [style])
(defn- skia-TextStyle-clearForegroundColor [style]
  (assert (pointer? style))
  (skia_TextStyle_clearForegroundColor style))

;; TextStyle* skia_TextStyle_setBackgroundColor(TextStyle* style, SkPaint* backgroundColor)
(defc skia_TextStyle_setBackgroundColor membraneskialib Pointer [style background])
(defn- skia-TextStyle-setBackgroundColor [style background]
  (assert (pointer? style))
  (skia_TextStyle_setBackgroundColor style background))

;; TextStyle* skia_TextStyle_clearBackgroundColor(TextStyle* style)
(defc skia_TextStyle_clearBackgroundColor membraneskialib Pointer [style])
(defn- skia-TextStyle-clearBackgroundColor [style]
  (assert (pointer? style))
  (skia_TextStyle_clearBackgroundColor style))

;; TextStyle* skia_TextStyle_setDecoration(TextStyle* style, int decoration)
(defc skia_TextStyle_setDecoration membraneskialib Pointer [style decoration])
(defn- skia-TextStyle-setDecoration [style decoration]
  (assert (pointer? style))
  (skia_TextStyle_setDecoration style (text-decoration->int decoration)))

;; TextStyle* skia_TextStyle_setDecorationMode(TextStyle* style, int mode)
(defc skia_TextStyle_setDecorationMode membraneskialib Pointer [style mode])
(defn- skia-TextStyle-setDecorationMode [style mode]
  (assert (pointer? style))
  (skia_TextStyle_setDecorationMode
   style
   (case mode
     :text-decoration-mode/gaps 0
     :text-decoration-mode/through 1)))

;; TextStyle* skia_TextStyle_setDecorationStyle(TextStyle* style, int tdStyle)
(defc skia_TextStyle_setDecorationStyle membraneskialib Pointer [style td-style])
(defn- skia-TextStyle-setDecorationStyle [style td-style]
  (assert (pointer? style))
  (skia_TextStyle_setDecorationStyle
   style
   (case td-style
     :text-decoration-style/solid 0
     :text-decoration-style/double 1
     :text-decoration-style/dotted 2
     :text-decoration-style/dashed 3
     :text-decoration-style/wavy 4)))

;; TextStyle* skia_TextStyle_setDecorationColor(TextStyle* style, uint32_t color)
(defc skia_TextStyle_setDecorationColor membraneskialib Pointer [style color])
(defn- skia-TextStyle-setDecorationColor [style [r g b a]]
  (assert (pointer? style))
  (let [color (skia_SkColor4f_make (float r)
                                   (float g)
                                   (float b)
                                   (float
                                    (if a
                                      a
                                      1)))]
   (skia_TextStyle_setDecorationColor style color)))

;; TextStyle* skia_TextStyle_setDecorationThicknessMultiplier(TextStyle* style, float m)
(defc skia_TextStyle_setDecorationThicknessMultiplier membraneskialib Pointer [style n])
(defn- skia-TextStyle-setDecorationThicknessMultiplier [style n]
  (assert (pointer? style))
  ;; skia can hard crash on values near zero
  (assert (>= n 0.05) (str n))
  (skia_TextStyle_setDecorationThicknessMultiplier style (float n)))

;; TextStyle* skia_TextStyle_setFontStyle(TextStyle* style, SkFontStyle* fontStyle)
(defc skia_TextStyle_setFontStyle membraneskialib Pointer [style font-style])
(defn- skia-TextStyle-setFontStyle [style font-style]
  (assert (pointer? style))
  (skia_TextStyle_setFontStyle style
                               (->FontStyle font-style)))

;; TextStyle* skia_TextStyle_addShadow(TextStyle* style, TextShadow* shadow)
(defc skia_TextStyle_addShadow membraneskialib Pointer [style shadow])
(defn- skia-TextStyle-addShadow [style shadow]
  (assert (pointer? style))
  (assert (pointer? shadow))
  (skia_TextStyle_addShadow style shadow))

;; TextStyle* skia_TextStyle_resetShadows(TextStyle* style)
(defc skia_TextStyle_resetShadows membraneskialib Pointer [style])
(defn- skia-TextStyle-resetShadows [style]
  (assert (pointer? style))
  (skia_TextStyle_resetShadows style))

;; TextStyle* skia_TextStyle_setFontSize(TextStyle* style, float fontSize)
(defc skia_TextStyle_setFontSize membraneskialib Pointer [style font-size])
(defn- skia-TextStyle-setFontSize [style font-size]
  (assert (pointer? style))
  (assert (>= font-size 0))
  (skia_TextStyle_setFontSize style (float font-size)))

;; TextStyle* skia_TextStyle_setFontFamilies(TextStyle* style, std::vector<SkString> families)
(defc skia_TextStyle_setFontFamilies membraneskialib Pointer [style families num-families])
(defn- skia-TextStyle-setFontFamilies [style families]
  (assert (pointer? style))
  (let [sk-families (ffi-buf)
        num-families (->FontFamilies sk-families families) ]
    (skia_TextStyle_setFontFamilies style
                                    sk-families
                                    num-families)))

;; TextStyle* skia_TextStyle_setBaselineShift(TextStyle* style, float shift)
(defc skia_TextStyle_setBaselineShift membraneskialib Pointer [style shift])
(defn- skia-TextStyle-setBaselineShift [style shift]
  (assert (pointer? style))
  (assert (>= shift 0))
  (skia_TextStyle_setBaselineShift style (float shift)))

;; TextStyle* skia_TextStyle_setHeight(TextStyle* style, float height)
(defc skia_TextStyle_setHeight membraneskialib Pointer [style height])
(defn- skia-TextStyle-setHeight [style height]
  (assert (pointer? style))
  (skia_TextStyle_setHeight style (float height)))

;; TextStyle* skia_TextStyle_setHeightOverride(TextStyle* style, int heightOverride)
(defc skia_TextStyle_setHeightOverride membraneskialib Pointer [style height-override])
(defn- skia-TextStyle-setHeightOverride [style height-override]
  (assert (pointer? style))
  (skia_TextStyle_setHeightOverride style (int height-override)))

;; TextStyle* skia_TextStyle_setHalfLeading(TextStyle* style, int halfLeading)
(defc skia_TextStyle_setHalfLeading membraneskialib Pointer [style half-leading])
(defn- skia-TextStyle-setHalfLeading [style half-leading]
  (assert (pointer? style))
  (skia_TextStyle_setHalfLeading style (int half-leading)))

;; TextStyle* skia_TextStyle_setLetterSpacing(TextStyle* style, float letterSpacing)
(defc skia_TextStyle_setLetterSpacing membraneskialib Pointer [style letter-spacing])
(defn- skia-TextStyle-setLetterSpacing [style letter-spacing]
  (assert (pointer? style))
  (skia_TextStyle_setLetterSpacing style (float letter-spacing)))

;; TextStyle* skia_TextStyle_setWordSpacing(TextStyle* style, float wordSpacing)
(defc skia_TextStyle_setWordSpacing membraneskialib Pointer [style word-spacing])
(defn- skia-TextStyle-setWordSpacing [style word-spacing]
  (assert (pointer? style))
  (skia_TextStyle_setWordSpacing style (float word-spacing)))

;; TextStyle* skia_TextStyle_setTypeface(TextStyle* style, SkTypeface* typeface)
(defc skia_TextStyle_setTypeface membraneskialib Pointer [style face])
(defn- skia-TextStyle-setTypeface [style face]
  (assert (pointer? style))
  (assert (pointer? face))
  (skia_TextStyle_setTypeface style face))

;; TextStyle* skia_TextStyle_setLocale(TextStyle* style, SkString* locale)
(defc skia_TextStyle_setLocale membraneskialib Pointer [style locale])
(defn- skia-TextStyle-setLocale [style locale]
  (skia_TextStyle_setLocale style (->SkString locale)))

;; TextStyle* skia_TextStyle_setTextBaseline(TextStyle* style, int baseline)
(defc skia_TextStyle_setTextBaseline membraneskialib Pointer [style baseline])
(defn- skia-TextStyle-setTextBaseline [style baseline]
  (assert (pointer? style))
  (skia_TextStyle_setTextBaseline style (int baseline)))

;; TextStyle* skia_TextStyle_setPlaceholder(TextStyle* style)
(defc skia_TextStyle_setPlaceholder membraneskialib Pointer [style])
(defn- skia-TextStyle-setPlaceholder [style]
  (assert (pointer? style))
  (skia_TextStyle_setPlaceholder style))

;; ParagraphStyle* skia_ParagraphStyle_make()
(defc skia_ParagraphStyle_make membraneskialib Pointer)
(defn- skia-ParagraphStyle-make []
  (skia_ParagraphStyle_make))

;; ParagraphStyle* skia_ParagraphStyle_setStrutStyle(ParagraphStyle* paragraphStyle, StrutStyle* strutStyle)
(defc skia_ParagraphStyle_setStrutStyle membraneskialib Pointer [style strut-style])
(defn- skia-ParagraphStyle-setStrutStyle [style strut-style]
  (assert (pointer? style))
  (assert (pointer? strut-style))
  (skia_ParagraphStyle_setStrutStyle style strut-style))

;; ParagraphStyle* skia_ParagraphStyle_setTextStyle(ParagraphStyle* paragraphStyle, TextStyle* textStyle)
(defc skia_ParagraphStyle_setTextStyle membraneskialib Pointer [style text-style])
(defn- skia-ParagraphStyle-setTextStyle [style text-style]
  (assert (pointer? style))
  (assert (pointer? text-style))
  (skia_ParagraphStyle_setTextStyle style text-style))

;; ParagraphStyle* skia_ParagraphStyle_setTextDirection(ParagraphStyle* paragraphStyle, int direction)
(defc skia_ParagraphStyle_setTextDirection membraneskialib Pointer [style direction])
(defn- skia-ParagraphStyle-setTextDirection [style direction]
  (assert (pointer? style))
  (skia_ParagraphStyle_setTextDirection style (int direction)))

;; ParagraphStyle* skia_ParagraphStyle_setTextAlign(ParagraphStyle* paragraphStyle, int align)
(defc skia_ParagraphStyle_setTextAlign membraneskialib Pointer [style align])
(defn- skia-ParagraphStyle-setTextAlign [style align]
  (assert (pointer? style))
  (skia_ParagraphStyle_setTextAlign style (int align)))

;; ParagraphStyle* skia_ParagraphStyle_setMaxLines(ParagraphStyle* paragraphStyle, int maxLines)
(defc skia_ParagraphStyle_setMaxLines membraneskialib Pointer [style max-lines])
(defn- skia-ParagraphStyle-setMaxLines [style max-lines]
  (assert (pointer? style))
  (skia_ParagraphStyle_setMaxLines style (int max-lines)))

;; ParagraphStyle* skia_ParagraphStyle_setEllipsis(ParagraphStyle* paragraphStyle, SkString* ellipsis)
(defc skia_ParagraphStyle_setEllipsis membraneskialib Pointer [style ellipsis])
(defn- skia-ParagraphStyle-setEllipsis [style ellipsis]
  (assert (pointer? style))
  (skia_ParagraphStyle_setEllipsis style (->SkString ellipsis)))

;; ParagraphStyle* skia_ParagraphStyle_setHeight(ParagraphStyle* paragraphStyle, float height)
(defc skia_ParagraphStyle_setHeight membraneskialib Pointer [style height])
(defn- skia-ParagraphStyle-setHeight [style height]
  (assert (pointer? style))
  (skia_ParagraphStyle_setHeight style (float height)))

;; ParagraphStyle* skia_ParagraphStyle_setTextHeightBehavior(ParagraphStyle* paragraphStyle, int v)
(defc skia_ParagraphStyle_setTextHeightBehavior membraneskialib Pointer [style text-height-behavior])
(defn- skia-ParagraphStyle-setTextHeightBehavior [style text-height-behavior]
  (assert (pointer? style))
  (skia_ParagraphStyle_setTextHeightBehavior style (int text-height-behavior)))

;; ParagraphStyle* skia_ParagraphStyle_setReplaceTabCharacters(ParagraphStyle* paragraphStyle, int value)
(defc skia_ParagraphStyle_setReplaceTabCharacters membraneskialib Pointer [style value])
(defn- skia-ParagraphStyle-setReplaceTabCharacters [style value]
  (assert (pointer? style))
  (skia_ParagraphStyle_setReplaceTabCharacters style (int value)))

    ;; SkScalar getMaxWidth() { return fWidth; }
(defc skia_Paragraph_getMaxWidth membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getMaxWidth [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getMaxWidth paragraph))

;; SkScalar getHeight() { return fHeight; }
(defc skia_Paragraph_getHeight membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getHeight [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getHeight paragraph))

;; SkScalar getMinIntrinsicWidth() { return fMinIntrinsicWidth; }
(defc skia_Paragraph_getMinIntrinsicWidth membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getMinIntrinsicWidth [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getMinIntrinsicWidth paragraph))

;; SkScalar getMaxIntrinsicWidth() { return fMaxIntrinsicWidth; }
(defc skia_Paragraph_getMaxIntrinsicWidth membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getMaxIntrinsicWidth [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getMaxIntrinsicWidth paragraph))

;; SkScalar getAlphabeticBaseline() { return fAlphabeticBaseline; }
(defc skia_Paragraph_getAlphabeticBaseline membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getAlphabeticBaseline [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getAlphabeticBaseline paragraph))

;; SkScalar getIdeographicBaseline() { return fIdeographicBaseline; }
(defc skia_Paragraph_getIdeographicBaseline membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getIdeographicBaseline [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getIdeographicBaseline paragraph))

;; SkScalar getLongestLine() { return fLongestLine; }
(defc skia_Paragraph_getLongestLine membraneskialib Float/TYPE [paragraph])
(defn- skia-Paragraph-getLongestLine [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_getLongestLine paragraph))

;; bool didExceedMaxLines() { return fExceededMaxLines; }
(defc skia_Paragraph_didExceedMaxLines membraneskialib Integer/TYPE [paragraph])
(defn- skia-Paragraph-didExceedMaxLines [paragraph]
  (assert (pointer? paragraph))
  (skia_Paragraph_didExceedMaxLines paragraph))

;; virtual void layout(SkScalar width) = 0;
(defc skia_Paragraph_layout membraneskialib void [paragraph width])
(defn- skia-Paragraph-layout [paragraph width]
  (assert (pointer? paragraph))
  (skia_Paragraph_layout paragraph (float width)))

;; virtual void paint(SkCanvas* canvas, SkScalar x, SkScalar y) = 0;
(defc skia_Paragraph_paint membraneskialib void [paragraph resource x y])
(defn- skia-Paragraph-paint [paragraph resource x y]
  (assert (pointer? paragraph))
  (assert (pointer? resource))
  (skia_Paragraph_paint paragraph resource (float x) (float y)))
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
#_#_(defc skia_Paragraph_getGlyphPositionAtCoordinate membraneskialib Pointer* [paragraph dx dy])
(defn- skia-Paragraph-getGlyphPositionAtCoordinate [paragraph dx dy]
  (assert (pointer? paragraph))
  (skia_Paragraph_getGlyphPositionAtCoordinate paragraph))
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


(defn- ->TextStyle [s]
  (reduce-kv (fn [style k v]
               (case k
                 :text-style/shadows style
                 :text-style/background-color style
                 :text-style/baseline-shift style
                 :text-style/color (skia-TextStyle-setColor style v)
                 :text-style/decoration (skia-TextStyle-setDecoration style v)
                 :text-style/decoration-style (skia-TextStyle-setDecorationStyle style v)
                 :text-style/decoration-mode (skia-TextStyle-setDecorationMode style v)
                 :text-style/decoration-color (skia-TextStyle-setDecorationColor style v)
                 :text-style/decoration-thickness-multiplier (skia-TextStyle-setDecorationThicknessMultiplier style v)
                 :text-style/font-families (skia-TextStyle-setFontFamilies style v)
                 :text-style/font-size (skia-TextStyle-setFontSize style v)
                 :text-style/font-style (skia-TextStyle-setFontStyle style v)
                 :text-style/foreground style
                 :text-style/half-leading style
                 :text-style/height style
                 :text-style/height-override style
                 :text-style/letter-spacing style
                 :text-style/locale style
                 :text-style/placeholder? (when v
                                            (skia-TextStyle-setPlaceholder style))
                 :text-style/text-baseline style
                 :text-style/typeface style
                 :text-style/word-spacing style

                 ;; else
                 style))
             (skia-TextStyle-make)
             s))

(defn- make-paragraph*
  ([text]
   (make-paragraph* text Float/POSITIVE_INFINITY))
  ([text width]
   (let [width (or width Float/POSITIVE_INFINITY)
         text-style (doto (skia-TextStyle-make)
                      (skia-TextStyle-setColor [0 0 0]))
         paragraph-style (doto (skia-ParagraphStyle-make)
                           (skia-ParagraphStyle-setTextStyle text-style))
         
         pb (skia-ParagraphBuilder-make paragraph-style)
         
         pb (reduce (fn [pb chunk]
                      (if (string? chunk)
                        (skia-ParagraphBuilder-addText pb chunk)
                        (doto pb
                          (skia-ParagraphBuilder-pushStyle (->TextStyle (:style chunk)))
                          (skia-ParagraphBuilder-addText (:text chunk))
                          (skia-ParagraphBuilder-pop))))
                    pb
                    text)
         paragraph (doto (skia-ParagraphBuilder-build pb)
                     (skia-Paragraph-layout width))]
     paragraph)))

(def make-paragraph (memoize make-paragraph*))

(defrecord Paragraph [paragraph width]
  ui/IOrigin
  (-origin [this]
    [0 0])

  ui/IBounds
  (-bounds [this]
    (let [para (make-paragraph paragraph width)
          width (if (or (nil? width)
                        (= ##Inf width))
                  (skia-Paragraph-getMaxIntrinsicWidth para)
                  width)
          height (skia-Paragraph-getHeight para)]
      [width height]))

  skia/IDraw
  (draw [this]
    (let [paragraph (make-paragraph paragraph width)]
        (skia-Paragraph-paint paragraph skia/*skia-resource* 0 0))))


 



