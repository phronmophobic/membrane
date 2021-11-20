(ns tutorial
  (:require [membrane.java2d :as java2d]
            [membrane.ui :as ui
             :refer [vertical-layout
                     translate
                     horizontal-layout
                     button
                     label
                     with-color
                     bounds
                     spacer
                     on]]
            [membrane.component :as component
             :refer [defui defeffect]]
            [membrane.basic-components :as basic]))

(defonce current-view (atom nil))

(defn main-ui []
  (ui/padding 5
              @current-view))

(def window
  (java2d/run #'main-ui
    {:window-start-width 500
     :window-start-height 400}))

(def repaint (::java2d/repaint window))
(add-watch current-view ::repaint
           (fn [_ _ _ _]
             (repaint)))

(reset! current-view
        (ui/label "Hello World!"))

(reset! current-view
        (ui/label "Hello\nWorld!" (ui/font "Menlo" 22)))

(reset! current-view
        (ui/label "Hello\nWorld!" (ui/font nil 22)))

(reset! current-view
        (ui/path [24.20 177.98]
                 [199.82 37.93]
                 [102.36 240.31]
                 [102.36 15.68]
                 [199.82 218.06]
                 [24.20 78.01]
                 [243.2 127.99]
                 [24.20 177.98]))

(reset! current-view
        (ui/with-style :membrane.ui/style-stroke
          (ui/with-stroke-width 3
            (ui/with-color [1 0 0]
              (apply
               ui/path
               (for [i (range 10)]
                 [(* 30 i)
                  (if (even? i) 0 30)]))))))

(reset! current-view
        (ui/with-style :membrane.ui/style-stroke
          (ui/with-stroke-width 3
            (ui/with-color [0.5 0 0.5]
              (ui/rectangle 100 200)))))

(reset! current-view
        (ui/rounded-rectangle 200 100 10))

(reset! current-view
        (ui/with-color [1 0 0]
          (ui/label "Hello")))

(reset! current-view
        [(ui/with-color [1 0 0 0.75]
           (ui/label "red"))
         (ui/with-color [0 1 0 0.75]
           (ui/label "green"))
         (ui/with-color [0 0 1 0.75]
           (ui/label "blue"))])

(reset! current-view
        [(ui/with-style :membrane.ui/style-stroke
           [(ui/path [0 0] [0 100])
            (ui/path [0 0] [60 0])])
         (ui/rectangle 30 50)
         (ui/translate 30 50
                       (ui/rectangle 30 50))])

(reset! current-view
        (ui/scale 3 10
                  (ui/label "sx: 3, sy: 10")))

(reset! current-view
        (ui/vertical-layout
         (ui/button "hello")
         (ui/button "world")))

(reset! current-view (ui/horizontal-layout
                      (ui/button "hello")
                      (ui/button "world")))

(reset! current-view (apply ui/horizontal-layout
                            (interpose
                             (spacer 10 0)
                             (for [i (range 1 5)]
                               (ui/with-color [0 0 0 (/ i 5.0)]
                                 (ui/rectangle 100 50))))))

;; most layouts can be created just by using bounds
(defn center [elem [width height]]
  (let [[ewidth eheight] (bounds elem)]
    (translate (int (- (/ width 2)
                       (/ ewidth 2)))
               (int (- (/ height 2)
                       (/ eheight 2)))
               elem)))

(reset! current-view (ui/horizontal-layout
                      (ui/padding 10 10
                                  (ui/checkbox false))
                      (ui/padding 10 10
                                  (ui/checkbox true))))

(reset! current-view (ui/horizontal-layout
                      (ui/padding 10 10
                                  (ui/button "button"))
                      (ui/padding 10 10
                                  (ui/button "button" nil true))))

;; mouse-down event at location [15 15]
(let [mpos [15 15]]
  (ui/mouse-down
   (ui/translate 10 10
                 (ui/on :mouse-down (fn [[mx my]]
                                   ;;return a sequence of effects
                                   [[:say-hello]])
                     (ui/label "Hello")))
   mpos))

(let [elem (ui/on
            ;; return nil for any mouse-down event
            :mouse-down (fn [_] nil)
            (ui/button "Big Red Button"
                       (fn []
                         [[:self-destruct!]])))]
  (ui/mouse-down elem [20 20]))

(let [elem (ui/no-events
            (ui/button "Big Red Button"
                       (fn []
                         [[:self-destruct!]])))]
  (ui/mouse-down elem [20 20]))

