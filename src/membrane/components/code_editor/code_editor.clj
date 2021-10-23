(ns membrane.components.code-editor.code-editor
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [liq.buffer :as buffer]
            [liq.util :as util]
            [liq.modes.clojure-mode :as clojure-mode]
            [liq.highlighter :as highlighter]
            [membrane.basic-components :as basic]
            [membrane.component :as component
             :refer [defui
                     defeffect]]
            [membrane.ui :as ui
             :refer [IChildren
                     IOrigin
                     bounds
                     IBounds
                     vertical-layout
                     horizontal-layout
                     maybe-key-press
                     on]])
  #_(:import [java.awt Font Color GraphicsEnvironment Dimension GraphicsDevice Window]
           [java.awt.event InputEvent KeyListener ComponentListener WindowAdapter]
           [java.awt.image BufferedImage]
           [javax.swing JFrame ImageIcon JPanel]
           javax.swing.SwingUtilities))
-
;; (import 'java.awt.Color)
;; (defn- hexcolor
;;   [h]
;;   (let [h (if (= (count h) 3)
;;             (apply str (for [c h]
;;                          (str c c))))]
;;    (mapv
;;     #(float (/ % 255))
;;     ((juxt #(.getRed %) #(.getGreen %) #(.getBlue %))
;;      (Color/decode (str "0x" h))))))

;; (defn convert-colormap
;;   "Convert hex values in colormap to
;;   type java.awt.Color."
;;   [m]
;;   (reduce (fn [r [k v]] (assoc r k (hexcolor v))) {} m))

(def cm-colors
  {:attribute [0.0 0.0 0.8],
   :hr [0.6 0.6 0.6],
   :meta [0.33333334 0.33333334 0.33333334],
   :variable [0.0 0.33333334 0.6666667],
   :invalidchar [1.0 0.0 0.0],
   :number [0.06666667 0.4 0.26666668],
   :string-2 [1.0 0.33333334 0.0],
   :nonmatchingbracket [1.0 0.13333334 0.13333334],
   :variable-3 [0.0 0.53333336 0.33333334],
   :property [0.6666667 0.33333334 0.0],
   :positive [0.13333334 0.6 0.13333334],
   :qualifier [0.33333334 0.33333334 0.33333334],
   :negative [0.8666667 0.26666668 0.26666668],
   :string [0.6666667 0.06666667 0.06666667],
   :header [0.0 0.6 0.0],
   :matchingbracket [0.0 1.0 0.0],
   :keyword [0.46666667 0.0 0.53333336],
   :link [0.0 0.0 0.8],
   :bracket [0.6 0.6 0.46666667],
   :comment [0.6666667 0.33333334 0.0],
   :operator [0 0 0],
   :error [1.0 0.0 0.0],
   :def [0.0 0.0 1.0],
   :tag [0.06666667 0.46666667 0.0],
   :atom [0.13333334 0.06666667 0.6],
   :builtin [0.2 0.0 0.6666667]})

(def colors {nil [0 0 0],
             :definition (:def cm-colors)
             :special (:builtin cm-colors)
             ;; :yellow [1 0 0];; (:def cm-colors)
             :default [0.6666667 0.6666667 0.6666667],
             :green [1 0 0];; [0.4509804 0.7882353 0.21176471],
             :string (:string cm-colors)
             :stringst (:string-2 cm-colors)
             :keyword (:keyword cm-colors)
             :red [1.0 0.0 0.0],
             :plain [0 0 0],
             :comment (:comment cm-colors)})

(def plain-color (get colors :plain))

(def bgcolors
  {nil [0.2 0.2 0.2],
   :statusline [0.0 0.0 0.0],
   :default [0.2 0.2 0.2],
   :cursor2 [0.0 0.0 0.8],
   :hl [1.0 1.0 0.0],
   :cursor0 [0.09411765 0.09411765 0.09411765],
   :plain [0.09411765 0.09411765 0.09411765],
   :cursor1 [0.2 0.4 0.2],
   :selection [1.0 0.0 0.0]})


(def buffer-font (ui/font "Menlo" 11))
(def lw 6.6225586)
(def lh 12.8046875)

(def open-chars
  #{\( \{ \[})
(def close-chars
  #{\) \} \]})


(defn highlight-paren
  [buf]
  (if (open-chars (buffer/get-char buf))
    
    (let [r (buffer/paren-matching-region buf
                                          (buf ::buffer/cursor))
          p (second r)]
      (if p
        (buffer/set-style buf p :red) 
        buf))
    (let [cursor (::buffer/cursor buf)
          cursor (update cursor ::buffer/col #(max 0 (dec %)))
          ch (buffer/get-char buf cursor)]
      (if (close-chars ch)
        (let [r (buffer/paren-matching-region buf
                                              cursor)
              p (second r)]
          (if p
            (buffer/set-style buf p :red) 
            buf))
        buf))))


(defn draw-buffer [buf focused?]
  (let [buf (highlight-paren buf)
        ]
    [
     (let [cursor (::buffer/cursor buf)
           row (::buffer/row cursor)
           col (::buffer/col cursor)
           ]
       (ui/translate (* lw (dec col))
                     (+ 2 (* lh (dec row)))
                     (ui/filled-rectangle [0 0 0 (if focused?
                                                   0.5
                                                   0.1)]
                                          lw lh)))
     (vec
      (for [[row line] (map-indexed vector (::buffer/lines buf))]
        (ui/translate
         0 (* lh row)
         (vec
          (for [[col cm] (map-indexed vector line)
                :let [c (::buffer/char cm)
                      style (get cm ::buffer/style :plain)]]
            (ui/translate
             (* lw col) 0
             (ui/with-color (get colors style plain-color)
               (ui/label (str c) buffer-font))))))))]))



(defrecord Buffer [font focused? buf]
    IOrigin
    (-origin [_]
        [0 0])

    IChildren
    (-children [this]
      (draw-buffer buf focused?))

    IBounds
    (-bounds [this]
      (let [
            line-count (buffer/line-count buf)
            max-col-count (reduce
                           #(max %1 (buffer/col-count buf %2))
                           0
                           (map inc (range line-count)))]
        [(max 50 (* lw
                    ;; extend past end a bit
                    (+ 3 max-col-count)))
         (* lh line-count)])))

(defonce buffer-cache (atom nil))

(def hl (:syntax clojure-mode/mode))
(defn highlight [buf]
  (highlighter/highlight buf hl ))


(defeffect ::insert-text [$buf s]
  (dispatch! :update $buf (comp highlight
                                #(buffer/insert-string % s))))

(defeffect ::handle-key [$buf s]
  (case s
    :up
    (dispatch! :update $buf buffer/up)

    :down
    (dispatch! :update $buf buffer/down)

    :left
    (dispatch! :update $buf buffer/left)
    
    :right
    (dispatch! :update $buf buffer/right)

    :enter
    (dispatch! :update $buf (comp
                             highlight
                             #(buffer/insert-char % \newline)))

    :tab
    (dispatch! :update $buf #(buffer/insert-char % \tab))

    :backspace
    (dispatch! :update $buf buffer/delete-backward)
    
    ;; else 
    (when (string? s)
      (dispatch! :update $buf (comp highlight
                                    #(buffer/insert-char % (first s))))))
  
  )

(defeffect ::update-cursor [$buf [mx my]]
  (dispatch! :update $buf 
             (fn [buf]
               (let [
                     row (min (buffer/line-count buf)
                              (inc (int (/ my lh))))
                     col (min (inc (int (/ mx lw)))
                              (inc (buffer/col-count buf row)))]
                 (assoc buf
                        ::buffer/cursor {::buffer/row row
                                         ::buffer/col col})))))

(defui text-editor [{:keys [buf
                            ^:membrane.component/contextual focus]}]
  (let [focused? (= $buf focus)]
    (on
     :mouse-down
     (fn [pos]
       (if focused?
         [[::update-cursor $buf pos]]
         [[:set $focus $buf]
          [::update-cursor $buf pos]]))
     :clipboard-paste
     (fn [s]
       (when focused?
         [[::insert-text $buf s]]))
     (maybe-key-press
      focused?
      (on
       :key-press
       (fn [s]
         [[::handle-key $buf s]])
       (ui/->Cached
        (Buffer. nil focused? buf)))))))


(buffer/text (buffer/buffer "adsf\nadsfa"))

(defui buf-ui [{:keys [buf]}]
  (ui/translate 10 10
                [(let [gray 0.7]
                   (ui/with-color [gray gray gray]
                     (ui/with-style ::ui/style-stroke
                       (ui/rectangle 200 200))))
                 (text-editor {:buf buf})])
  )

(defonce buf-state (atom nil))
(defn initial-buf-state []
  {:buf (buffer/buffer "" {:rows 40 :cols 5
                           :mode :insert})})

#_(defn test-buf []
  (reset! buf-state (initial-buf-state))
  (skia/run (component/make-app #'buf-ui
                                buf-state))
  )

