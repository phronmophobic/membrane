^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(ns notebooks.paragraph
  (:require [nextjournal.clerk :as clerk]
            [membrane.skia :as skia]
            [membrane.skia.paragraph :as para]
            [membrane.ui :as ui]
            [clojure.java.io :as io]
            [clojure.math.combinatorics :as combo]
            [membrane.skia.paragraph.spec :as ps])
  (:import javax.imageio.ImageIO))


^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(defn paragraph? [p]
  (instance? membrane.skia.paragraph.Paragraph p))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def paragraph-viewer
  {:pred paragraph?
   :transform-fn
   (clerk/update-val
    
    (fn [p]
      (let [path (str "tmp/paragraph-" (hash p) ".png")]
        (skia/save-image path (ui/padding 4 p))
        (ImageIO/read (io/file path)))))})

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(clerk/add-viewers! [paragraph-viewer])


;; ## Styled Text

;; ### Basic Usage

;; For our examples, we'll assume the following require:
;; ```clojure
;; (require '[membrane.skia.paragraph :as para])
;; ```
;; `para/paragraph` requires version `0.11.1-beta` or later.
;; ```clojure
;; com.phronemophobic/membrane {:mvn/version "0.11.1-beta"}
;; com.phronemophobic.membrane/skialib-linux-x86-64 {:mvn/version "0.11.1-beta"}
;; com.phronemophobic.membrane/skialib-macosx-aarch64 {:mvn/version "0.11.1-beta"}
;; com.phronemophobic.membrane/skialib-macosx-x86-64 {:mvn/version "0.11.1-beta"}
;; ```
;; The main entry point is a single function, `para/paragraph`.
;; The most basic usage is calling `para/paragraph` with a string
;; to render the text using the default styling.

(para/paragraph "The quick brown fox jumped over the lazy dog.")

;; `para/paragraph` can also take a `width` as the second argument.
;; The rendered text will be wrapped to fit within `width` pixels.


(para/paragraph "The quick brown fox jumped over the lazy dog."
                100)


;; For brevity, we'll use the following `text` definition
;; throughout the rest of the examples.

{:nextjournal.clerk/visibility {:code :show :result :hide}}
(def text "The quick brown fox 🦊 ate a zesty hamburgerfons 🍔.\nThe 👩‍👩‍👧‍👧 laughed.")
{:nextjournal.clerk/visibility {:code :show :result :show}}
(para/paragraph text)

