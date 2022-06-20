(ns membrane.basic-components
  #?(:cljs
     (:require-macros [membrane.ui :refer [maybe-key-event]]
                      [membrane.component :refer [defui defeffect]]))
  (:require [membrane.component :refer [#?(:clj defui)
                                        #?(:clj defeffect)]
             :as component]
            [membrane.ui :as ui
             :refer [vertical-layout
                     translate
                     horizontal-layout
                     label
                     with-color
                     with-style
                     image
                     on-click
                     on-mouse-up
                     bounds
                     spacer
                     filled-rectangle
                     rectangle
                     IBounds
                     IKeyPress
                     origin
                     origin-x
                     origin-y
                     on-key-press
                     bordered
                     children
                     maybe-key-press
                     on
                     IHandleEvent
                     index-for-position]]))




(defui on-hover
  "Component for adding a hover? state."
  [{:keys [hover? body]}]
  (if hover?
    (ui/wrap-on
     :mouse-move-global
     (fn [handler [x y :as pos]]
       (let [[w h] (bounds body)
             child-intents (handler pos)]
         (if (or (neg? x)
                 (> x w)
                 (neg? y)
                 (> y h))
           (conj child-intents
                 [:set $hover? false])
           child-intents)))
     body)
    (ui/on-mouse-move
     (fn [[x y]]
       [[:set $hover? true]])
     body)))

(defui on-mouse-out [{:keys [mouse-out body hover?]}]
  (if hover?
    (ui/wrap-on
     :mouse-move-global
     (fn [handler [x y :as pos]]
       (let [[w h] (ui/bounds body)
             intents (handler pos)]
         (if (or (neg? x)
                 (> x w)
                 (neg? y)
                 (> y h))
           (into
            (conj intents
                  [:set $hover? false])
            (mouse-out))
           intents)))
       body)
    (ui/wrap-on
     :mouse-move
     (fn [handler [x y :as pos]]
       (into [[:set $hover? true]]
             (handler pos)))
     body)))

