(ns membrane.vdom
  (:require-macros [membrane.ui :refer [add-default-draw-impls-cljs! defcomponent]]
                   [membrane.component :refer [defui]]
                   [cljs.tools.reader.reader-types])
  
  (:require [vdom.core :as vdom]
            [membrane.basic-components :as basic
             :refer [textarea]]
            [membrane.ui :as ui
             :refer [IBounds
                     IOrigin
                     IMouseDown
                     bounds
                     vertical-layout
                     horizontal-layout
                     rectangle
                     bordered
                     filled-rectangle
                     defcomponent
                     label
                     children
                     image]]
            [cljs.core.async :refer [put! chan <! timeout dropping-buffer promise-chan]
             :as async]
            [cljs.js :as cljs]
            [cljs.env :as env]
            cljs.analyzer
            [membrane.component :refer [defui]]
            [membrane.eval :as eval]
            [cognitect.transit :as transit]

            cljs.tools.reader.reader-types
            [membrane.example.todo :as todo]))


(def ^:dynamic *ctx*)
(defonce event-handlers (atom {}))
(def freetype-font)

(defprotocol IRender
  (render [this]))

;; (add-default-draw-impls-cljs! IDraw draw)

(defonce freetype-callbacks (atom []))
(defn on-freetype-loaded [callback]
  (if freetype-font
    (callback freetype-font)
    (swap! freetype-callbacks conj callback)))

(js/opentype.load
 "https://fonts.gstatic.com/s/ubuntu/v10/4iCs6KVjbNBYlgo6eA.ttf"
 (fn [err font]
   (if err
     (do (println "Error: " err)
         (js/console.log err))
     (do
       (set! freetype-font font)
       (reset! membrane.component/component-cache {})
       (doseq [cb @freetype-callbacks]
         (cb freetype-font))
       (reset! freetype-callbacks [])))))

(defn load-font []
  (let [link (.createElement js/document "link")]
    (doto link
      (.setAttribute "rel" "stylesheet")
      (.setAttribute "href" "https://fonts.googleapis.com/css?family=Ubuntu&display=swap"))
    (.appendChild (-> js/document .-body)
                  link)))
(load-font)


(defn font-scale [freetype-font font-size]
  (* (/ 1 (aget freetype-font "unitsPerEm"))
     font-size))

(defn get-font [font]
  freetype-font)

(defn font-units->pixels [font font-units]
  (let [font-size (get font :size (:size ui/default-font))
        fscale (font-scale (get-font font) font-size)]
    (* font-units fscale)))

(defn font-line-height [font]
  (let [os2 (-> (get-font font)
                (aget "tables")
                (aget "os2"))
        sTypoAscender (font-units->pixels font (aget os2  "sTypoAscender"))
        sTypoDescender (font-units->pixels font (aget os2  "sTypoDescender"))
        sTypoLineGap (font-units->pixels font (aget os2  "sTypoLineGap"))
        line-height (+ (- sTypoAscender sTypoDescender) sTypoLineGap)]
    line-height))

(defn line-bounds [font text]
  (let [maxy (volatile! 0)
        maxx (.forEachGlyph (get-font freetype-font)
                            text
                            0 0
                            (or (:size font)
                                (:size ui/default-font))
                            (js-obj {:kerning false})
                            (fn [glyph gx gy gFontSize]
                              (vswap! maxy max (or (aget glyph "yMax") 0))))]
    [maxx (font-units->pixels font @maxy)]))

(defn ->px [num]
  (str num "px"))