(let [lower-case-letters (set (map str "abcdefghijklmnopqrstuvwxyz"))
      child-elem (ui/on :key-press
                        (fn [s]
                          [[:child-effect1 s]
                           [:child-effect2 s]])
                        (ui/label "child elem"))
      elem (ui/on
            :key-press (fn [s]
                         (when (contains? lower-case-letters s)
                           (ui/key-press child-elem s)))
            child-elem)]
  {"a" (ui/key-press elem "a")
   "." (ui/key-press elem ".")})

(let [child-elem (ui/on :key-press
                        (fn [s]
                          [[:child-effect1 s]
                           [:child-effect2 s]])
                        (ui/label "child elem"))
      elem (ui/on
            :key-press (fn [s]
                        (if (= s ".")
                          [[:do-something-special]]
                          (ui/key-press child-elem s)
                          ))
            child-elem)]
  {"a" (ui/key-press elem "a")
   "." (ui/key-press elem ".")})


(defn search-bar [s]
  (horizontal-layout
   (on
    :mouse-down (fn [_]
                  [[:search s]])
    (ui/button "Search"))
   (ui/label s)))

(let [selected-search-type :full-text
      bar (search-bar "clojure")
      elem (on :search
               (fn [s]
                 [[:search selected-search-type s]])
               bar)]
  (ui/mouse-down elem
                 [10 10]))


(def app-state (atom false))

(defn checkbox [checked?]
  (on
   :mouse-down
   (fn [_]
     (swap! app-state not)
     nil)
   (ui/label (if checked?
               "X"
               "O"))))

(comment
  (java2d/run #(checkbox @app-state))
  ,)

(defui checkbox [ {:keys [checked?]}]
  (on
   :mouse-down
   (fn [_]
     [[::toggle $checked?]])
   (ui/label (if checked?
               "X"
               "O"))))

(defeffect ::toggle [$checked?]
  (dispatch! :update $checked? not))

(defui checkbox-test [{:keys [x y z]}]
  (vertical-layout
   (checkbox {:checked? x})
   (checkbox {:checked? y})
   (checkbox {:checked? z})
   (ui/label
    (with-out-str (clojure.pprint/pprint
                   {:x x
                    :y y
                    :z z})))))

(comment
  (java2d/run (component/make-app #'checkbox-test {:x false :y true :z false})
    {:window-start-width 400
     :window-start-height 400})
  
  ,)


(defui item-row [ {:keys [item-name selected?]}]
  (on
   :mouse-down
   (fn [_]
     [[:update $selected? not]])
   ;; put the items side by side
   (horizontal-layout
    (translate 5 5
               ;; checkbox in `membrane.ui` is non interactive.
               (ui/checkbox selected?))
    (spacer 5 0)
    (ui/label item-name))))

(comment
 ;; It's a very common workflow to work on sub components one piece at a time.
  (java2d/run (component/make-app #'item-row {:item-name "my item" :selected? false})
    {:window-start-width 400
     :window-start-height 400})

  ,)


(defui item-selector
  "`item-names` a vector of choices to select from
`selected` a set of selected items
`str-filter` filter out item names that don't contain a case insensitive match for `str-filter` as a substring
"
  [{:keys [item-names selected str-filter]
    :or {str-filter ""
         selected #{}}}]
  (let [filtered-items (filter #(clojure.string/includes? (clojure.string/lower-case %) str-filter) item-names)]
    (apply
     vertical-layout
     (basic/textarea {:text str-filter})
     (for [iname filtered-items]
       ;; override the default behaviour of updating the `selected?` value directly
       ;; instead, we'll keep the list of selected items in a set
       (on :update
           (fn [& args]
             [[:update $selected (fn [selected]
                                   (if (contains? selected iname)
                                     (disj selected iname)
                                     (conj selected iname)))]])
           (item-row {:item-name iname :selected? (get selected iname)}))))))

(comment
  (java2d/run (component/make-app #'item-selector {:item-names (->> (clojure.java.io/file ".")
                                (.listFiles)
                                (map #(.getName %)))} )
    {:window-start-width 400
     :window-start-height 400})

  ,)



(defn file-selector [path]
  (let [state (atom {:item-names
                     (->> (clojure.java.io/file path)
                          (.listFiles)
                          (map #(.getName %))
                          sort)})]
    (java2d/run-sync (component/make-app #'item-selector state))
    (:selected @state)))

