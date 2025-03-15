(ns membrane.skia.impl.paint
  (:require [tech.v3.datatype :as dtype]
            [tech.v3.datatype.struct :as dt-struct]
            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype.native-buffer :as native-buffer]
            
            [membrane.ui :as ui])
  (:import java.lang.ref.Cleaner
           tech.v3.datatype.ffi.Pointer))

(set! *warn-on-reflection* true)
(def ^:private cleaner (delay (Cleaner/create)))

(def paint-fns
  {
   

   :skia_SkRefCntBase_ref {:rettype :void :argtypes '[[o :pointer]]} 

   :skia_Paint_delete {:rettype :void :argtypes '[[paint :pointer]]} 
   :skia_Paint_make {:rettype :pointer :argtypes '[]} 
   :skia_Paint_reset {:rettype :void :argtypes '[[paint :pointer]]} 
   :skia_Paint_isAntiAlias {:rettype :int32 :argtypes '[[paint :pointer]]} 
   :skia_Paint_setAntiAlias {:rettype :void :argtypes '[[paint :pointer] [aa :int32]]} 
   :skia_Paint_isDither {:rettype :int32 :argtypes '[[paint :pointer]]} 
   :skia_Paint_setDither {:rettype :void :argtypes '[[paint :pointer] [dither :int32]]} 
   :skia_Paint_setStroke {:rettype :void :argtypes '[[paint :pointer] [stroke :int32]]} 
   :skia_Paint_getColor {:rettype :int32 :argtypes '[[paint :pointer]]} 
   :skia_Paint_setColor {:rettype :void :argtypes '[[paint :pointer] [color :int32]]} 
   :skia_Paint_getAlphaf {:rettype :float32 :argtypes '[[paint :pointer]]} 
   :skia_Paint_getAlpha {:rettype :int8 :argtypes '[[paint :pointer]]} 
   :skia_Paint_setAlphaf {:rettype :void :argtypes '[[paint :pointer] [a :float32]]} 
   :skia_Paint_getStrokeWidth {:rettype :float32 :argtypes '[[paint :pointer]]} 
   :skia_Paint_setStrokeWidth {:rettype :void :argtypes '[[paint :pointer] [width :float32]]} 
   :skia_Paint_getStrokeMiter {:rettype :float32 :argtypes '[[paint :pointer]]} 
   :skia_Paint_setStrokeMiter {:rettype :void :argtypes '[[paint :pointer] [miter :float32]]} 
   :getStrokeCap {:rettype :int32 :argtypes '[[paint :pointer]]} 
   :skia_Paint_setStrokeCap {:rettype :void :argtypes '[[paint :pointer] [cap :int32]]} 
   :skia_Paint_getStrokeJoin {:rettype :int8 :argtypes '[[paint :pointer]]} 
   :skia_Paint_setStrokeJoin {:rettype :void :argtypes '[[paint :pointer] [join :int8]]} 
   :skia_Paint_getBlendMode_or {:rettype :int32 :argtypes '[[paint :pointer] [default-mode :int32]]} 
   :skia_Paint_isSrcOver {:rettype :int32 :argtypes '[[paint :pointer]]} 
   :skia_Paint_setBlendMode {:rettype :void :argtypes '[[paint :pointer] [mode :int32]]} 
   :skia_SkColor4f_make {:rettype :int32 :argtypes '[[red :float32] [green :float32] [blue :float32] [alpha :float32]]} 
   :skia_SkColor4f_getComponents {:rettype :void :argtypes '[[color :int32] [red* :pointer] [green* :pointer] [blue* :pointer] [alpha* :pointer]]} 
   

   ,})
(dt-ffi/define-library-interface
  paint-fns)

(defmacro defc [& body]
  nil)

(defc skia_Paint_delete membraneskialib void [p])
(defc skia_Paint_make membraneskialib Pointer [])

    ;; void skia_Paint_reset(SkPaint* paint){
(defc skia_Paint_reset membraneskialib void [paint])
;; int skia_Paint_isAntiAlias(SkPaint* paint)  {
(defc skia_Paint_isAntiAlias membraneskialib Integer/TYPE [paint])
;; void skia_Paint_setAntiAlias(SkPaint* paint, int aa) { paint->setAntiAlias(aa); }
(defc skia_Paint_setAntiAlias membraneskialib void [paint aa])
;; int skia_Paint_isDither(SkPaint* paint)  {
(defc skia_Paint_isDither membraneskialib Integer/TYPE [paint])
;; void skia_Paint_setDither(SkPaint* paint, int dither) { paint->setDither(dither); }
(defc skia_Paint_setDither membraneskialib void [paint dither])
;; void skia_Paint_setStroke(SkPaint* paint, int stroke){
(defc skia_Paint_setStroke membraneskialib void [paint stroke])
;; uint32_t skia_Paint_getColor(SkPaint* paint)  { return paint->getColor(); }
(defc skia_Paint_getColor membraneskialib Integer/TYPE [paint])
;; void skia_Paint_setColor(SkPaint* paint, uint32_t color){
(defc skia_Paint_setColor membraneskialib void [paint color])
;; float skia_Paint_getAlphaf(SkPaint* paint)  { return paint->getAlphaf(); }
(defc skia_Paint_getAlphaf membraneskialib Float/TYPE [paint])
;; uint8_t skia_Paint_getAlpha(SkPaint* paint)  {
(defc skia_Paint_getAlpha membraneskialib Byte/TYPE [paint])
;; void skia_Paint_setAlphaf(SkPaint* paint, float a){
(defc skia_Paint_setAlphaf membraneskialib void [paint a])
;; SkScalar skia_Paint_getStrokeWidth(SkPaint* paint)  { return paint->getStrokeWidth(); }
(defc skia_Paint_getStrokeWidth membraneskialib Float/TYPE [paint])
;; void skia_Paint_setStrokeWidth(SkPaint* paint, SkScalar width){
(defc skia_Paint_setStrokeWidth membraneskialib void [paint width])
;; SkScalar skia_Paint_getStrokeMiter(SkPaint* paint)  { return paint->getStrokeMiter(); }
(defc skia_Paint_getStrokeMiter membraneskialib Float/TYPE [paint])
;; void skia_Paint_setStrokeMiter(SkPaint* paint, SkScalar miter){
(defc skia_Paint_setStrokeMiter membraneskialib void [paint miter])
;; // enum Cap {
;; // enum Join : uint8_t {
;; int getStrokeCap(SkPaint* paint)  { return paint->getStrokeCap(); }
(defc getStrokeCap membraneskialib Integer/TYPE [paint])
;; void skia_Paint_setStrokeCap(SkPaint* paint, int cap){
(defc skia_Paint_setStrokeCap membraneskialib void [paint cap])
;; uint8_t skia_Paint_getStrokeJoin(SkPaint* paint)  {
(defc skia_Paint_getStrokeJoin membraneskialib Byte/TYPE [paint])
;; void skia_Paint_setStrokeJoin(SkPaint* paint, uint8_t join){
(defc skia_Paint_setStrokeJoin membraneskialib void [paint join])
;; int skia_Paint_getBlendMode_or(SkPaint* paint, int defaultMode) {
(defc skia_Paint_getBlendMode_or membraneskialib Integer/TYPE [paint default-mode])
;; int skia_Paint_isSrcOver(SkPaint* paint) {
(defc skia_Paint_isSrcOver membraneskialib Integer/TYPE [paint])
;; void skia_Paint_setBlendMode(SkPaint* paint, int mode){
(defc skia_Paint_setBlendMode membraneskialib void [paint mode])

(defc skia_SkColor4f_make membraneskialib Pointer [red green blue alpha])
(defn- skia-SkColor4f-make [r g b a]
  (skia_SkColor4f_make
   (float r)
   (float g)
   (float b)
   (float a)))

(defn ^:private ->color [[r g b a]]
  (skia_SkColor4f_make (float r)
                       (float g)
                       (float b)
                       (float
                        (if a
                          a
                          1))))
(defc skia_SkColor4f_getComponents membraneskialib void [color red* green* blue* alpha*])
(defn float-by-reference []
  (-> (native-buffer/malloc 4
                            {:uninitialized? true
                             :resource-type :auto})
      (native-buffer/set-native-datatype :float32)))

(defn <-color [color]
  (let [red (float-by-reference)
        green (float-by-reference)
        blue (float-by-reference)
        alpha (float-by-reference)]
    (skia_SkColor4f_getComponents color red green blue alpha)
    [(nth red 0)
     (nth green 0)
     (nth blue 0)
     (nth alpha 0)]))

(def sk-blend-modes
  [:clear     ;; //!< r = 0
   :src       ;; //!< r = s
   :dst       ;; //!< r = d
   :src-over  ;; //!< r = s + (1-sa)*d
   :dst-over  ;; //!< r = d + (1-da)*s
   :src-in    ;; //!< r = s * da
   :dst-in    ;; //!< r = d * sa
   :src-out   ;; //!< r = s * (1-da)
   :dst-out   ;; //!< r = d * (1-sa)
   :src-a-top ;; //!< r = s*da + d*(1-sa)
   :dst-a-top ;; //!< r = d*sa + s*(1-da)
   :xor       ;; //!< r = s*(1-da) + d*(1-sa)
   :plus      ;; //!< r = min(s + d, 1)
   :modulate  ;; //!< r = s*d
   :screen    ;; //!< r = s + d - s*d

   :overlay     ;; //!< multiply or screen, depending on destination
   :darken      ;; //!< rc = s + d - max(s*da, d*sa), ra = kSrcOver
   :lighten     ;; //!< rc = s + d - min(s*da, d*sa), ra = kSrcOver
   :color-dodge ;; //!< brighten destination to reflect source
   :color-burn  ;; //!< darken destination to reflect source
   :hard-light  ;; //!< multiply or screen, depending on source
   :soft-light  ;; //!< lighten or darken, depending on source
   :difference ;; //!< rc = s + d - 2*(min(s*da, d*sa)), ra = kSrcOver
   :exclusion  ;; //!< rc = s + d - two(s*d), ra = kSrcOver
   :multiply   ;; //!< r = s*(1-da) + d*(1-sa) + s*d

   :hue ;; //!< hue of source with saturation and luminosity of destination
   :saturation ;; //!< saturation of source with hue and luminosity of destination
   :color ;; //!< hue and saturation of source with luminosity of destination
   :luminosity ;; //!< luminosity of source with hue and saturation of destination

   ;; :LastCoeffMode     = kScreen ;; //!< last porter duff blend mode
   ;; :LastSeparableMode = kMultiply ;; //!< last blend mode operating separately on components
   ;; :LastMode          = kLuminosity ;; //!< last valid value
   ])

(def ^:private ->blend-mode
  (into {}
        (map-indexed (fn [i kw]
                       [kw (int i)]))
        sk-blend-modes))

(def ^:private <-blend-mode
  (into {}
        (map-indexed (fn [i kw]
                       [(int i) kw]))
        sk-blend-modes))




    
;; enum Cap {
;;     kButt_Cap,                  //!< no stroke extension
;;     kRound_Cap,                 //!< adds circle
;;     kSquare_Cap,                //!< adds square
;;     kLast_Cap    = kSquare_Cap, //!< largest Cap value
;;     kDefault_Cap = kButt_Cap,   //!< equivalent to kButt_Cap
;; };

(def skpaint-stroke-caps
  [:butt
   :round
   :square])

(def ^:private ->cap
  (into {}
        (map-indexed (fn [i kw]
                       [kw (int i)]))
        skpaint-stroke-caps))

(def ^:private <-cap
  (into {}
        (map-indexed (fn [i kw]
                       [(int i) kw]))
        skpaint-stroke-caps))


;; enum Join : uint8_t {
;;     kMiter_Join,                 //!< extends to miter limit
;;     kRound_Join,                 //!< adds circle
;;     kBevel_Join,                 //!< connects outside edges
;;     kLast_Join    = kBevel_Join, //!< equivalent to the largest value for Join
;;     kDefault_Join = kMiter_Join, //!< equivalent to kMiter_Join
;; };

(def skpaint-joins
  [:miter
   :round
   :bevel])

(def ^:private ->join
  (into {}
        (map-indexed (fn [i kw]
                       [kw (int i)]))
        skpaint-joins))

(def ^:private <-join
  (into {}
        (map-indexed (fn [i kw]
                       [(int i) kw]))
        skpaint-joins))



(defn ^:private ->bool [b]
  (if b
    (int 1)
    (int 0)))

(defn ^:private <-bool [i]
  (not (zero? i)))


(defmacro ^:private add-cleaner [type p]
  (let [delete-sym (symbol (str "skia_" type "_delete"))]
      `(let [p# ~p
             ptr# (dt-ffi/pointer->address p#)]
         (.register ^Cleaner @cleaner p#
                    (fn []
                      (~delete-sym (Pointer. ptr#))))
         p#)))

(defn- skia-Paint-make []
  (add-cleaner
   Paint
   
   (proxy [Pointer
           clojure.lang.ILookup]
       [(long (dt-ffi/pointer->address (skia_Paint_make)))
        nil]

       (valAt [k]
         (case k
           :anti-alias? (<-bool (skia_Paint_isAntiAlias this))
           :dither? (<-bool (skia_Paint_isDither this))
           :color (<-color (skia_Paint_getColor this))
           :alpha (skia_Paint_getAlphaf this)
           :stroke-width (skia_Paint_getStrokeWidth this)
           :stroke-miter (skia_Paint_getStrokeMiter this)
           :stroke-cap (<-cap (getStrokeCap this))
           :stroke-join (<-join (skia_Paint_getStrokeJoin this))
           :blend-mode (<-blend-mode (skia_Paint_getBlendMode_or this -1))
           ;; else
           nil)))))

(defn ->SkPaint
  ([m]
   (->SkPaint (skia-Paint-make)
              m))
  ([paint m]
   (reduce-kv (fn [paint k v]
                (case k
                  :anti-alias? (skia_Paint_setAntiAlias paint (->bool v))
                  :dither? (skia_Paint_setDither paint (->bool v))
                  :stroke? (skia_Paint_setStroke paint (->bool v))
                  :color (skia_Paint_setColor paint (->color v))
                  :alpha (skia_Paint_setAlphaf paint (float v))
                  :stroke-width (skia_Paint_setStrokeWidth paint (float v))
                  :stroke-miter (skia_Paint_setStrokeMiter paint (float v))
                  :stroke-cap (skia_Paint_setStrokeCap paint (->cap v))
                  :stroke-join (skia_Paint_setStrokeJoin paint (->join v))
                  :blend-mode (skia_Paint_setBlendMode paint (->blend-mode v))

                  ;; else
                  nil)
                paint)
              paint
              m)))