(defn text-bounds [font text]
  (let [lines (clojure.string/split text #"\n" -1)
        bounds (map #(line-bounds font %) lines)
        maxx (reduce max 0 (map first bounds))
        maxy (* (dec (font-line-height font))
                (count lines))]
    [maxx maxy]))

(set! membrane.ui/text-bounds text-bounds)




(defn render-rect []

  [:div {:style {:width "100px"
                 :height "50"
                 :background-color "green"}}])



(defn index-for-position [font text px py]
  (let [lines (clojure.string/split text #"\n" -1)
        line-height (font-line-height font)
        line-index (int (/ py line-height))]
    (if (>= line-index (count lines))
      (count text)
      (let [line (nth lines line-index)
            font-size (get font :size (:size ui/default-font))
            options (js-obj {:kerning true})
            glyphs (.stringToGlyphs freetype-font line options)
            position  (aget freetype-font "position")
            script (.getDefaultScriptName position)
            kerning-lookups (.getKerningTables position
                                               script
                                               nil)
            fscale (font-scale freetype-font font-size)
            column-index (loop [idx 0
                                x 0
                                y 0]
                           (if (< px x)
                             (dec idx)
                             (if (< idx (alength glyphs))
                               (let [glyph (aget glyphs idx)
                                     x (if (aget glyph "advanceWidth")
                                         (+ x (* fscale (aget glyph "advanceWidth")))
                                         x)
                                     x (if (< idx (dec (alength glyphs)))
                                         (let [next-glyph (aget glyphs (inc idx))
                                               kerning-value (if kerning-lookups
                                                               (.getKerningValue position (aget glyph "index") (aget next-glyph "index"))
                                                               (.getKerningValue freetype-font glyph next-glyph))]
                                           (+ x (* kerning-value fscale)))
                                         x)]
                                 (recur (inc idx)
                                        x
                                        y))
                               idx)))]
        (apply + column-index (map #(inc (count %)) (take line-index lines)))))
    ))
(set! membrane.ui/index-for-position index-for-position)

(extend-type membrane.ui.Label
  IBounds
  (-bounds [this]
    (let [font (:font this)]
     (text-bounds font
                  (:text this))))
  IRender
  (render [this]
    (when (seq (:text this))
      [:div {:style (merge
                     {:margin 0
                      :padding 0
                      ;; :width "1000px"
                      :white-space "nowrap"
                      :position "absolute"
                      :left 0
                      :top 0}
                     (when-let [font (:font this)]
                       {:font (str (when (:weight font)
                                     (str (:weight font) " "))
                                   (or (:size font)
                                       (:size ui/default-font))
                                   "px "
                                   (or (:name font)
                                       (:name ui/default-font)))}))
             
             :className "label"}
       (let [line-height (font-line-height (:font this)) ]
         (map-indexed (fn [i line]
                        [:div {:style {:position "absolute"
                                       :top (str (* i (dec line-height))
                                                 "px")
                                       :left 0}}
                         line])
                      (clojure.string/split-lines (:text this))))])
    #_(let [lines (clojure.string/split (:text this) #"\n" -1)
          font (:font this)
          line-height (font-line-height font)]
      (push-state *ctx*
                  
                  (doseq [line lines]
                    (.translate *ctx* 0 (dec line-height))
                    (case *paint-style*

                      :membrane.ui/style-fill (.fillText *ctx* line 0 0)
                      :membrane.ui/style-stroke (.strokeText *ctx* line 0 0)
                      :membrane.ui/style-stroke-and-fill (do
                                                           (.fillText *ctx* line 0 0)
                                                           (.strokeText *ctx* line 0 0)))
                    )))
    ))

(extend-type nil
  IRender
  (render [this]
    nil))

(extend-type cljs.core/PersistentVector
  IRender
  (render [this]
    [:div {:style {:position "relative"
                   :left 0
                   :top 0}
           :className "vec"}
     (doall
      (for [drawable this]
        (render drawable)))]))

(extend-type membrane.ui.Group
  IRender
  (render [this]
    [:div {:style {:position "relative"
                   :left 0
                   :top 0}
           :className "group"}
     (doall
      (for [drawable this]
        (render drawable)))]
    #_(doall
       (for [drawable this]
         (render drawable)))))

(extend-type membrane.ui.Spacer
  IRender
  (render [this]
    nil))

(extend-type membrane.ui.FixedBounds
  IRender
  (render [this]
    (let [[w h] (:size this)]
      [:div {:style {:width (str w "px")
                     :height (str h "px")
                     :position "absolute"
                     :left 0
                     :top 0}
             :className "fixed-bounds"}
       (render (:drawable this))])))

(extend-type default
  IRender
  (render [this]
    (doall
     (for [drawable (children this)]
       (render drawable)))
    #_[:div {:style {:position "relative"
              :left 0
                     :top 0}
             :className "default"}
     ]
    #_(doall
       (for [drawable (children this)]
         (render drawable)))))

