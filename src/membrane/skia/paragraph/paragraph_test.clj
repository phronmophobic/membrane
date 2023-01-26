(ns membrane.skia.paragraph.paragraph-test
  (:require
   [membrane.skia.paragraph :as para]
   [membrane.skia.paragraph.spec :as ps]
   [membrane.ui :as ui]
   [clojure.edn :as edn]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [membrane.component :refer [make-app defui defeffect]]
   [membrane.skia :as skia]
   [membrane.basic-components :as basic]))

(defn write-edn [w obj]
  (binding [*print-length* nil
            *print-level* nil
            *print-dup* false
            *print-meta* false
            *print-readably* true

            ;; namespaced maps not part of edn spec
            *print-namespace-maps* false

            *out* w]
    (pr obj)))

(def test-paragraph-view-gen
  (gen/such-that
   (fn [view]
     #_(with-open [w ((requiring-resolve 'clojure.java.io/writer) "paragraph.edn")]
       (write-edn w view))
     (let [[w h] (ui/bounds view)]
       (and (<= w 400)
            (<= h 400))))
   (gen/fmap (fn [[paragraph width paragraph-style]]
               (para/paragraph paragraph width paragraph-style))
             (s/gen (s/cat :paragraph ::ps/paragraph
                           :width ::ps/paragraph-width
                           :paragraph-style (s/nilable ::ps/paragraph-style))))
   100))


(declare window-info)
(defeffect ::reroll [$paragraph $running?]
  (if (dispatch! :get $running?)
    (dispatch! :set $running? false)
    (do
      (dispatch! :set $running? true)
      (future
        (while (dispatch! :get $running?)
          (let [paragraph (gen/generate test-paragraph-view-gen)]
            (dispatch! :set $paragraph paragraph)
            ((::skia/repaint window-info))
            (Thread/sleep 100)))
        (dispatch! :set $running? false))))
  (dispatch! :set $paragraph (gen/generate test-paragraph-view-gen)))

(def my-fonts (para/available-font-families))
(defui rand-paragraph-viewer [{:keys [paragraph font-index running?]}]
  (let [font-index (or font-index 0)]
   (ui/vertical-layout
    (basic/button {:text "Reroll"
                   :on-click
                   (fn []
                     [[::reroll $paragraph $running?]])})
    (basic/number-slider {:num font-index
                          :min 0
                          :max (dec (count my-fonts))
                          :integer? true})
    (ui/label (nth my-fonts font-index))
    (ui/translate
     100 100
     [(ui/filled-rectangle
       [0 0 0 0.05]
       450 450)
      (ui/scissor-view [0 0]
                       [450 450]
                       paragraph)]
     #_(para/paragraph [{:text "The quick brown fox jumped over the lazy dog."
                         :style {:text-style/font-families [(nth my-fonts font-index)]
                                 ;; :text-style/color [0 0  0] 
                                 }
                         }])))))

(comment
  (def state (atom {}))
  (def app (make-app #'rand-paragraph-viewer state))
  (def window-info (skia/run app))

  (require 'dev
           '[clojure.java.io :as io])


  ,)



(defn last-paragraph []
  (edn/read-string {:readers {'membrane.skia.paragraph.Paragraph
                              (fn [m]
                                (para/map->Paragraph m))}}
                   (slurp "paragraph.edn")
                   ))

(defn -main [& args]
  (skia/run-sync (constantly
                  (last-paragraph))))





(defn run-random [& args]
  (while true
    (let [paragraph (gen/generate test-paragraph-view-gen)]
      (with-open [w ((requiring-resolve 'clojure.java.io/writer) "paragraph.edn")]
        (write-edn w paragraph))
      (skia/save-image "paragraph.png"
                       paragraph
                       [450 450]))))
