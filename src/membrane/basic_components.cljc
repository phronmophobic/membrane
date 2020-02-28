(ns membrane.basic-components
  #?(:cljs
     (:require-macros [membrane.basic-components :refer [maybe-key-event]]))
  (:require [membrane.component :refer [defui run-ui path->spec defeffect] :as component]
            [com.rpl.specter :as spec
             :refer [ATOM ALL FIRST LAST MAP-VALS META]]
            [membrane.ui :as ui
             :refer [vertical-layout
                     translate
                     horizontal-layout
                     button
                     label
                     with-color
                     image
                     on-click
                     on-mouse-up
                     bounds
                     spacer
                     filled-rectangle
                     rectangle
                     defcomponent
                     IBounds
                     IKeyPress
                     IDraw
                     origin
                     origin-x
                     origin-y
                     draw
                     on-keypress
                     bordered
                     children
                     on
                     IHandleEvent
                     index-for-position]]))


(defcomponent NoEvents [drawable]
    ui/IBounds
    (-bounds [this]
        (bounds drawable))

  ui/IOrigin
  (-origin [_]
      [0 0])

  ui/IChildren
    (-children [this]
        [drawable])

    IDraw
    (draw [this]
        (draw drawable))

    ui/IBubble
    (-bubble [this events]
        nil)

    IHandleEvent
    (-can-handle? [this other-event-type]
        false)

    (-handle-event [this event-type event-args]
        nil)

    ui/IClipboardCopy
    (-clipboard-copy [_] nil)
    ui/IClipboardCut
    (-clipboard-cut [_] nil)
    ui/IClipboardPaste
    (-clipboard-paste [_ s] nil)
    ui/IKeyPress
    (-key-press [this key] nil)
    ui/IKeyType
    (-key-type [this key] nil)
    ui/IMouseDown
    (-mouse-down [this pos] nil)
    ui/IMouseDrag
    (-mouse-drag [this pos] nil)
    ui/IMouseMove
    (-mouse-move [this pos] nil)
    ui/IMouseMoveGlobal
    (-mouse-move-global [this pos] nil)
    ui/IMouseUp
    (-mouse-up [this pos] nil)
    ui/IMouseWheel
    (-mouse-wheel [this pos] nil)
    ui/IScroll
    (-scroll [this pos] nil))

#_(defn no-events [body]
  (NoEvents. body))

(defn no-events [body]
  (let [do-nothing (constantly nil)]
    (on :mouse-down do-nothing
        :keypress do-nothing
        :mouse-up do-nothing
        :mouse-move do-nothing
        body)))

(defcomponent NoKeyEvent [drawable]
    ui/IOrigin
    (-origin [_]
        [0 0])

    ui/IBounds
    (-bounds [this]
        (ui/bounds drawable))

    ui/IChildren
    (-children [this]
        [drawable])

    IDraw
    (draw [this]
        (draw drawable))
    ui/IHasKeyEvent
    (has-key-event [this]
        false))