(extend-type membrane.ui.Padding
  IRender
  (render [this]
    (let [px (:px this)
          py (:py this)]
      [:div {:style {:padding-x (str px "px")
                     :padding-y (str py "px")
                     :position "absolute"
                     :left 0
                     :top 0}
             :className "padding"}
       (render (:drawable this))])))

(extend-type membrane.ui.Bordered
  IRender
  (render [this]
    (render (ui/bordered-draw this))))



(defcomponent Checkbox [checked?]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [this]
        ;; TODO: do better
        [12 12])

    IRender
    (render [this]
    (let [checked (:checked? checked?)]
      [:input (merge
               {:type "checkbox"
                :style {:pointer-events "none"}}
               (when checked?
                 {:checked "on"}))])))

(extend-type membrane.ui.Checkbox
  IRender
  (render [this]
    (render (ui/draw-checkbox (:checked? this)))))


(defn checkbox
  "Graphical elem that will draw a checkbox."
  [checked?]
  (Checkbox. checked?))

(defcomponent Button [text on-click hover?]
    IOrigin
    (-origin [_]
        [0 0])

    IBounds
    (-bounds [_]
        (let [[text-width text-height] (bounds (label text))
              padding 12
              btn-width (+ text-width padding)
              btn-height (+ text-height padding)]
          [btn-width btn-height]))
  IMouseDown
  (-mouse-down [this [mx my]]
      (when on-click
        (on-click)))

  IRender
  (render [this]
      (render (ui/button-draw this))))

(extend-type membrane.ui.Button
  IRender
  (render [this]
    (render (ui/button-draw this))))


(extend-type membrane.ui.Rectangle
  IRender
  (render [this]
    (let [{:keys [width height border-radius]} this
          border-radius-px (str border-radius "px")
          ]
      (case (:style *ctx*)
        :membrane.ui/style-fill
        [:div {:style {:background-color (:color *ctx*)
                       :border-color (:color *ctx*)
                       :border-width "0"
                       :width (str width "px")
                       :height (str height "px")
                       :position "absolute"
                       :left 0
                       :top 0}
               :className "rectangle"}]
        :membrane.ui/style-stroke
        [:div {:style {:border-color (:color *ctx*)
                       :border-width (->px (:stroke-width *ctx*))
                       :border-style "solid"
                       :width (str width "px")
                       :height (str height "px")
                       :position "absolute"
                       :left 0
                       :top 0}
               :className "rectangle"}]
        :membrane.ui/style-stroke-and-fill
        [:div {:style {:border-color (:color *ctx*)
                       :border-width (->px (:stroke-width *ctx*))
                       :border-style "solid"
                       :background-color (:color *ctx*)
                       :width (str width "px")
                       :height (str height "px")
                       :position "absolute"
                       :left 0
                       :top 0}
               :className "rectangle"}]))))

(defn button
  "Graphical elem that draws a button. Optional on-click function may be provided that is called with no arguments when button has a mouse-down event."
  ([text]
   (Button. text nil false))
  ([text on-click]
   (Button. text on-click false))
  ([text on-click hover?]
   (Button. text on-click hover?)))



(defonce images (atom {}))


(defn image-size [image-path]
  (-> @images
      (get image-path)
      :size))
(set! membrane.ui/image-size image-size)


(extend-type membrane.ui.Image
  IBounds
  (-bounds [this]
    (:size this))
  IRender
  (render [this]
    (when-let [image-info (get @images (:image-path this))]
      (let [[width height] (:size this)
            props (if-let [opacity (:opacity this)]
                    {:style {:opacity opacity}}
                    {})
            props (merge props
                         {:src (:url image-info)
                          :width (str width "px")
                          :height (str height "px")}
                         )]
        [:img props]))
    #_(when-let [image-info (get @images (:image-path this))]
      (let [[width height] (:size this)]
        (push-state *ctx*
                    (when-let [opacity (:opacity this)]
                      (set! (.-globalAlpha *ctx*) opacity))
                    (.drawImage *ctx*
                                (:image-obj image-info)
                                0 0
                                width height))))))
