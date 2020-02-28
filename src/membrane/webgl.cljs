(ns membrane.webgl
  (:require-macros [membrane.webgl-macros
                    :refer [push-state
                            add-image!]])
  (:require [membrane.ui :as ui
             :refer [IBounds
                     IDraw
                     draw
                     vertical-layout
                     horizontal-layout
                     rectangle
                     button
                     bordered
                     filled-rectangle
                     label
                     image]]
            [membrane.audio :as audio]
            [membrane.component :refer [defui run-ui]]
            [com.rpl.specter :as spec
             :refer [ATOM ALL FIRST LAST MAP-VALS META]]))


(def canvas (.getElementById js/document "canvas"))
(def ctx (.getContext canvas "2d"))

(def freetype-font)
(js/opentype.load
 "https://fonts.gstatic.com/s/ubuntu/v10/4iCs6KVjbNBYlgo6eA.ttf"
 (fn [err font]
   (if err
     (do (println "Error: " err)
         (js/console.log err))
     (do
       (set! freetype-font font)
       (reset! membrane.component/component-cache {})))))

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

(defn text-bounds [font text]
  (let [lines (clojure.string/split text #"\n" -1)
        bounds (map #(line-bounds font %) lines)
        maxx (reduce max 0 (map first bounds))
        maxy (* (dec (font-line-height font))
                (count lines))]
    [maxx maxy]))

(set! membrane.ui/text-bounds text-bounds)

(set! (.-font ctx)
      (str (when-let [weight (:weight ui/default-font)]
             (str weight " "))
           (:size ui/default-font) "px"
           " "
           (:name ui/default-font)
           ))

(defn draw-rect []
  (set! (.-fillStyle ctx)  "green")
  (.fillRect ctx 10, 10, 150, 100)
  )



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

(extend-type membrane.ui/Label
  IBounds
  (-bounds [this]
    (let [font (:font this)]
     (text-bounds font
                  (:text this))))
  IDraw
  (draw [this]
    (let [lines (clojure.string/split (:text this) #"\n" -1)
          font (:font this)
          line-height (font-line-height font)]
     (push-state ctx
                 (when font
                   (set! (.-font ctx)
                         (str (when (:weight font)
                                (str (:weight font) " "))
                              (or (:size font)
                                  (:size ui/default-font))
                              "px "
                              (or (:name font)
                                  (:name ui/default-font)))))
                 (doseq [line lines]
                   (.translate ctx 0 (dec line-height))
                   (.fillText ctx line 0 0))))
    ))

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
  IDraw
  (draw [this]
    (when-let [image-info (get @images (:image-path this))]
      (let [[width height] (:size this)]
        (push-state ctx
                    (when-let [opacity (:opacity this)]
                      (set! (.-globalAlpha ctx) opacity))
                    (.drawImage ctx
                                (:image-obj image-info)
                                0 0
                                width height))))))
(extend-type membrane.ui.Translate
  IDraw
  (draw [this]
    (push-state ctx
     (.translate ctx (:x this) (:y this))
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
           idx 0]
      (when (pos? selection-length)
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
                  (draw (ui/translate x (+ y (- line-height
                                           selection-height))
                                 (filled-rectangle color
                                                   selection-width selection-height))))
                (recur new-x new-y 0 (dec selection-length) (inc idx)))
              (recur new-x new-y (dec selection-start) selection-length (inc idx))))))
      ))
  )

