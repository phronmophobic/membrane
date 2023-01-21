(ns membrane.skia.paragraph.spec
  (:require
   [membrane.skia.paragraph :as para]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]))


(s/def ::zero-to-one (s/and
                      number?
                      #(>= % 0)
                      #(<= % 1)))

(s/def ::color
  (s/coll-of ::zero-to-one
             :min-count 3
             :max-count 4))

(defn within-normal-range? [n]
  (<= (Math/abs n)
      1000))

(defn with-normal-range-gen [spec]
  (s/with-gen spec
    #(gen/such-that within-normal-range?
                    (s/gen spec)
                    500)))

(s/def ::positive-int
  (with-normal-range-gen
    (s/and number?
           #(>= % 0)
           #(try
              (int %)
              (catch IllegalArgumentException e
                false)))))

(s/def ::integer
  (with-normal-range-gen
    (s/and number?
           #(try
              (int %)
              (catch IllegalArgumentException e
                false)))))

(s/def ::float
  (with-normal-range-gen
    (s/and number?
           #(try
              (float %)
              (catch IllegalArgumentException e
                false)))))

(s/def ::positive-float
  (with-normal-range-gen
    (s/and number?
           #(>= % 0)
           #(try
              (float %)
              (catch IllegalArgumentException e
                false)))))

(s/def :font-style/weight
  (s/or :number ::positive-int
        :named #{:invisible 
                 :thin 
                 :extra-light 
                 :light 
                 :normal 
                 :medium 
                 :semi-bold 
                 :bold 
                 :extra-bold
                 :black 
                 :extra-black}))

(s/def :font-style/width
  (s/or :number #{1 2 3 4 5 6 7 8 9}
        :named #{:ultracondensed
                 :extracondensed
                 :condensed
                 :semicondensed
                 :normal
                 :semiexpanded
                 :expanded
                 :extraexpanded
                 :ultraexpanded}))

(s/def :font-style/slant
  (s/or :number #{1 2 3}
        :named #{:upright
                 :italic
                 :oblique}))

(s/def ::font-style
  (s/keys :opt
          [:font-style/weight
           :font-style/width
           :font-style/slant]))

(s/def :text-style/shadows
  (s/coll-of :text-style/shadow))
(s/def :text-style/background-color ::color)
(s/def :text-style/baseline-shift ::positive-float)
(s/def :text-style/color ::color)
(s/def :text-style/decoration
  (s/coll-of #{:text-decoration/no-decoration
               :text-decoration/underline
               :text-decoration/overline
               :text-decoration/line-through}
             :into #{}))
(s/def :text-style/decoration-style
  #{:text-decoration-style/solid 
    :text-decoration-style/double 
    :text-decoration-style/dotted 
    :text-decoration-style/dashed 
    :text-decoration-style/wavy})
(s/def :text-style/decoration-mode
  #{:text-decoration-mode/gaps
    :text-decoration-mode/through})
(s/def :text-style/decoration-color ::color)
(s/def :text-style/decoration-thickness-multiplier
  (s/and ::positive-float
         ;; skia hard crashed with numbers near zero
         #(>= % 0.1)))
(s/def :text-style/font-families
  (s/with-gen (s/coll-of string?)
    (fn []
      (s/gen (s/coll-of (set (para/available-font-families)))))))
(s/def :text-style/font-size ::positive-float)
(s/def :text-style/font-style ::font-style)


(s/def :text-style/half-leading ::integer)
(s/def :text-style/height ::float)
(s/def :text-style/height-override ::integer)
(s/def :text-style/letter-spacing ::float)
(s/def :text-style/locale string?)
(s/def :text-style/placeholder? boolean?)
(s/def :text-style/text-baseline ::integer)
(s/def :text-style/word-spacing ::float)

(s/def ::paint #{})
(s/def ::typeface #{})
(s/def :text-style/typeface ::typeface)
(s/def :text-style/foreground ::paint)

(s/def ::text-style
  (s/keys
   :opt [:text-style/font-families
         :text-style/baseline-shift
         :text-style/color
         :text-style/decoration
         :text-style/decoration-style
         :text-style/decoration-mode
         :text-style/decoration-color
         :text-style/decoration-thickness-multiplier

         :text-style/font-size
         :text-style/font-style

         :text-style/half-leading
         :text-style/height
         :text-style/height-override
         :text-style/letter-spacing
         :text-style/locale
         :text-style/placeholder?
         :text-style/text-baseline
         :text-style/word-spacing
         ;; unimplemented
         ;; :text-style/typeface
         ;; :text-style/foreground
         ;; :text-style/shadows
         ;; :text-style/background-color
         ]))
(s/def :styled-text/text
  (s/with-gen string?
    #(gen/string)))
(s/def :styled-text/style ::text-style)

(s/def ::styled-text
  (s/or :string string?
        :styled (s/keys :opt-un [:styled-text/style]
                        :req-un [:styled-text/text])))

(s/def ::paragraph
  (s/coll-of ::styled-text))


(s/def ::paragraph-width (s/or :nil nil?
                               :number ::positive-float))