(extend-type membrane.ui.Translate
  IRender
  (render [this]
    [:div {:style {:position "absolute"
                   :left (str (:x this) "px")
                   :top (str (:y this) "px")}
           :className "Translate"}
     (render (:drawable this))]
    #_(push-state *ctx*
                (.translate *ctx* (:x this) (:y this))
                (draw (:drawable this)))))


(defn render-selection [font text selection-start selection-end color]
  (let [font-size (get font :size (:size ui/default-font))
        options (js-obj {:kerning true})
        text (str text "8")
        glyphs (.stringToGlyphs freetype-font text options)
        position  (aget freetype-font "position")
        script (.getDefaultScriptName position)
        kerning-lookups (.getKerningTables position
                                           script
                                           nil)
        fscale (font-scale freetype-font font-size)
        line-height (font-line-height font)
        selection-height (font-units->pixels font (-> freetype-font
                                                      (aget "tables")
                                                      (aget "hhea")
                                                      (aget "ascender")))]
    (loop [x 0
           y 0
           selection-start selection-start
           selection-length (- selection-end selection-start)
           idx 0
           elems []]
      (if (pos? selection-length)
        (let [c (nth text idx)
              glyph (nth glyphs idx)]
          (let [new-x (cond
                        (= c "\n") 0
                        (aget glyph "advanceWidth") (let [new-x (+ x (* fscale (aget glyph "advanceWidth")))]
                                                 (if (< idx (dec (alength glyphs)))
                                                   (let [next-glyph (aget glyphs (inc idx))
                                                         kerning-value (if kerning-lookups
                                                                         (.getKerningValue position (aget glyph "index") (aget next-glyph "index"))
                                                                         (.getKerningValue freetype-font glyph next-glyph))]
                                                     (+ new-x (* kerning-value fscale)))
                                                   x))
                        :else x)
                new-y (if (= c "\n")
                        (+ y (dec line-height))
                        y)]
            (if (<= selection-start 0)
              (do
                (let [selection-width (if (= c "\n")
                                        5
                                        (- new-x x))]
                  (recur new-x new-y 0 (dec selection-length) (inc idx)
                         (conj elems
                               (ui/translate x (+ y (- line-height
                                                       selection-height))
                                             (filled-rectangle color
                                                               selection-width selection-height))))))
              (recur new-x new-y (dec selection-start) selection-length (inc idx) elems))))
        (render elems))
      ))
  )

(extend-type membrane.ui.TextSelection
  IBounds
  (-bounds [this]
    (text-bounds (:font this) (:text this)))

  IRender
  (render [this]
    (let [{:keys [text font]
           [selection-start selection-end] :selection} this]
      (render-selection (:font this) text selection-start selection-end
                        [0.6980392156862745
                         0.8431372549019608
                         1]))))

(extend-type membrane.ui.TextCursor
  IBounds
  (-bounds [this]
    (text-bounds (:font this) (:text this)))

  IRender
  (render [this]
    (let [cursor (min (count (:text this)) (:cursor this))]
      (render-selection (:font this) (str (:text this) "8") cursor (inc cursor)
                        [0.9 0.9 0.9]))
    ))



(extend-type membrane.ui.RoundedRectangle
  IRender
  (render [this]
    (let [{:keys [width height border-radius]} this
          border-radius-px (str border-radius "px")
          ]
      (case (:style *ctx*)
        :membrane.ui/style-fill
        [:div {:style {:background-color (:color *ctx*)
                       "-moz-border-radius" border-radius-px
                       "-webkit-border-radius" border-radius-px
                       "border-radius" border-radius-px
                       :border-color (:color *ctx*)
                       :border-width "0"
                       :width (str width "px")
                       :height (str height "px")
                       :position "absolute"
                       :left 0
                       :top 0}
               :className "RoundedRectangle"}]
        :membrane.ui/style-stroke
        [:div {:style {:border-color (:color *ctx*)
                       "-moz-border-radius" border-radius-px
                       "-webkit-border-radius" border-radius-px
                       "border-radius" border-radius-px
                       :border-width (->px (:stroke-width *ctx*))
                       :border-style "solid"
                       :width (str width "px")
                       :height (str height "px")
                       :position "absolute"
                       :left 0
                       :top 0}
               :className "RoundedRectangle"}]
        :membrane.ui/style-stroke-and-fill
        [:div {:style {:border-color (:color *ctx*)
                       "-moz-border-radius" border-radius-px
                       "-webkit-border-radius" border-radius-px
                       "border-radius" border-radius-px
                       :border-width (->px (:stroke-width *ctx*))
                       :border-style "solid"
                       :background-color (:color *ctx*)
                       :width (str width "px")
                       :height (str height "px")
                       :position "absolute"
                       :left 0
                       :top 0}
               :className "RoundedRectangle"}]))))