(extend-type membrane.ui.TextSelection
  IBounds
  (-bounds [this]
    (text-bounds (:font this) (:text this)))

  IDraw
  (draw [this]
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

  IDraw
  (draw [this]
    (let [cursor (:cursor this)]
      (render-selection (:font this) (str (:text this) "8") cursor (inc cursor)
                        [0.9 0.9 0.9]))
    ))


(extend-type membrane.ui.Path
  IDraw
  (draw [this]
    (push-state ctx
     (.beginPath ctx)
     (let [[x y] (first (:points this))]
       (.moveTo ctx x y))
     (doseq [[x y] (rest (:points this))]
       (.lineTo ctx x y))
     (.stroke ctx)

     
)))

(extend-type membrane.ui.Polygon
  IDraw
  (draw [this]
    (let [color (case (count (:color this))
                  3 (str "rgb(" (clojure.string/join ","
                                                     (map #(int (* 255.0 %)) (:color this))) ")")
                  4 (str "rgba(" (clojure.string/join ","
                                                      (map #(int (* 255.0 %)) (take 3 (:color this))))
                         "," (nth (:color this) 3) ")")
                  )]
     (push-state ctx
                 (set! (.-fillStyle ctx)  color)
                 (.beginPath ctx)
                 (let [[x y] (first (:points this))]
                   (.moveTo ctx x y))
                 (doseq [[x y] (rest (:points this))]
                   (.lineTo ctx x y))
                 (.fill ctx)

                
                 ))
    ))



(extend-type membrane.ui.WithColor
  IDraw
  (draw [this]
    (push-state ctx
                (set! (.-fillStyle ctx) (:color this) )
                (set! (.-strokeStyle ctx) (:color this) )
                (doseq [drawable (:drawables this)]
                  (draw drawable)))))

(extend-type membrane.ui.WithScale
  IDraw
  (draw [this]
    (push-state ctx
                (let [[sx sy] (:scalars this)]
                  (.scale ctx sx sy))
                (doseq [drawable (:drawables this)]
                  (draw drawable)))))

(extend-type membrane.ui.Arc
  IDraw
  (draw [this]
    (draw-rect)))

(extend-type membrane.ui.ScissorView
  IDraw
  (draw [this]
    
    ))

(extend-type membrane.ui.ScrollView
  IDraw
  (draw [this]
    (draw-rect)))

(extend-type membrane.ui.OnScroll
  IDraw
  (draw [this]
      (doseq [drawable (:drawables this)]
        (draw drawable))))

(defonce ui (atom nil))
(declare -make-ui)
(defn redraw []
  (when -make-ui
    (.clearRect ctx
                0 0
                (.-width canvas) (.-height canvas))
    (reset! ui (-make-ui))
    (draw @ui)))

(defn run [make-ui]
  (set! -make-ui make-ui)
  (redraw))
(set! membrane.ui/run run)


(defn get-client-pos [e]
  (if-let [touches (.-targetTouches e)]
    (let [touch (-> touches
                    (aget 0))]
      [(.-clientX touch) (.-clientY touch)])
    [(.-clientX e) (.-clientY e)])
  )


(let [touch-check? (atom false)
      last-touch (atom nil)]
  (defn -on-mouse-down [e]
    (when (not @touch-check?)
      (do
        (when (.-targetTouches e)
          (.removeEventListener canvas "mousedown" -on-mouse-down))
        (reset! touch-check? true)))
    

    (let [rect (.getBoundingClientRect canvas)
          [client-x client-y] (get-client-pos e)
          pos [(- client-x (.-left rect))
               (- client-y (.-top rect))]
          button (.-button e)
          mouse-down? true]
      (try
        (membrane.ui/mouse-event @ui pos button mouse-down? nil)
        (catch js/Object e
          (println e))))

    (redraw)
    ;; (.stopPropagation e)
    ;; (.preventDefault e)

    (let [current-time (.getTime (js/Date.))]
      (when-let [last-touch-time @last-touch]
        (when (< (- current-time last-touch-time)
                 300)
          (.stopPropagation e)
          (.preventDefault e)))
      (reset! last-touch current-time))

    nil))

(defonce mouseDownEventHandler
  (doto canvas
    (.addEventListener "touchstart"
                       -on-mouse-down)
    (.addEventListener "mousedown"
                       -on-mouse-down)))

(defn -on-mouse-up [e]
  (let [rect (.getBoundingClientRect canvas)
        [client-x client-y] (get-client-pos e)
        pos [(- client-x (.-left rect))
             (- client-y (.-top rect))]
        button (.-button e)
        mouse-down? false]
    (try
      (membrane.ui/mouse-event @ui pos button mouse-down? nil)
      (catch js/Object e
        (println e))))

  (redraw)
  ;; (.stopPropagation e)
  ;; (.preventDefault e)

  nil)

(defonce mouseUpEventHandler
  (doto canvas
    (.addEventListener "mouseup"
                       -on-mouse-up)))

(def enlarge-bottom-button (.getElementById js/document "enlarge-canvas-bottom"))

(defonce enlargeBottomEventHandler
  (doto enlarge-bottom-button
    (.addEventListener "mousedown"
                       (fn []
                         (doto canvas
                           (.setAttribute "height" (+ (int (.-height canvas)) 200)))))))

(def enlarge-right-button (.getElementById js/document "enlarge-canvas-right"))

(defonce enlargeRightEventHandler
  (doto enlarge-right-button
    (.addEventListener "mousedown"
                       (fn []
                         (doto canvas
                           (.setAttribute "width" (+ (int (.-width canvas)) 200)))))))


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
  (redraw))

(defn -on-mouse-move [e]
  (let [rect (.getBoundingClientRect canvas)
        [client-x client-y] (get-client-pos e)
        pos [(- client-x (.-left rect))
             (- client-y (.-top rect))]]
        (try
          (doall (membrane.ui/mouse-move @ui pos))

          (catch js/Object e
            (println e))))

  (redraw)

  ;; (.stopPropagation e)
  ;; (.preventDefault e)
  
  nil)
(defonce mouseMoveHandler
  (doto canvas
    (.addEventListener "mousemove"
                       -on-mouse-move)
    (.addEventListener "touchmove"
                       -on-mouse-move)))


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

(defn -on-key-down [e]
  (let [raw-key (.-key e)
        key (if (> (.-length raw-key) 1)
                (get keymap raw-key :undefined)
                raw-key)]
    (membrane.ui/key-event @ui key nil nil nil)
    (membrane.ui/key-press @ui key))

    (.stopPropagation e)
    (.preventDefault e)


  (redraw))

(defonce keyDownHandler
  (.addEventListener canvas "keydown"
                     -on-key-down))

(defn -on-key-up [e]
  ;; (println (.-key e))
    (.stopPropagation e)
    (.preventDefault e)


  (redraw))

(defonce keyUpHandler
  (.addEventListener canvas "keyup"
                     -on-key-up))