(defui button
  "Button component with hover state."
  [{:keys [hover? text on-click]}]
  (on-hover {:hover? hover?
             :body (ui/button text on-click hover?)}))



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
         [:update [(list 'collect-one $cursor)
                   (list 'collect-one $select-cursor)
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
         [:update [(list 'collect-one $select-cursor)
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
           [:update [(list 'collect-one $down-pos)
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
           [:update [(list 'collect-one $select-cursor)
                     $cursor]
            (fn [select-cursor cursor]
              (if (and select-cursor (> end-index select-cursor))
                (min (count text) (inc end-index))
                end-index))]])))


(def double-click-threshold 500)
(let [getTimeMillis #?(:clj (fn [] (.getTime ^java.util.Date (java.util.Date.)))
                       :cljs (fn [] (.getTime (js/Date.))))
      pow #?(:clj (fn [n x] (Math/pow n x))
             :cljs (fn [n x] (js/Math.pow n x)))
      find-white-space #?(:clj (fn [text start]
                                 (let [matcher (doto (re-matcher  #"\s" text)
                                                 (.region start (count text)))]
                                   (when (.find matcher)
                                     (.start matcher))))
                          :cljs (fn [text start]
                                  (let [regexp (js/RegExp. "\\s" "g")]
                                    (set! (.-lastIndex regexp) start)
                                    (let [result (.exec regexp text)]
                                      (when result
                                        (.-index result))))))]
  (defeffect ::text-double-click [$last-click $select-cursor $cursor pos text font]
    (let [now (getTimeMillis)
          [mx my] pos]
      (run! #(apply dispatch! %)
            [
             [:update [(list 'collect-one $last-click)
                       $select-cursor]
              (fn [[last-click [dx dy]] select-cursor]
                (if last-click
                  (let [diff (- now last-click)]
                    (if (and (< diff double-click-threshold)
                             (< (+ (pow (- mx dx) 2)
                                   (pow (- my dy) 2))
                                100))
                      (let [index (index-for-position font
                                                      text mx my)]
                        (if-let [start (find-white-space text index)]
                          start
                          (count text)))
                      select-cursor))
                  select-cursor))]
             [:update [(list 'collect-one $last-click)
                       $cursor]
              (fn [[last-click [dx dy]] cursor]
                (if last-click
                  (let [diff (- now last-click)]
                    (if (and (< diff double-click-threshold)
                             (< (+ (pow (- mx dx) 2)
                                   (pow (- my dy) 2))
                                100))
                      (let [index (index-for-position font
                                                      text mx my)
                            text-backwards (clojure.string/reverse text)]
                        (if-let [start (find-white-space text-backwards
                                                         (- (count text) index))]
                          (- (count text) start)
                          0)
                        )
                      cursor))
                  cursor))]

             [:set $last-click [now pos]]]))
    ))


(defeffect ::delete-backward [$cursor $select-cursor $text]
  (run!
   #(apply dispatch! %)
   [
    [:update [(list 'collect-one $cursor)
              (list 'collect-one $select-cursor)
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
    [:update [(list 'collect-one [$select-cursor])
              $cursor]
     (fn [select-cursor cursor]
       (max 0 (if select-cursor
                (min select-cursor cursor)
                (dec cursor))))]
    [:set $select-cursor nil]
    
    ]))


(defui textarea-view
  "Raw component for a basic textarea. textarea should be preferred."
  [{:keys [cursor
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
  (let [text (or text "")
        padding-x (if border? 5 0)
        padding-y (if border? 2 0)]
    (maybe-key-press
     focus?
     (ui/wrap-on
      :mouse-event
      (fn [handler [mx my] button mouse-down? mods]
        (handler [(- mx padding-x)
                  (- my padding-y)]
                 button mouse-down? mods))
      (on
       :key-press
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

                 [w h] (ui/bounds body)]
             [(with-color [gray gray gray]
                (with-style :membrane.ui/style-stroke
                  (rectangle (+ w (* 2 padding-x))
                             (+ (max h (+ padding-y (or (:size font)
                                                        (:size ui/default-font)))) (* 2 padding-y)))))
              (translate padding-x
                         padding-y
                         body)])
           body)))))))

(defui textarea
  "Textarea component."
  [{:keys [text
           border?
           font
           ^:membrane.component/contextual focus
           textarea-state]
    :or {border? true}}]
  (on
   ::request-focus
   (fn []
     [[:set $focus $text]])
   (textarea-view {:text text
                   :cursor (get textarea-state :cursor 0)
                   :focus? (= focus $text)
                   :font font
                   :down-pos (:down-pos textarea-state)
                   :mpos (:mpos textarea-state)
                   :border? (or border?
                                (nil? border?))
                   :select-cursor (:select-cursor textarea-state)}))
  )

(defui textarea-light
  "Alternate look for textarea component."
  [{:keys [text
           font
           ^:membrane.component/contextual focus
           textarea-state]}]
  (on
   ::request-focus
   (fn []
     [[:set [$focus] $text]])
   (let [focus? (= focus $text)]
     (let [textarea
           (textarea-view {:text text
                           :cursor (get textarea-state :cursor 0)
                           :focus? focus?
                           :font font
                           :down-pos (:down-pos textarea-state)
                           :mpos (:mpos textarea-state)
                           :select-cursor (:select-cursor textarea-state)
                           :border? false})]
       (ui/fill-bordered [0.97 0.97 0.97] [0 0]
                         textarea)))))



(defui scrollview
  "Basic scrollview.

  scroll-bounds should be a two element vector of [width height] of the scrollview
  body should be an element.
"
  [{:keys [offset mdownx? mdowny? scroll-bounds body]
    :or {offset [0 0]}}]
  (let [offset-x (nth offset 0)
        offset-y (nth offset 1)
        [width height] scroll-bounds
        scroll-button-size 7
        [total-width total-height] (bounds body)



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

        scroll-elem (ui/scrollview
                     scroll-bounds [(- (clampx offset-x))
                                    (- (clampy offset-y))]
                     body)

        div0 (fn [a b]
             (if (zero? b)
               b
               (/ a b)))

        on-mouse-move
        (if mdowny?
          (fn [body]
            (ui/on-mouse-move
             (fn [[mx my]]
               [[:set $offset-y (clampy (* (div0 (float my) height)
                                           max-offset-y))]])
             body))
          (if mdownx?
            (fn [body]
              (ui/on-mouse-move
               (fn [[mx my]]
                 [[:set $offset-x (clampx (* (div0 (float mx) width)
                                             max-offset-x))]])
               body))
            identity))]
    (on-mouse-out
     {:hover (get extra [:mdown :hover])
      :mouse-out
      (fn []
        [[:set $mdowny? nil]
         [:set $mdownx? nil]])
      :body
      (ui/wrap-on
       :scroll
       (fn [handler [ox oy :as offset] pos]
         (let [intents (handler offset pos)]
           (if (seq intents)
             intents
             (when (or (not= offset-x
                             (clampx (+ ox offset-x)))
                       (not= offset-y
                             (clampy (+ oy offset-y))))
               [[:update $offset-x (fn [old-offset]
                                     (clampx (+ ox offset-x)))]
                [:update $offset-y (fn [old-offset]
                                     (clampy (+ oy offset-y)))]]))))
       (on-mouse-move
        (ui/on-mouse-event
         (fn [[mx my :as mpos] button mouse-down? mods]
           (if mouse-down?
             (let [new-mdownx? (and (> my height)
                                    (> total-width width))
                   new-mdowny? (and (> mx width)
                                    (> total-height height))]
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
                                          scroll-button-size height)
                        (let [top (/ offset-y total-height)
                              bottom (/ (+ offset-y height)
                                        total-height)]

                          (translate 0 (* height top)
                                     (with-color
                                       [0.73 0.73 0.73]
                                       (ui/rounded-rectangle scroll-button-size (* height (- bottom top)) (/ scroll-button-size 2)))
                                     ))

                        (with-color [0.89 0.89 0.89]
                          (with-style :membrane.ui/style-stroke
                            (rectangle scroll-button-size height)))]))
          (when (> total-width width)
            (translate 0 height
                       [(filled-rectangle [0.941 0.941 0.941]
                                          width scroll-button-size)
                        (let [left (/ offset-x total-width)
                              right (/ (+ offset-x width)
                                       total-width)]
                          (translate (* width left) 0
                                     (with-color
                                       [0.73 0.73 0.73]
                                       (ui/rounded-rectangle (* width (- right left)) scroll-button-size  (/ scroll-button-size 2)))
                                     )
                          )
                        (with-color [0.89 0.89 0.89]
                          (with-style :membrane.ui/style-stroke
                            (rectangle width scroll-button-size )))]))

          ])))})))

(defui test-scrollview [{:keys [state]}]
  (scrollview {:scroll-bounds [200 200]
               :body
               (apply
                vertical-layout
                (for [i (range 100)]
                  (label (str "The quick brown fox"
                              " jumped over the lazy dog"
                              ))))}))

(defeffect ::toggle [$bool]
  (dispatch! :update $bool not))

(defui checkbox
  "Checkbox component."
  [{:keys [checked?]}]
  (on
   :mouse-down
   (fn [_]
     [[::toggle $checked?]])
   (ui/checkbox checked?)))



(defui dropdown-list
  [{:keys [options selected]}]
  (let [
        labels (for [option (map second options)]
                 (ui/label option))
        max-width (reduce max 0 (map ui/width labels))
        padding-y 8
        padding-x 12

        rows
        (apply
         vertical-layout
         (for [[value option] options]
           (let [hover? (get extra [:hover? value])


                 selected? (= selected value)

                 label (if selected?
                         (ui/with-color [1 1 1]
                           (ui/label option))
                         (ui/label option))

                 [_ h] (bounds label)
                 row-height (+ h 4)
                 row-width (+ max-width (* 2 padding-x))]
             (on-hover
              {:hover? hover?
               :body
               (on
                :mouse-down
                (fn [_]
                  [[::select $selected value]])

                [(spacer row-width row-height)
                 (cond

                   selected?
                   (ui/filled-rectangle [0 0.48 1]
                                        row-width row-height)

                   hover?
                   (ui/filled-rectangle [0.976 0.976 0.976]
                                        row-width row-height))
                 (translate padding-x 2
                            label)])}))))
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
     (ui/with-style
       ::ui/style-fill
       (ui/with-color [1 1 1]
         (ui/rounded-rectangle rows-width
                               (+ rows-height (* 2 padding-y))
                               4)))
     (translate 0 (- padding-y 2)
                rows)])
  )

(defui dropdown [{:keys [options selected open?]}]
  (vertical-layout
   (on
    :mouse-down
    (fn [_]
      [[:update $open? not]])
    (ui/bordered [10 10]
                 (if selected
                   (ui/label (first (keep (fn [[value option]]
                                            (when (= value selected)
                                              option))
                                          options)))
                   (with-color [0.7 0.7 0.7]
                     (ui/label "no selection")))))
   (when open?
     (on
      ::select
      (fn [$selected value]
        [[::select $selected value]
         [:set $open? false]])
      (dropdown-list {:options options :selected selected})))
   ))

(defeffect ::select [$selected value]
  (dispatch! :set $selected value))

(comment
  (skia/run (component/make-app #'dropdown {:options [[:this "This"]
                                                      [:that "That "]
                                                      [:the-other "The Other"]]})))

(defeffect ::counter-dec [$num min]
  (if min
    (dispatch! :update $num #(max min (dec %)))
    (dispatch! :update $num dec)))

(defeffect ::counter-inc [$num max]
  (if max
    (dispatch! :update $num #(min max (inc %)))
    (dispatch! :update $num inc)))

(defui counter [{:keys [num min max]
                 :or {num 0}}]
  (horizontal-layout
   (button {:text "-"
            :on-click
            (fn []
              [[::counter-dec $num min]])})
   (ui/spacer 5 0)
   (let [lbl (ui/label num)
         w (ui/width lbl)
         padding (/ (clojure.core/max 0 (- 20 w)) 2)]
     (horizontal-layout
      (spacer padding 0)
      lbl
      (spacer padding 0)))
   (ui/spacer 5 0)
   (button {:text "+"
            :on-click
            (fn []
              [[::counter-inc $num max]])})))


(comment
  (skia/run (component/make-app #'counter {:num 3})))

(defeffect ::update-slider [$num min max max-width integer? x]
  (let [ratio (/ x max-width)
        num (+ min (* ratio (- max min)))
        num (if integer?
              (int num)
              (double num))]
   (dispatch! :set $num num)))

(defui number-slider [{:keys [num max-width min max integer? mdown?]
                       :or {max-width 100}}]
  (let [ratio (/ (- num min)
                 (- max min))
        width (* max-width (double ratio))
        tint 0.85
        gray [tint tint tint]]
    (on
     :mouse-down
     (fn [[x y]]
       [[:set $mdown? true]
        [::update-slider $num min max max-width integer? x]])
     :mouse-up
     (fn [[x y]]
       [[:set $mdown? false]
        [::update-slider $num min max max-width integer? x]])
     :mouse-move
     (fn [[x y]]
       (when mdown?
         [[::update-slider $num min max max-width integer? x]]))
     (ui/translate 1 1
                   (let [height 20
                         lbl (ui/label (if integer?
                                         num
                                         #?(:clj (format "%.2f" (double num))
                                            :cljs (.toFixed (double num) 2))))]
                     [(ui/with-style :membrane.ui/style-fill
                        (ui/with-color gray
                          (rectangle width height)))
                      lbl
                      (ui/with-style :membrane.ui/style-stroke
                        (rectangle max-width height))
                      ]))))
  )


(comment
  (skia/run (component/make-app #'number-slider {:num 3
                                                 :min 0
                                                 :max 20}))

  (skia/run (component/make-app #'number-slider {:num 3
                                                 :min 5
                                                 :max 20
                                                 :max-width 300
                                                 :integer? true})))