(extend-type membrane.ui.Path
  IRender
  (render [this]
    ;; <polyline points="60, 110 65, 120 70, 115 75, 130 80, 125 85, 140 90, 135 95, 150 100, 145"/>
    (let [points (clojure.string/join "," (apply concat (:points this)))]
      [:svg
       {:style {:position "absolute"
                :left 0
                :top 0
                :overflow "visible"}}
       [:polyline
        (merge
         {:points points}
         (case (:style *ctx*)
           :membrane.ui/style-fill
           {:fill (:color *ctx*)
            :stroke "none"}
           :membrane.ui/style-stroke
           {:stroke (:color *ctx*)
            :stroke-width (:stroke-width *ctx*)
            :fill "none"}
           :membrane.ui/style-stroke-and-fill
           {:stroke (:color *ctx*)
            :stroke-width (:stroke-width *ctx*)
            :fill (:color *ctx*)}))]])))

(defn color-text [[r g b a]]
  (if a
    (str "rgba(" (* r 255.0) "," (* g 255.0) "," (* b 255.0) "," a ")")
    (str "rgb(" (* r 255.0) "," (* g 255.0) "," (* b 255.0) ")")))

(extend-type membrane.ui.WithColor
  IRender
  (render [this]
    (let [color-style (color-text (:color this))]
      (binding [*ctx* (assoc *ctx* :color color-style)]
        [:div
         {:style {:color color-style
                  :position "absolute"
                  :left 0
                  :top 0}
          :className "WithColor"}
         (doall
          (for [drawable (:drawables this)]
            (render drawable)))]))))

(extend-type membrane.ui.WithStyle
  IRender
  (render [this]
    (let [style (:style this)]
      (binding [*ctx* (assoc *ctx* :style  style)]
        (doall
         (for [drawable (:drawables this)]
           (render drawable)))))))

(extend-type membrane.ui.WithStrokeWidth
  IRender
  (render [this]
    (let [stroke-width (:stroke-width this)]
      (binding [*ctx* (assoc *ctx* :stroke-width stroke-width)]
        (doall
         (for [drawable (:drawables this)]
           (render drawable)))))))

(extend-type membrane.ui.Scale
  IRender
  (render [this]
    (let [[sx sy] (:scalars this)]
      [:div {:style {:transform (str "scaleX(" sx ") scaleY(" sy ")" )
                     :position "absolute"
                     :left 0
                     :top 0}
             }
       (doall
        (for [drawable (:drawables this)]
          (render drawable)))])))

(extend-type membrane.ui.Arc
  IRender
  (render [this]
    (render-rect)))

(extend-type membrane.ui.ScissorView
  IRender
  (render [this]
    (let [[w h] (:bounds this)
          [ox oy] (:offset this)]
      [:div {:style {:width (str w "px")
                     :height (str h "px")
                     :position "relative"
                     :overflow "hidden"}}
       [:div {:style {:position "absolute"
                      :left (str ox "px")
                      :top (str oy "px")}}
        (render (:drawable this))]])))

(extend-type membrane.ui.ScrollView
  IRender
  (render [this]
    (let [[w h] (:bounds this)
          [ox oy] (:offset this)]
      [:div {:style {:width (str w "px")
                     :height (str h "px")
                     :position "relative"
                     :overflow "hidden"
                     }}
       [:div {:style {:position "absolute"
                      :left (str ox "px")
                      :top (str oy "px")}}
        (render (:drawable this))]])))

(extend-type membrane.ui.OnScroll
  IRender
  (render [this]
    (doall
     (for [drawable (:drawables this)]
       (render drawable)))))