;; For styled text, `para/paragraph` also accepts a map of two keys: `text` and `style`.
(para/paragraph {:text text
                 :style #:text-style {:font-families ["Menlo"]}}
                150)

;; To apply multiple stylings, pass a sequence of styled or unstyled text.
(para/paragraph ["This is unstyled text.\n"
                 {:text "This is some styled text.\n"
                  :style #:text-style {:font-families ["Menlo"]}}
                 {:text "This is some differently styled text.\n"
                  :style #:text-style {:font-families ["Zapfino"]}}])

;; ## Text Style Properties

;; Examples and descriptions of the available text style properties
;; are described below. For additional info, check out the
;; specs in `membrane.skia.paragraph.spec`.

;; ### :text-style/font-families

;; A sequence of font families. The families will be tried, in order,
;; until a matching family is found on the system.

(para/paragraph {:text "Font families is a collection of font families to try. If a font family isn't available, then the next font family is tried until a matching family is found."
                 :style {:text-style/font-families ["Foobar" "Papyrus"]}}
                300)

;; ### :text-style/font-size
;; Specifies the size of the font in pixels.
(para/paragraph
 (for [font-size (range 10 30 4)]
   {:text text
    :style #:text-style {:font-size font-size}}))


;; ### :text-style/font-style
;; A map that represents the font weight, slant, and width. Most fonts don't support every style.

;; #### :font-style/weight
;; valid values: `:invisible`, `:thin`, `:extra-light`, `:light`, `:normal`, `:medium`, `:semi-bold`, `:bold`, `:extra-bold`, `:black`, `:extra-black`
;; #### :font-style/slant
;; valid values: `:upright`, `:italic`, `:oblique`
;; #### :font-style/width
;; valid values: `:ultracondensed` `:extracondensed` `:condensed` `:semicondensed` `:normal` `:semiexpanded` `:expanded` `:extraexpanded` `:ultraexpanded`

(para/paragraph
 (for [weight [:invisible 
               :thin 
               :extra-light 
               :light 
               :normal 
               :medium 
               :semi-bold 
               :bold 
               :extra-bold
               :black 
               :extra-black]
       
       slant [:upright
              :italic
              :oblique]
       width [:ultracondensed
              :extracondensed
              :condensed
              :semicondensed
              :normal
              :semiexpanded
              :expanded
              :extraexpanded
              :ultraexpanded]]
   {:text "h"
    :style #:text-style {:font-style #:font-style{:weight weight
                                                  :width width
                                                  :slant slant}
                         :font-families ["SF Pro"]
                         :font-size 12}})
 400)


;; ### :text-style/baseline-shift
;; Shifts the baseline by the amount (in pixels).

(para/paragraph (for [i (range 20)]
                  {:text (str i)
                   :style {:text-style/baseline-shift i}}))

;; ### :text-style/color
;; Specify the color of the text. Colors are either
;; `[r g b]` or `[r g b a]` with values in the range `[0,1]` inclusive.

(para/paragraph (for [g (range 10)]
                  {:text text
                   :style #:text-style {:color [1 (/ g 10.0) 0]}}))


;; ### :text-style/decoration
;; A set of decorations. Valid decorations are any subset of:

{:nextjournal.clerk/visibility {:code :show :result :hide}}
(def decorations #{:text-decoration/no-decoration
                   :text-decoration/underline
                   :text-decoration/overline
                   :text-decoration/line-through})
{:nextjournal.clerk/visibility {:code :show :result :show}}

(para/paragraph (for [i (range 1 (inc (count decorations)))
                      decoration (combo/combinations (seq decorations) i)]
                  {:text (str (pr-str decoration) "\n")
                   :style #:text-style {:decoration decoration}}))


;; ### :text-style/decoration-style
;; The decoration style. Useful only alongside `:text-style/decoration`.
;; Valid decorations are:

{:nextjournal.clerk/visibility {:code :show :result :hide}}
(def decoration-styles
  #{:text-decoration-style/solid 
    :text-decoration-style/double 
    :text-decoration-style/dotted 
    :text-decoration-style/dashed 
    :text-decoration-style/wavy})
{:nextjournal.clerk/visibility {:code :show :result :show}}

(para/paragraph (for [dstyle decoration-styles]
                  {:text (str (pr-str dstyle) "\n")

                   :style #:text-style {:decoration #{:text-decoration/line-through}
                                        :font-size 42
                                        :decoration-style dstyle}}))


;; ### :text-style/decoration-color
;; The decoration color. Colors are either
;; `[r g b]` or `[r g b a]` with values in the range `[0,1]` inclusive.
;; Useful only alongside `:text-style/decoration`.

(para/paragraph
 {:text text
  :style #:text-style {:decoration #{:text-decoration/overline}
                       :font-size 42
                       :decoration-color [1 0 0]}}
 400)

;; ### :text-style/decoration-thickness-multiplier
;; This will scale the thickness of the decorations.
;; Scalars must be positive values not near zero.
;; Useful only alongside `:text-style/decoration`.
(para/paragraph
 (for [multiplier (range 1 10)]
   {:text text
    :style #:text-style {:decoration #{:text-decoration/overline }
                         :text-style/decoration-thickness-multiplier multiplier
                         }}))








;; ### :text-style/height, :text-style/height-override
;; `:text-style/height` is a height multiplier. `:text-style/height` has no effect if `:text-style/height-override` is unspecified or false.

(para/paragraph
 (for [i (range 1 4)]
   {:text (str text "\n")
    :style #:text-style {:height i
                         :height-override true
                         :font-families ["SF Pro"]}}))



;; ### :text-style/letter-spacing
(para/paragraph
 (for [[i c] (map-indexed vector "This sentence is slowing down...") ]
   {:text (str c)
    :style #:text-style {:letter-spacing i}})
 400)




;; ### :text-style/word-spacing
(para/paragraph
 (for [i (range 20)]
   {:text (str i " ")
    :style #:text-style {:word-spacing i}})
 400)



^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(comment
  ;; I'm not sure what this does
  :text-style/locale

  ;; Not sure what this does
  :text-style/text-baseline

  ;; Not implemented yet
  :text-style/placeholder?

  ;; I don't know what this does
  :text-style/decoration-mode
  (para/paragraph (for [dmode [:text-decoration-mode/gaps
                               :text-decoration-mode/through]]
                    {:text (str "Asdf aslkd fjlsadkfj asdlkf" "\n")

                     :style #:text-style {:decoration #{:text-decoration/overline}
                                          :font-size 42
                                          :decoration-mode dmode}})
                  400)


  ;; I don't know what this does
  :text-style/half-leading
  (para/paragraph
   (for [i (range 10 100 10)]
     {:text (str text "\n")
      :style #:text-style {:half-leading i}}))

  )


^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(comment
  (clerk/serve! {:watch-paths ["notebooks/paragraph.clj"]})
  
  (clerk/show! "notebooks/paragraph.clj")
  (clerk/build! {:paths ["notebooks/paragraph.clj"]
                 :out-path "docs/styled-text"
                 :bundle true})
  
  ,)