(defmacro maybe-key-event [test body]
  `(if ~test
     ~body
     (NoKeyEvent. ~body)))




(defui on-hover [ & {:keys [hover? body]}]
  (if hover?
    (ui/on-mouse-move-global
     (fn [[x y]]
       (let [[w h] (bounds body)]
         (when (or (neg? x)
                   (> x w)
                   (neg? y)
                   (> y h))
           [[:set $hover? false]])))
     body)
    (ui/on-mouse-move
     (fn [[x y]]
       [[:set $hover? true]])
     body)))

(defui hover-button [& {:keys [hover? text on-click]}]
  (on-hover :hover? hover?
            :body
            (let [btn (button text on-click)]
              [(when hover?
                 (let [[bwidth bheight] (bounds btn)]
                   (filled-rectangle [0.9 0.9 0.9] bwidth bheight)))
               (button text on-click)])))





(defeffect ::previous-line [$cursor $select-cursor text]
  (run! #(apply dispatch! %)
        [[:set $select-cursor nil]
         [:update $cursor
          (fn [cursor]
            (let [prev-newline (.lastIndexOf ^String text "\n" (int (dec cursor)))]
              (if (not= -1 prev-newline)
                prev-newline
                0)))]]))

(defeffect ::next-line [$cursor $select-cursor text]
  (run! #(apply dispatch! %)
        [[:set $select-cursor nil]
         [:update $cursor
          (fn [cursor]
            (let [next-newline (.indexOf ^String text "\n" (int cursor))]
              (if (not= -1 next-newline)
                (inc next-newline)
                (count text))))]]))

(defeffect ::forward-char [$cursor $select-cursor text]
  (run! #(apply dispatch! %)
        [[:set $select-cursor nil]
         [:update $cursor
          (fn [cursor]
            (min (count text)
                 (inc cursor)))]]))


(defeffect ::backward-char [$cursor $select-cursor text]
  (run! #(apply dispatch! %)
        [[:set $select-cursor nil]
         [:update $cursor
          (fn [cursor]
            (max 0
                 (dec (min (count text) cursor))))]]))

(defeffect ::insert-newline [$cursor $select-cursor $text]
  (dispatch! ::insert-text $cursor $select-cursor $text "\n"))

(defeffect ::insert-text [$cursor $select-cursor $text s]

  (run! #(apply dispatch! %)
        [
         [:update [(spec/collect-one (path->spec $cursor))
                   (spec/collect-one (path->spec $select-cursor))
                   $text]
          (fn [cursor select-cursor text]
            (let [
                  start-clip-index (min
                                    (count text)
                                    (if select-cursor
                                      (min cursor select-cursor)
                                      cursor))
                  end-clip-index (min
                                  (count text)
                                  (if select-cursor
                                    (max cursor select-cursor)
                                    cursor))]
             (if text
               (str (subs text 0 start-clip-index) s (subs text end-clip-index))
               s)))]
         [:update [(spec/collect-one (path->spec $select-cursor))
                   $cursor]
          (fn [select-cursor cursor]
            (let [cursor (or cursor 0)
                  index (if select-cursor
                          (min select-cursor cursor)
                          cursor)]
             (+ (count s) index)))]
         [:set $select-cursor nil]
         ]))


(defeffect ::move-cursor-to-pos [$cursor text font pos]
  (run! #(apply dispatch! %)
        [[:update $cursor (fn [cursor]
                            (let [[mx my] pos
                                  new-cursor (index-for-position font
                                                                 text mx my)]
                              new-cursor))]]))


(defeffect ::start-drag [$mpos $down-pos pos]
  (run! #(apply dispatch! %)
        [[:set $mpos pos]
         [:set $down-pos pos]]))


(defeffect ::drag [$mpos pos]
  (run! #(apply dispatch! %)
        [[:set $mpos pos]]))

(defeffect ::finish-drag [$select-cursor $cursor $down-pos pos text font]
  (let [[mx my] pos
        end-index (index-for-position font
                                      text mx my)]
    (run! #(apply dispatch! %)
          [
           [:update [(spec/collect-one (path->spec $down-pos))
                     $select-cursor]
            (fn [down-pos select-cursor]
              (when-let [[dx dy] down-pos]
                (let [idx (index-for-position font
                                              text dx dy)]
                  (when (not= idx end-index)
                    (if (> idx end-index)
                      (min (count text) (inc idx))
                      idx))))
              
              )]
           [:set $down-pos nil]
           [:update [(spec/collect-one (path->spec $select-cursor))
                     $cursor]
            (fn [select-cursor cursor]
              (if (and select-cursor (> end-index select-cursor))
                (min (count text) (inc end-index))
                end-index))]])))


(def double-click-threshold 500)
#?
(:clj
 (defeffect ::text-double-click [$last-click $select-cursor $cursor pos text font]
   (let [now (java.util.Date.)
         [mx my] pos]
     (run! #(apply dispatch! %)
           [
            [:update [(spec/collect-one (path->spec $last-click))
                      $select-cursor]
             (fn [[last-click [dx dy]] select-cursor]
               (if last-click
                 (let [diff (- (.getTime ^java.util.Date now) (.getTime ^java.util.Date last-click))]
                   (if (and (< diff double-click-threshold)
                            (< (+ (Math/pow (- mx dx) 2)
                                  (Math/pow (- my dy) 2))
                               100))
                     (let [index (index-for-position font
                                                     text mx my)
                           matcher (doto (re-matcher  #"\s" text)
                                     (.region index (count text)))]
                       (if (.find matcher)
                         (.start matcher)
                         (count text)))
                     select-cursor))
                 select-cursor))]
            [:update [(spec/collect-one (path->spec $last-click))
                      $cursor]
             (fn [[last-click [dx dy]] cursor]
               (if last-click
                 (let [diff (- (.getTime now) (.getTime ^java.util.Date last-click))]
                   (if (and (< diff double-click-threshold)
                            (< (+ (Math/pow (- mx dx) 2)
                                  (Math/pow (- my dy) 2))
                               100))
                     (let [index (index-for-position font
                                                     text mx my)
                           text-backwards (clojure.string/reverse text)
                           matcher (doto (re-matcher  #"\s" text-backwards)
                                     (.region (- (count text) index) (count text)))
                           ]
                       (if (.find matcher)
                         (- (count text) (.start matcher))
                         0))
                     cursor))
                 cursor))]
           
            [:set $last-click [now pos]]]))
   ))

(defeffect ::delete-backward [$cursor $select-cursor $text]
  (run!
   #(apply dispatch! %)
   [
    [:update [(spec/collect-one (path->spec [$cursor]))
              (spec/collect-one (path->spec [$select-cursor]))
              $text]
     (fn [cursor select-cursor text]
       (let [cursor (min (count text) cursor)
             [clip-start clip-end] (if select-cursor
                                     (let [select-cursor (min (count text) select-cursor)]
                                       (if (< cursor select-cursor)
                                         [cursor select-cursor]
                                         [select-cursor cursor]))
                                     [(max 0 (dec cursor)) cursor])]
         (str (subs text 0 clip-start)
              (subs text clip-end))))]
    [:update [(spec/collect-one (path->spec [$select-cursor]))
              $cursor]
     (fn [select-cursor cursor]
       (max 0 (if select-cursor
                (min select-cursor cursor)
                (dec cursor))))]
    [:set $select-cursor nil]
    
    ]))


(defui textarea-view [& {:keys [cursor
                                focus?
                                text
                                down-pos
                                mpos
                                select-cursor
                                last-click
                                font
                                border?]
                         :or {cursor 0
                              text ""
                              border? true}}]
  (maybe-key-event
   focus?
   (on
    :keypress
    (fn [s]
      (when focus?
        (case s

          :up
          [[::previous-line $cursor $select-cursor  text]]

          :enter
          [[::insert-newline $cursor $select-cursor $text]]

          :down
          [[::next-line $cursor $select-cursor text]]

          :left
          [[::backward-char $cursor $select-cursor text]]

          :right
          [[::forward-char $cursor $select-cursor text]]
          
          :backspace
          [[::delete-backward $cursor $select-cursor $text]]
          
          ;; else
          (when (string? s)
            [[::insert-text  $cursor $select-cursor $text s]]))))
    :mouse-up
    (fn [[mx my :as pos]]
      [[::finish-drag $select-cursor $cursor $down-pos pos text font]
       [::text-double-click $last-click $select-cursor $cursor pos text font]])
    :mouse-down
    (fn [[mx my :as pos]]
      [[::request-focus]
       [::move-cursor-to-pos $cursor text font pos]
       [::start-drag $mpos $down-pos pos]
       [:set $select-cursor nil]])
    :mouse-move
    (fn [[mx my :as pos]]
      (when down-pos
        [[::drag $mpos pos]]))
    :clipboard-copy
    (fn []
      (when (and focus? select-cursor)
        [[:clipboard-copy (subs text
                                (min cursor select-cursor)
                                (max cursor select-cursor))]]))
    :clipboard-cut
    (fn []
      (when (and focus? select-cursor)
        (let [new-text (when text
                         (str (subs text 0 (min cursor select-cursor))
                              (subs text (max cursor select-cursor))))]
          [[:set $cursor (min cursor select-cursor)]
           [:set $select-cursor nil]
           [:set $text new-text]
           [:clipboard-cut (subs text
                                 (min cursor select-cursor)
                                 (max cursor select-cursor))]
           [::new-text new-text]])
        )
      )
    :clipboard-paste
    (fn [s]
      (when focus?
        [[::insert-text $cursor $select-cursor $text s]]))
    (let [body [(spacer 100 10)
                (when focus?
                  (ui/with-color
                    [0.5725490196078431
                     0.5725490196078431
                     0.5725490196078431
                     0.4]
                    (ui/text-cursor text cursor font)))
                (when select-cursor
                  (ui/with-color
                    [0.6980392156862745
                     0.8431372549019608
                     1]
                    (ui/text-selection text
                                       [(min select-cursor cursor)
                                        (max select-cursor cursor)]
                                       font)))

                (when-let [[dx dy] down-pos]
                  (when-let [[mx my] mpos]
                    (translate (min mx dx)
                               (min my dy)
                               (filled-rectangle
                                [0.9 0.9 0.9]
                                (Math/abs
                                 (double (- mx dx)))
                                (Math/abs
                                 (double (- my dy)))))))
                (label text font)]]
      (if border?
        (let [gray  0.65
              padding-x 5
              padding-y 2
              [w h] (ui/bounds body)]
          [(with-color [gray gray gray]
             (rectangle (+ w (* 2 padding-x))
                        (+ (max h (+ padding-y (or (:size font)
                                                   (:size ui/default-font)))) (* 2 padding-y))))
           (translate padding-x
                      padding-y
                      body)])
        body)))))

(defui textarea-focusable [& {:keys [text
                                     font
                                     ^:membrane.component/contextual focus
                                     textarea-state]}]
  (on
   ::request-focus
   (fn []
     [[:set [$focus] $text]])
   (textarea-view :text text
                  :cursor (get textarea-state :cursor 0)
                  :focus? (= focus $text)
                  :font font
                  :down-pos (:down-pos textarea-state)
                  :mpos (:mpos textarea-state)
                  :border? true
                  :select-cursor (:select-cursor textarea-state)))
  )

(defui textarea-focusable-light [& {:keys [text
                                           font
                                     ^:membrane.component/contextual focus
                                     textarea-state]}]
  (on
   ::request-focus
   (fn []
     [[:set [$focus] $text]])
   (let [focus? (= focus $text)]
     (let [textarea
           (textarea-view :text text
                          :cursor (get textarea-state :cursor 0)
                          :focus? focus?
                          :font font
                          :down-pos (:down-pos textarea-state)
                          :mpos (:mpos textarea-state)
                          :select-cursor (:select-cursor textarea-state)
                          :border? false)]
       (ui/fill-bordered [0.97 0.97 0.97] [0 0]
                         textarea)))))



(defui scrollview [& {:keys [offset mdownx? mdowny? scroll-bounds body]
                      :or {offset [0 0]}}]
  (let [offset-x (nth offset 0)
        offset-y (nth offset 1)
        [width height] scroll-bounds
        scroll-button-width 7
        scroll-button-height 10
        [total-width total-height] (bounds body)

        scroll-elem (ui/scrollview
                     scroll-bounds [(- offset-x) (- offset-y)]
                     body)

        max-offset-x (max 0
                          (- total-width width))
        max-offset-y (max 0
                          (- total-height height))
        clampx (fn [old-offset]
                 (max 0
                      (min max-offset-x
                           old-offset)))
        clampy (fn [old-offset]
                 (max 0
                      (min max-offset-y
                           old-offset)))

        div0 (fn [a b]
             (if (zero? b)
               b
               (/ a b)))
        ]
    (ui/on-scroll
     (fn [[ox oy]]
       [[:update $offset-x (fn [old-offset]
                             (clampx (+ ox offset-x)))]
        [:update $offset-y (fn [old-offset]
                             (clampy (+ oy offset-y)))]])
     (ui/on-mouse-move
      (fn [[mx my :as mpos]]
        (if mdowny?
          [[:set $offset-y (clampy (* (div0 (float my) height)
                                      max-offset-y))]]
          (if mdownx?
            [[:set $offset-x (clampx (* (div0 (float mx) width)
                                        max-offset-x))]]
            (ui/mouse-move scroll-elem mpos))))
      (ui/on-mouse-event
       (fn [[mx my :as mpos] button mouse-down? mods]
         (if mouse-down?
           (let [new-mdownx? (> my (- height scroll-button-height))
                 new-mdowny? (> mx (- width scroll-button-width))]
             (into
              [[:set $mdownx? new-mdownx?]
               [:set $mdowny? new-mdowny?]]
              (if new-mdowny?
                [[:set $offset-y (clampy (* (div0 (float my) height)
                                            max-offset-y))]]
                (if new-mdownx?
                  [[:set $offset-x (clampx (* (div0 (float mx) width)
                                              max-offset-x))]]
                  (ui/mouse-event scroll-elem mpos button mouse-down? mods)))))
           ;; mouse up
           (into
            [[:set $mdownx? false]
             [:set $mdowny? false]]
            (ui/mouse-event scroll-elem mpos button mouse-down? mods)))
         )
       [
        scroll-elem
        (when (> total-height height)
          (translate width 0
                     [(filled-rectangle [0.941 0.941 0.941]
                                        scroll-button-width height)
                      (let [top (/ offset-y total-height)
                            bottom (/ (+ offset-y height)
                                      total-height)]

                        (translate 0 (* height top)
                                   (with-color
                                    [0.73 0.73 0.73]
                                    (ui/rounded-rectangle scroll-button-width (* height (- bottom top)) (/ scroll-button-width 2)))
                                   ))

                      (with-color [0.89 0.89 0.89]
                                 (rectangle scroll-button-width height))]))
        (when (> total-width width)
          (translate 0 height
                     [(filled-rectangle [0.941 0.941 0.941]
                                        width scroll-button-width)
                      (let [left (/ offset-x total-width)
                            right (/ (+ offset-x width)
                                     total-width)]
                        (translate (* width left) 0
                                   (with-color
                                    [0.73 0.73 0.73]
                                    (ui/rounded-rectangle (* width (- right left)) scroll-button-width  (/ scroll-button-width 2)))
                                   )
                        )
                      (with-color [0.89 0.89 0.89]
                                 (rectangle width scroll-button-width ))]))

        ])))))

(defui test-scrollview [& {:keys [state]}]
  (scrollview :scroll-bounds [200 200]
              :body
              (apply
               vertical-layout
               (for [i (range 100)]
                 (label (str "The quick brown fox"
                             " jumped over the lazy dog"
                             ))))))


(defui checkbox [& {:keys [checked?]}]
  (on
   :mouse-down
   (fn [_]
     [[:update $checked? not]])
   (ui/checkbox checked?)))



(defui dropdown [& {:keys [options selected]}]
  (let [
        labels (for [option options]
                 (ui/label option))
        max-width (reduce max 0 (map ui/width labels))
        padding-y 8
        padding-x 12

        rows
        (apply
         vertical-layout
         (for [option options]
           (let [hover? (get extra [:hover? $option])


                 selected? (= selected option)

                 label (if selected?
                         (ui/with-color [1 1 1]
                                       (ui/label option))
                         (ui/label option))

                 [_ h] (bounds label)
                 row-height (+ h 4)
                 row-width (+ max-width (* 2 padding-x))]
             (println selected?)
             (on-hover
              :hover? hover?
              :body
              (on
               :mouse-down
               (fn [_]
                 [[::select $selected option]])

               [(spacer row-width row-height)
                (cond

                  selected?
                  (ui/filled-rectangle [0 0.48 1]
                                       row-width row-height)

                  hover?
                  (ui/filled-rectangle [0.976 0.976 0.976]
                                       row-width row-height))
                (translate padding-x 2
                           label)])))))
        [rows-width rows-height] (bounds rows)
        ]
    [(ui/with-style
      ::ui/style-stroke
      (ui/with-color [0.831
                     0.831
                     0.831]
       (ui/rounded-rectangle rows-width
                             (+ rows-height (* 2 padding-y))
                             4)))
     (translate 0 (- padding-y 2)
                rows)])
  )

(defeffect ::select [$selected option]
  (dispatch! [:set $selected option])
  )

(comment
  (run-ui #'dropdown {:options ["This" "That " "The Other"]}))