(defrecord AppRoot [ui make-ui last-touch touch-check? elem renderer])

(defn app-root [elem make-ui]
  (let [root (AppRoot.
              (atom nil)
              make-ui
              (atom nil)
              (atom false)
              elem
              (vdom/renderer elem))]
    (def ^export vroot root)

    (doseq [[event handler] @event-handlers]
      (let [elem (if (.startsWith event "key")
                   js/document.body
                   elem)]
        (.addEventListener elem event (partial handler root) true)))
    root))

(defn update-scale [canvas]
  (let [content-scale (.-devicePixelRatio js/window)]
    (when (and content-scale (not= 1 content-scale))
      (let [cwidth (.-clientWidth canvas)
            cheight (.-clientHeight canvas)
            canvas-style (.-style canvas)
            ctx (.getContext canvas "2d")]
        (set! (.-width canvas-style) (str cwidth "px") )
        (set! (.-height canvas-style) (str cheight "px"))

        (set! (.-width canvas) (* cwidth content-scale))
        (set! (.-height canvas) (* cheight content-scale))
        (set! (.-font ctx)
              (str (when-let [weight (:weight ui/default-font)]
                     (str weight " "))
                   (:size ui/default-font) "px"
                   " "
                   (:name ui/default-font)))))))



(let [content-scale (.-devicePixelRatio js/window)
      app-container
      [:div {:id "app-container"
             :style {:display "block"
                     :position "relative"
                     :font
                     (str (when-let [weight (:weight ui/default-font)]
                            (str weight " "))
                          (:size ui/default-font) "px"
                          " "
                          (:name ui/default-font))}}]]
  (defn rerender [app-root]
    (binding [*ctx* {:color "black"
                     :stroke-width 1
                     :style :membrane.ui/style-fill}]
      (let [ui (:ui app-root)
            renderer (:renderer app-root)]
        (let [old-ui @ui
              current-ui (reset! ui ((:make-ui app-root)))]
          (when (not= current-ui old-ui)
            (let [current-vdom (render current-ui)
                  [w h] (bounds current-ui)
                  app-vdom (->  app-container
                                (assoc-in [1 :style :width] (->px w))
                                (assoc-in [1 :style :height] (->px h))
                                (conj current-vdom))]
              (renderer app-vdom))))))))



(defn run [make-ui options]
  (on-freetype-loaded
   (fn [_]
     (-> (.-fonts js/document)
         (.load (str (when-let [weight (:weight ui/default-font)]
                       (str weight " "))
                     (:size ui/default-font) "px"
                     " "
                     (:name ui/default-font)
                     ))
         (.then (fn []
                  (let [root (app-root (:root options) make-ui)
                        root-style (.-style (:elem root))]
                    (set! (.-position root-style) "relative")
                    
                    (rerender root))))))))

(defn get-client-pos [e]
  (if-let [touches (.-targetTouches e)]
    (let [touch (-> touches
                    (aget 0))]
      [(.-clientX touch) (.-clientY touch)])
    [(.-clientX e) (.-clientY e)])
  )


(defn -on-mouse-down [canvas e]
  (let [touch-check? (:touch-check? canvas)]
    (when (not @touch-check?)
      (do
        (when (.-targetTouches e)
          (.removeEventListener (:elem canvas) "mousedown" -on-mouse-down))
        (reset! touch-check? true))))
  
  (let [rect (.getBoundingClientRect (:elem canvas))
        [client-x client-y] (get-client-pos e)
        pos [(- client-x (.-left rect))
             (- client-y (.-top rect))]
        button (.-button e)
        mouse-down? true]
    (try
      (membrane.ui/mouse-event @(:ui canvas) pos button mouse-down? nil)
      (catch js/Object e

        (println e)
        (throw e))))

  (rerender canvas)
  (.stopPropagation e)
  (.preventDefault e)

  (let [current-time (.getTime (js/Date.))
        last-touch (:last-touch canvas)]
    (when-let [last-touch-time @last-touch]
      (when (< (- current-time last-touch-time)
               300)
        (.stopPropagation e)
        (.preventDefault e)))
    (reset! last-touch current-time))

  false)

(swap! event-handlers
       assoc
       "touchstart" -on-mouse-down
       "mousedown" -on-mouse-down)

(defn -on-mouse-up [canvas e]
  (let [rect (.getBoundingClientRect (:elem canvas))
        [client-x client-y] (get-client-pos e)
        pos [(- client-x (.-left rect))
             (- client-y (.-top rect))]
        button (.-button e)
        mouse-down? false]
    (try
      (membrane.ui/mouse-event @(:ui canvas) pos button mouse-down? nil)
      (catch js/Object e
        (println e))))

  (rerender canvas)
  (.stopPropagation e)
  (.preventDefault e)

  false)

(swap! event-handlers
       assoc
       "mouseup" -on-mouse-up)


#_(defn -scroll-callback [window window-handle offset-x offset-y]
  (let [ui @(:ui window)
        [x y] @(:mouse-position window)
        results (find-all-under ui [x y] [0 0] #(satisfies? IScroll %))
        ;; [[local-x local-y] result] (find-first-under ui [x y] [0 0] #(satisfies? IScroll %))
        ]
    (doseq [[[local-x local-y] result] results
            :let [ret (try
                        (-scroll result [offset-x offset-y])
                        (catch Exception e
                          (println e)))]
            :while (not (false? ret))]))
  (rerender))

(defn -on-mouse-move [canvas e]
  (let [rect (.getBoundingClientRect (:elem canvas))
        [client-x client-y] (get-client-pos e)
        pos [(- client-x (.-left rect))
             (- client-y (.-top rect))]]
        (try
          (doall (membrane.ui/mouse-move @(:ui canvas) pos))
          (doall (membrane.ui/mouse-move-global @(:ui canvas) pos))

          (catch js/Object e
            (println e))))

  (rerender canvas)

  (.stopPropagation e)
  (.preventDefault e)
  
  false)

(swap! event-handlers
       assoc
       "mousemove" -on-mouse-move
       "touchmove" -on-mouse-move)

(def keymap
  {
   ;; probably should figure out how to distinguish left and right shift like on mac
   "Shift" :shift
   "Enter" :enter
   "Backspace" :backspace

   "Up" :up
   "Down" :down
   "Left" :left
   "Right" :right
   "ArrowUp" :up
   "ArrowDown" :down
   "ArrowLeft" :left
   "ArrowRight" :right

   })

(defn -on-key-down [canvas e]
  (let [raw-key (.-key e)
        key (if (> (.-length raw-key) 1)
                (get keymap raw-key :undefined)
                raw-key)]
    (membrane.ui/key-event @(:ui canvas) key nil nil nil)
    (membrane.ui/key-press @(:ui canvas) key))
    (.stopPropagation e)

    (.preventDefault e)


  (rerender canvas))

(swap! event-handlers
       assoc
       "keydown" -on-key-down)

(defn -on-key-up [canvas e]
  ;; (println (.-key e))
    (.stopPropagation e)
    (.preventDefault e)


  (rerender canvas))

(swap! event-handlers
       assoc
       "keyup" -on-key-up)


#_(defonce start-todo-app
  (run
    (membrane.component/make-app #'todo/todo-app todo/todo-state)
    {:root (js/document.getElementById "app")}))

(def state (atom {}))

(declare vroot)
(defn ^:export change []
  (swap! state assoc :s "")
  (rerender vroot))

(defui test-ui [& {:keys [num s]
                   :or {num 1
                        s "b"}}]
  (ui/label s)
  (apply
   ui/vertical-layout
   (for [[color style] [[[0 0 1] :membrane.ui/style-fill]
                        [[1 0 0] :membrane.ui/style-stroke]
                        [[0 1 0 .5] :membrane.ui/style-stroke-and-fill]]]
     (ui/with-color color
      (ui/with-style style
        (ui/path
         [0 0]
         [20 30]
         [0 50]
         [0 0])))))
  ;; (membrane.basic-components/textarea :text s :focus? true)
  
  #_(ui/vertical-layout
     (ui/label "hello")
     (ui/label (str "count is " num))
     (button "hello world"
             (fn []
               (prn "handler")
               [[:update $num inc]]))
     (checkbox (even? num))
     (membrane.basic-components/textarea :text s)
     (checkbox false)
   
     )
  )

