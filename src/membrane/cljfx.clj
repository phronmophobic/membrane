(ns membrane.cljfx
  (:require [cljfx.api :as fx]
            [com.rpl.specter :as spec
             :refer [ATOM ALL FIRST LAST MAP-VALS META]]
            membrane.component
            [membrane.ui :as ui
             :refer [IBounds
                     bounds
                     origin]])

  (:import
   com.sun.javafx.tk.FontMetrics
   com.sun.javafx.tk.Toolkit

   java.awt.geom.PathIterator

   javafx.application.Application
   javafx.scene.Group
   javafx.scene.Scene
   javafx.scene.SnapshotParameters
   javafx.scene.canvas.Canvas
   javafx.scene.canvas.GraphicsContext
   javafx.scene.image.Image
   javafx.scene.input.KeyCode
   javafx.scene.input.KeyEvent
   javafx.scene.input.MouseEvent
   javafx.scene.paint.Color
   javafx.scene.shape.ArcType
   javafx.scene.shape.Path
   javafx.scene.text.Font
   javafx.stage.Screen
   javafx.stage.Stage))

(def ^:dynamic *ctx* nil)
(def ^:dynamic *paint-style* :membrane.ui/style-fill)
(def ^:dynamic *image-cache* (atom {}))
(def ^:dynamic *draw-cache* (atom {}))

(def font-loader (-> (Toolkit/getToolkit)
                     .getFontLoader))

(defprotocol IDraw
  :extend-via-metadata true
  (draw [this]))

(ui/add-default-draw-impls! IDraw #'draw)

(set! *warn-on-reflection* true)

(defn get-image [image]
  (if (instance? Image image)
    image
    (if-let [img (get @*image-cache* image)]
      img
      (let [img (with-open [is (clojure.java.io/input-stream image)]
                  (Image. ^java.io.InputStream is))]
        (swap! *image-cache* assoc image img)
        img))))

(defn get-java-font [font]
  (Font. (:name font)
         (or (:size font)
             (:size ui/default-font))) )



(def mouse-button->int
  {javafx.scene.input.MouseButton/PRIMARY 1
   javafx.scene.input.MouseButton/MIDDLE 2
   javafx.scene.input.MouseButton/SECONDARY 3})

(def keycodes
  {
   :accept KeyCode/ACCEPT
   :add KeyCode/ADD
   :again KeyCode/AGAIN
   :all-candidates KeyCode/ALL_CANDIDATES
   :alphanumeric KeyCode/ALPHANUMERIC
   :alt KeyCode/ALT
   :alt_graph KeyCode/ALT_GRAPH
   :ampersand KeyCode/AMPERSAND
   :asterisk KeyCode/ASTERISK
   :at KeyCode/AT
   :back-quote KeyCode/BACK_QUOTE
   :back-slash KeyCode/BACK_SLASH
   :backspace KeyCode/BACK_SPACE
   :begin KeyCode/BEGIN
   :braceleft KeyCode/BRACELEFT
   :braceright KeyCode/BRACERIGHT
   :cancel KeyCode/CANCEL
   :caps KeyCode/CAPS
   :channel-down KeyCode/CHANNEL_DOWN
   :channel-up KeyCode/CHANNEL_UP
   :circumflex KeyCode/CIRCUMFLEX
   :clear KeyCode/CLEAR
   :close-bracket KeyCode/CLOSE_BRACKET
   :code-input KeyCode/CODE_INPUT
   :colon KeyCode/COLON
   :colored-key-0 KeyCode/COLORED_KEY_0
   :colored-key-1 KeyCode/COLORED_KEY_1
   :colored-key-2 KeyCode/COLORED_KEY_2
   :colored-key-3 KeyCode/COLORED_KEY_3
   :comma KeyCode/COMMA
   :command KeyCode/COMMAND
   :compose KeyCode/COMPOSE
   :context-menu KeyCode/CONTEXT_MENU
   :control KeyCode/CONTROL
   :convert KeyCode/CONVERT
   :copy KeyCode/COPY
   :cut KeyCode/CUT
   :dead-abovedot KeyCode/DEAD_ABOVEDOT
   :dead-abovering KeyCode/DEAD_ABOVERING
   :dead-acute KeyCode/DEAD_ACUTE
   :dead-breve KeyCode/DEAD_BREVE
   :dead-caron KeyCode/DEAD_CARON
   :dead-cedilla KeyCode/DEAD_CEDILLA
   :dead-circumflex KeyCode/DEAD_CIRCUMFLEX
   :dead-diaeresis KeyCode/DEAD_DIAERESIS
   :dead-doubleacute KeyCode/DEAD_DOUBLEACUTE
   :dead-grave KeyCode/DEAD_GRAVE
   :dead-iota KeyCode/DEAD_IOTA
   :dead-macron KeyCode/DEAD_MACRON
   :dead-ogonek KeyCode/DEAD_OGONEK
   :dead-semivoiced-sound KeyCode/DEAD_SEMIVOICED_SOUND
   :dead-tilde KeyCode/DEAD_TILDE
   :dead-voiced-sound KeyCode/DEAD_VOICED_SOUND
   :decimal KeyCode/DECIMAL
   :delete KeyCode/DELETE
   :digit0 KeyCode/DIGIT0
   :digit1 KeyCode/DIGIT1
   :digit2 KeyCode/DIGIT2
   :digit3 KeyCode/DIGIT3
   :digit4 KeyCode/DIGIT4
   :digit5 KeyCode/DIGIT5
   :digit6 KeyCode/DIGIT6
   :digit7 KeyCode/DIGIT7
   :digit8 KeyCode/DIGIT8
   :digit9 KeyCode/DIGIT9
   :divide KeyCode/DIVIDE
   :dollar KeyCode/DOLLAR
   :down KeyCode/DOWN
   :eject-toggle KeyCode/EJECT_TOGGLE
   :end KeyCode/END
   :enter KeyCode/ENTER
   :equals KeyCode/EQUALS
   :escape KeyCode/ESCAPE
   :euro-sign KeyCode/EURO_SIGN
   :exclamation-mark KeyCode/EXCLAMATION_MARK
   :f1 KeyCode/F1
   :f10 KeyCode/F10
   :f11 KeyCode/F11
   :f12 KeyCode/F12
   :f13 KeyCode/F13
   :f14 KeyCode/F14
   :f15 KeyCode/F15
   :f16 KeyCode/F16
   :f17 KeyCode/F17
   :f18 KeyCode/F18
   :f19 KeyCode/F19
   :f2 KeyCode/F2
   :f20 KeyCode/F20
   :f21 KeyCode/F21
   :f22 KeyCode/F22
   :f23 KeyCode/F23
   :f24 KeyCode/F24
   :f3 KeyCode/F3
   :f4 KeyCode/F4
   :f5 KeyCode/F5
   :f6 KeyCode/F6
   :f7 KeyCode/F7
   :f8 KeyCode/F8
   :f9 KeyCode/F9
   :fast-fwd KeyCode/FAST_FWD
   :final KeyCode/FINAL
   :find KeyCode/FIND
   :full-width KeyCode/FULL_WIDTH
   :game-a KeyCode/GAME_A
   :game-b KeyCode/GAME_B
   :game-c KeyCode/GAME_C
   :game-d KeyCode/GAME_D
   :greater KeyCode/GREATER
   :half-width KeyCode/HALF_WIDTH
   :help KeyCode/HELP
   :hiragana KeyCode/HIRAGANA
   :home KeyCode/HOME
   :info KeyCode/INFO
   :input-method-on-off KeyCode/INPUT_METHOD_ON_OFF
   :insert KeyCode/INSERT
   :inverted-exclamation-mark KeyCode/INVERTED_EXCLAMATION_MARK
   :japanese-hiragana KeyCode/JAPANESE_HIRAGANA
   :japanese-katakana KeyCode/JAPANESE_KATAKANA
   :japanese-roman KeyCode/JAPANESE_ROMAN
   :kana KeyCode/KANA
   :kana-lock KeyCode/KANA_LOCK
   :kanji KeyCode/KANJI
   :katakana KeyCode/KATAKANA
   :kp-down KeyCode/KP_DOWN
   :kp-left KeyCode/KP_LEFT
   :kp-right KeyCode/KP_RIGHT
   :kp-up KeyCode/KP_UP
   :left KeyCode/LEFT
   :left-parenthesis KeyCode/LEFT_PARENTHESIS
   :less KeyCode/LESS
   :meta KeyCode/META
   :minus KeyCode/MINUS
   :modechange KeyCode/MODECHANGE
   :multiply KeyCode/MULTIPLY
   :mute KeyCode/MUTE
   :nonconvert KeyCode/NONCONVERT
   :num-lock KeyCode/NUM_LOCK
   :number-sign KeyCode/NUMBER_SIGN
   :numpad0 KeyCode/NUMPAD0
   :numpad1 KeyCode/NUMPAD1
   :numpad2 KeyCode/NUMPAD2
   :numpad3 KeyCode/NUMPAD3
   :numpad4 KeyCode/NUMPAD4
   :numpad5 KeyCode/NUMPAD5
   :numpad6 KeyCode/NUMPAD6
   :numpad7 KeyCode/NUMPAD7
   :numpad8 KeyCode/NUMPAD8
   :numpad9 KeyCode/NUMPAD9
   :open-bracket KeyCode/OPEN_BRACKET
   :page-down KeyCode/PAGE_DOWN
   :page-up KeyCode/PAGE_UP
   :paste KeyCode/PASTE
   :pause KeyCode/PAUSE
   :period KeyCode/PERIOD
   :play KeyCode/PLAY
   :plus KeyCode/PLUS
   :pound KeyCode/POUND
   :power KeyCode/POWER
   :previous-candidate KeyCode/PREVIOUS_CANDIDATE
   :printscreen KeyCode/PRINTSCREEN
   :props KeyCode/PROPS
   :quote KeyCode/QUOTE
   :quotedbl KeyCode/QUOTEDBL
   :record KeyCode/RECORD
   :rewind KeyCode/REWIND
   :right KeyCode/RIGHT
   :right-parenthesis KeyCode/RIGHT_PARENTHESIS
   :roman-characters KeyCode/ROMAN_CHARACTERS
   :scroll-lock KeyCode/SCROLL_LOCK
   :semicolon KeyCode/SEMICOLON
   :separator KeyCode/SEPARATOR
   :shift KeyCode/SHIFT
   :shortcut KeyCode/SHORTCUT
   :slash KeyCode/SLASH
   :softkey-0 KeyCode/SOFTKEY_0
   :softkey-1 KeyCode/SOFTKEY_1
   :softkey-2 KeyCode/SOFTKEY_2
   :softkey-3 KeyCode/SOFTKEY_3
   :softkey-4 KeyCode/SOFTKEY_4
   :softkey-5 KeyCode/SOFTKEY_5
   :softkey-6 KeyCode/SOFTKEY_6
   :softkey-7 KeyCode/SOFTKEY_7
   :softkey-8 KeyCode/SOFTKEY_8
   :softkey-9 KeyCode/SOFTKEY_9
   :star KeyCode/STAR
   :stop KeyCode/STOP
   :subtract KeyCode/SUBTRACT
   :tab KeyCode/TAB
   :track-next KeyCode/TRACK_NEXT
   :track-prev KeyCode/TRACK_PREV
   :undefined KeyCode/UNDEFINED
   :underscore KeyCode/UNDERSCORE
   :undo KeyCode/UNDO
   :up KeyCode/UP
   :volume-down KeyCode/VOLUME_DOWN
   :volume-up KeyCode/VOLUME_UP
   :windows KeyCode/WINDOWS})
(def keymap (into {} (map (comp vec reverse) keycodes)))

(def key-action-map
  {1 :press
   2 :repeat
   3 :release})

(defn printable? [c]
  (let [block (java.lang.Character$UnicodeBlock/of \backspace)]
    (and (not (Character/isISOControl ^char (.charValue ^Character c)))
         (not= KeyEvent/CHAR_UNDEFINED c)
         (some? block)
         (not= block java.lang.Character$UnicodeBlock/SPECIALS))))

(defn membrane-component [ui-var state set-state]
  (let [
        handler (fn dispatch!
                  ([] nil)
                  ([type & args]
                   (case type
                     :update
                     (let [[path f & args ] args]
                       (set-state
                        (fn [state]
                          (spec/transform* (membrane.component/path->spec path)
                                           (fn [& spec-args]
                                             (apply f (concat spec-args
                                                              args)))
                                           state))))
                     :set
                     (let [[path v] args]
                       (set-state
                        (fn [state]
                          (spec/setval* (membrane.component/path->spec path) v state))))

                     :get
                     (let [path (first args)]
                       (spec/select-one* (membrane.component/path->spec path)
                                         state))

                     :delete
                     (let [[path] args]
                       (set-state
                        (fn [state]
                          (spec/setval* (membrane.component/path->spec path) spec/NONE state))))

                     (let [effects @membrane.component/effects]
                       (let [handler (get effects type)]
                         (if handler
                           (apply handler dispatch! args)
                           (println "no handler for " type)))))))
        
        arglist (-> ui-var
                    meta
                    :arglists
                    first)
        m (first arglist)
        arg-names (disj (set (:keys m))
                        'extra
                        'context)
        defaults (:or m)
        top-level (membrane.component/top-level-ui
                   {:state state :$state []
                    :body ui-var
                    :arg-names arg-names
                    :defaults defaults
                    :handler handler})
        [width height] (ui/bounds top-level)]
    {:fx/type :canvas
     :width width
     :height height
     :focus-traversable true
     :on-key-typed (fn [e]
                     (let [s (.getCharacter ^javafx.scene.input.KeyEvent e)
                           ui top-level]
                       (try
                         (when-let [c (first s)]
                           (when (printable? c)
                             (ui/key-press ui s)))
                         (catch Exception e
                           (println e))))
                     )
     :on-key-pressed (fn [e]
                       (let [action :press
                             mods 0 ;; (.getModifiers e)
                             code (.getCode ^javafx.scene.input.KeyEvent e)
                             key-char (.getCharacter ^javafx.scene.input.KeyEvent e)]
                         (ui/key-event top-level key code action mods)
                         (let [k (get keymap code)]
                           (when (keyword? k)
                             (try
                               (ui/key-press top-level k)
                               (catch Exception e
                                 (println e)))))))
     :on-key-released (fn [e]
                        (let [action :release
                              mods 0 ;; (.getModifiers e)
                              code (.getCode ^javafx.scene.input.KeyEvent e)
                              key-char (.getCharacter ^javafx.scene.input.KeyEvent e)]
                          (ui/key-event top-level key code action mods)))
     :on-mouse-moved (fn [e]
                       (let [
                             x (.getX ^MouseEvent e)
                             y (.getY ^MouseEvent e)
                             pos [x y]]
                         (try
                           (doall (membrane.ui/mouse-move top-level pos))
                           (doall (membrane.ui/mouse-move-global top-level pos))

                           (catch Exception e
                             (println e)))))
     :on-mouse-dragged (fn [e]
                       (let [
                             x (.getX ^MouseEvent e)
                             y (.getY ^MouseEvent e)
                             pos [x y]]
                         (try
                           (doall (membrane.ui/mouse-move top-level pos))
                           (doall (membrane.ui/mouse-move-global top-level pos))

                           (catch Exception e
                             (println e)))))
     :on-mouse-pressed (fn [e]
                         (let [x (.getX ^MouseEvent e)
                               y (.getY ^MouseEvent e)
                               button (mouse-button->int (.getButton ^MouseEvent e))
                               mouse-down? true]
                           (try
                             (membrane.ui/mouse-event top-level [x y] button mouse-down? nil)
                             (catch Exception e
                               (throw e))))
                         )
     :on-mouse-released (fn [e]
                          (let [x (.getX ^MouseEvent e)
                                y (.getY ^MouseEvent e)
                                button (mouse-button->int (.getButton ^MouseEvent e))
                                mouse-down? false]
                            (try
                              (membrane.ui/mouse-event top-level [x y] button mouse-down? nil)
                              (catch Exception e
                                (throw e)))))
     :draw (fn [^Canvas canvas]
             (binding [*ctx* (.getGraphicsContext2D canvas)]
               (.setFill ^GraphicsContext *ctx* Color/WHITE)
               (.fillRect ^GraphicsContext *ctx* 0 0 width height)
               (.setFill ^GraphicsContext *ctx* Color/BLACK)
               (.setStroke ^GraphicsContext *ctx* Color/BLACK)
               
               (draw top-level)))}))



(defmacro save [& body]
  `(do
     (.save ^GraphicsContext *ctx*)
     (try
       ~@body
       (finally
         (.restore ^GraphicsContext *ctx*)))))


(extend-type membrane.ui.WithStrokeWidth
  IDraw
  (draw [this]
    (let [stroke-width (:stroke-width this)]
      (save
       (.setLineWidth ^GraphicsContext *ctx* stroke-width)
        (doseq [drawable (:drawables this)]
          (draw drawable))))))

(extend-type membrane.ui.WithStyle
  IDraw
  (draw [this]
    (let [style (:style this)]
      (binding [*paint-style* style]
        (doseq [drawable (:drawables this)]
            (draw drawable))))))


(def text-layout (-> (Toolkit/getToolkit)
                     .getTextLayoutFactory
                     .createLayout))
(def text-layout-type (type text-layout))

(defn text-bounds [jfont text]
  (let [native-font (com.sun.javafx.scene.text.FontHelper/getNativeFont jfont)]
    (.setContent ^com.sun.javafx.text.PrismTextLayout text-layout text native-font)
    (let [bounds (.getBounds ^com.sun.javafx.text.PrismTextLayout text-layout)]
      [(.getWidth ^com.sun.javafx.geom.RectBounds bounds)
       (.getHeight ^com.sun.javafx.geom.RectBounds bounds)]))
  ;; Using FontMetrics and getCharWidth doesn't quite work
  ;; I think it has to do with not incorporating kerning
  ;; however, text-layout is part of the non public javafx api
  #_(let [lines (clojure.string/split text #"\n" -1)

          metrics (.getFontMetrics font-loader jfont)
          line-height (.getLineHeight ^FontMetrics metrics)
        
          widths (map (fn [line]
                        (reduce (fn [width c]
                                  (+ width (.getCharWidth font-loader c jfont)))
                                0
                                line))
                      lines)
          maxx (reduce max 0 widths)
          maxy (* (dec line-height)
                  (count lines))]
      [maxx maxy]))


(defn draw-text-shape [font text]
  ;; Using non public API to get cursor, text selection, and text drawing all to match
  (let [jfont (get-java-font font)
        native-font (com.sun.javafx.scene.text.FontHelper/getNativeFont jfont)]
    (.setContent ^com.sun.javafx.text.PrismTextLayout text-layout text native-font)
    (let [shape (.getShape ^com.sun.javafx.text.PrismTextLayout text-layout com.sun.javafx.scene.text.TextLayout/TYPE_TEXT nil)

          commands (.getCommandsNoClone ^com.sun.javafx.geom.Path2D shape)
          coords (.getFloatCoordsNoClone ^com.sun.javafx.geom.Path2D shape)
          num-commands (.getNumCommands ^com.sun.javafx.geom.Path2D shape)]
      (loop [commands (seq (take num-commands commands))
             coord-idx 0]
        (if commands
          (let [cmd (first commands)]
            (case (int cmd)
              0 ;; PathIterator/SEG_MOVETO
              (do (.moveTo ^GraphicsContext *ctx*
                           (aget ^floats coords coord-idx) (aget ^floats coords (inc coord-idx)))
                  (recur (next commands) (+ coord-idx 2)))
              1 ;; PathIterator/SEG_LINETO
              (do (.lineTo ^GraphicsContext *ctx*
                           (aget ^floats coords coord-idx) (aget ^floats coords (inc coord-idx)))
                  (recur (next commands) (+ coord-idx 2)))
              2 ;; PathIterator/SEG_QUADTO
              (do (.quadraticCurveTo ^GraphicsContext *ctx*
                                     (aget ^floats coords coord-idx) (aget ^floats coords (inc coord-idx))
                                     (aget ^floats coords (+ 2 coord-idx)) (aget ^floats coords (+ 3 coord-idx)))
                  (recur (next commands) (+ coord-idx 4)))
              3 ;; PathIterator/SEG_CUBICTO
              (do (.bezierCurveTo ^GraphicsContext *ctx*
                                  (aget ^floats coords coord-idx) (aget ^floats coords (inc coord-idx))
                                  (aget ^floats coords (+ 2 coord-idx)) (aget ^floats coords (+ 3 coord-idx))
                                  (aget ^floats coords (+ 4 coord-idx)) (aget ^floats coords (+ 5 coord-idx)))
                  (recur (next commands) (+ coord-idx 6)))
              4 ;; PathIterator/SEG_CLOSE
              (do (.closePath ^GraphicsContext *ctx*)
                  (recur (next commands) coord-idx)))))))))

(defrecord LabelRaw [text font]
  IBounds
  (-bounds [_]
    (let [[maxx maxy] (text-bounds (get-java-font font)
                                   text)
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
      [maxx maxy]))

  IDraw
  (draw [this]
    (let [ ;;lines (clojure.string/split (:text this) #"\n" -1)
          jfont (get-java-font (:font this))
          metrics (-> (Toolkit/getToolkit)
                      .getFontLoader
                      (.getFontMetrics jfont))
          line-height (.getLineHeight metrics)
          ]
      (save
       (.beginPath ^GraphicsContext *ctx*)
       (draw-text-shape jfont (:text this))
       (.fill ^GraphicsContext *ctx*)

       ;; We are using the non public API to draw text
       ;; Keeping around the public API version in case
       ;; non public version breaks
       #_(when font
           (.setFont ^GraphicsContext *ctx* jfont))
       #_(.fillText ^GraphicsContext *ctx* (:text this) 0 line-height)
       #_(loop [lines (seq lines)
                y (dec line-height)]
           (when lines
             (let [line (first lines)]
               (.fillText  ^GraphicsContext *ctx* line  0  y)
               (recur (next lines) (+ y (dec line-height))))))))))



(declare ->Cached rectangle)
(extend-type membrane.ui.Label
  IBounds
  (-bounds [this]
    (let [[maxx maxy] (text-bounds (get-java-font (:font this))
                                             (:text this))
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
        [maxx maxy]))
  IDraw
  (draw [this]
    (draw (->Cached (LabelRaw. (:text this)
                               (:font this))))))


(defn image-draw [{:keys [image-path size opacity] :as image}]
  (when-let [img (get-image image-path)]
    (let [[w h] size]
      (if opacity
        (save
         (.setGlobalAlpha ^GraphicsContext *ctx* opacity)
         (.drawImage ^GraphicsContext *ctx* img 0 0 w h))
        (.drawImage ^GraphicsContext *ctx* img 0 0 w h)))))


(extend-type membrane.ui.Image
  IDraw
  (draw [this]
    (image-draw this)))

(extend-type membrane.ui.Translate
  IDraw
  (draw [this]
    (save
     (.translate ^GraphicsContext *ctx* (double (:x this)) (double (:y this)))
     (draw (:drawable this)))))


(def selection-color [0.6980392156862745
                      0.8431372549019608
                      1])

(extend-type javafx.scene.shape.MoveTo
  IDraw
  (draw [this]
    (.moveTo ^GraphicsContext *ctx* (.getX this) (.getY this))))

(extend-type javafx.scene.shape.LineTo
  IDraw
  (draw [this]
    (.lineTo ^GraphicsContext *ctx* (.getX this) (.getY this))))


(defn text-selection-draw [font text [selection-start selection-end] selection-color]
  (let [jfont (get-java-font font)
        native-font (com.sun.javafx.scene.text.FontHelper/getNativeFont jfont)
        [r g b a] selection-color
        paint (Color. r g b (or a 1))]
    (.setContent ^com.sun.javafx.text.PrismTextLayout text-layout text native-font)
    (let [path-elems (.getRange ^com.sun.javafx.text.PrismTextLayout text-layout selection-start selection-end com.sun.javafx.scene.text.TextLayout/TYPE_TEXT 0 0)]
      (save
       (.setFill ^GraphicsContext *ctx* paint)
       (.beginPath ^GraphicsContext *ctx*)
       (run! draw path-elems)
       (.fill ^GraphicsContext *ctx* ))))
  ;; Keeping around public API version
  #_(let [
          jfont (get-java-font font)
          lines (clojure.string/split-lines text)

          metrics (.getFontMetrics font-loader jfont)
          line-height (.getLineHeight ^FontMetrics metrics)
          selection-height line-height

          text (str text "8")
        
          ;;glyphs (.createGlyphVector jfont frc text)
          ]
      (loop [x 0
             y 0
             selection-start selection-start
             selection-length (- selection-end selection-start)
             idx 0]
        (when (pos? selection-length)
          (let [c (nth text idx)]
            (let [new-x (if (= c \newline)
                          0
                          (+ x (max 0 (.getCharWidth font-loader c jfont))))
                  new-y (if (= c \newline)
                          (+ y (dec line-height))
                          y)]
              (if (<= selection-start 0)
                (do
                  (let [selection-width (if (= c \newline)
                                          5
                                          (- new-x x))]
                    (draw (ui/translate x (+ y (- line-height
                                                  selection-height))
                                        (ui/filled-rectangle selection-color
                                                             selection-width selection-height))))
                  (recur new-x new-y 0 (dec selection-length) (inc idx)))
                (recur new-x new-y (dec selection-start) selection-length (inc idx)))))))))

(extend-type membrane.ui.TextSelection
  IBounds
  (-bounds [this]
    (let [[maxx maxy] (text-bounds (get-java-font (:font this))
                                   (:text this))
          maxx (max 0 maxx)
          maxy (max 0 maxy)]
      [maxx maxy]))

  IDraw
  (draw [this]
    (text-selection-draw
     (:font this)
     (:text this)
     (:selection this)
     selection-color)))

(extend-type membrane.ui.TextCursor
  IBounds
  (-bounds [this]
    (text-bounds (get-java-font (:font this)) (:text this)))

  IDraw
  (draw [this]
    (let [cursor (min (count (:text this)) (:cursor this))]
      (text-selection-draw (:font this) (str (:text this) "8") [cursor (inc cursor)]
                           [0.9 0.9 0.9]))))


(defn stroke-or-fill []
  (case *paint-style*
    :membrane.ui/style-fill (.fill ^GraphicsContext *ctx*)
    :membrane.ui/style-stroke (.stroke ^GraphicsContext *ctx*)
    :membrane.ui/style-stroke-and-fill (do (.stroke ^GraphicsContext *ctx*)
                                           (.fill ^GraphicsContext *ctx*))))

(extend-type membrane.ui.Path
  IDraw
  (draw [this]
    (.beginPath ^GraphicsContext *ctx*)
    (let [points (:points this)
          [x1 y1] (first points)]
      (.moveTo ^GraphicsContext *ctx* x1 y1)
      (doseq [[x y] (next points)]
        (.lineTo ^GraphicsContext *ctx* x y))
      (stroke-or-fill))))


(extend-type membrane.ui.RoundedRectangle
  IDraw
  (draw [this]
    (case *paint-style*
      :membrane.ui/style-fill
      (.fillRoundRect ^GraphicsContext *ctx*
                      0 0
                      (:width this) (:height this)
                      (:border-radius this) (:border-radius this))

      :membrane.ui/style-stroke
      (.strokeRoundRect ^GraphicsContext *ctx*
                        0 0
                        (:width this) (:height this)
                        (:border-radius this) (:border-radius this))

      :membrane.ui/style-stroke-and-fill
      (do (.strokeRoundRect ^GraphicsContext *ctx*
                            0 0
                            (:width this) (:height this)
                            (:border-radius this) (:border-radius this)) (.stroke ^GraphicsContext *ctx*)
          (.fillRoundRect ^GraphicsContext *ctx*
                          0 0
                          (:width this) (:height this)
                          (:border-radius this) (:border-radius this))))))

(extend-type membrane.ui.WithColor
  IDraw
  (draw [this]
    (let [[r g b a] (:color this)
          paint (Color. r g b (or a 1))]
      (save
       (.setFill ^GraphicsContext *ctx* paint)
       (.setStroke  ^GraphicsContext *ctx*  paint)
       (doseq [drawable (:drawables this)]
         (draw drawable))))))

(extend-type membrane.ui.Scale
  IDraw
  (draw [this]
    (let [[sx sy] (:scalars this)]
      (save
       (.scale ^GraphicsContext *ctx* sx sy)
       (doseq [drawable (:drawables this)]
        (draw drawable))))))

(extend-type membrane.ui.Arc
  IDraw
  (draw [this]
    #_(let [arc-length (- (:rad-end this) (:rad-start this))]
      (draw-line-strip
       (doseq [i (range (inc (:steps this)))
               :let [pct (/ (float i) (:steps this))
                     rad (- (+ (:rad-start this)
                               (* arc-length pct)))
                     x (* (:radius this) (Math/cos rad))
                     y (* (:radius this) (Math/sin rad))]]
         (vertex x y))))))


(defn scissor-draw [scissor-view]
  (save
   (let [[ox oy] (:offset scissor-view)
         [w h] (:bounds scissor-view)]
     (save
      (.moveTo ^GraphicsContext *ctx* ox oy)
      (.lineTo ^GraphicsContext *ctx* (+ ox w) oy)
      (.lineTo ^GraphicsContext *ctx* (+ ox w) (+ oy h))
      (.lineTo ^GraphicsContext *ctx* ox (+ oy h))
      (.lineTo ^GraphicsContext *ctx* ox oy)
      (.clip ^GraphicsContext *ctx*)
      (draw (:drawable scissor-view))))))

(extend-type membrane.ui.ScissorView
  IDraw
  (draw [this]
      (scissor-draw this)))


(defn scrollview-draw [scrollview]
  (draw
   (ui/->ScissorView [0 0]
                  (:bounds scrollview)
                  (let [[mx my] (:offset scrollview)]
                    (ui/translate mx my (:drawable scrollview))))))

(extend-type membrane.ui.ScrollView
  IDraw
  (draw [this]
    (scrollview-draw this)))

(def ^:dynamic *already-drawing* false)
(defn cached-draw [drawable]
  (let [padding (float 5)]
      (if *already-drawing*
        (draw drawable)
        ;; always use content scale 2x until I can figure out how to
        ;; get javafx to tell me the current content scale
        (let [[xscale yscale :as content-scale] [2 2] ;; @(:window-content-scale *window*)
              paint-key [*paint-style*
                         (.getStroke ^GraphicsContext *ctx*)
                         (.getFill ^GraphicsContext *ctx*)]
              [img img-width img-height]
              (if-let [img-info (get @*draw-cache* [drawable content-scale paint-key])]
                img-info
                (do
                  (let [[w h] (bounds drawable)
                        img-width (int (+ (* 2 padding) (max 0 w)))
                        img-height (int (+ (* 2 padding) (max 0 h)))

                        max-texture-size 4096
                        canvas (Canvas. (min max-texture-size (* xscale img-width))
                                        (min max-texture-size (* yscale img-height)))
                        ctx (doto (.getGraphicsContext2D canvas)
                              (.setStroke (.getStroke ^GraphicsContext *ctx*))
                              (.setFill (.getFill ^GraphicsContext *ctx*)))
                      
                        img (binding [*ctx* ctx
                                      *already-drawing* true]
                              (when (and (not= xscale 1)
                                         (not= yscale 1))
                                (.scale ^GraphicsContext *ctx*  (float xscale) (float yscale)))
                              (.translate ^GraphicsContext *ctx* padding padding)
                            
                              (draw drawable)
                              (let [snapshot-params (doto (SnapshotParameters.)
                                                      (.setFill Color/TRANSPARENT))]
                                (.snapshot ^Canvas canvas snapshot-params nil)))
                        img-info [img img-width img-height]]
                    (swap! *draw-cache* assoc [drawable content-scale paint-key] img-info)
                    img-info)))]
          (.drawImage ^GraphicsContext *ctx* img (float (- padding)) (float (- padding)) img-width img-height)))))

(defrecord Cached [drawable]
  ui/IOrigin
  (-origin [_]
    (origin drawable))

  IBounds
  (-bounds [_]
    (bounds drawable))

  ui/IChildren
  (-children [this]
    [drawable])

  IDraw
  (draw [this]
    (cached-draw drawable)))

(extend-type membrane.ui.Cached
    IDraw
    (draw [this]
      (cached-draw (:drawable this))))

(defn index-for-position-line [jfont text px]
  
  (let [max-index (dec (.length ^String text))]
    (loop [index 0
           px px]
      (if (> index max-index)
        index
        (let [c (nth text index)
              width (.getCharWidth ^com.sun.javafx.font.PrismFontLoader font-loader c jfont)
              new-px (- px width)]
          (if (neg? new-px)
            index
            (recur (inc index)
                   new-px)))))))


(defn- index-for-position [font text px py]
  (assert (some? text) "can't find index for nil text")
  (let [jfont (get-java-font font)
        native-font (com.sun.javafx.scene.text.FontHelper/getNativeFont jfont)]
    (.setContent ^com.sun.javafx.text.PrismTextLayout text-layout text native-font)
    (let [hit-info (.getHitInfo ^com.sun.javafx.text.PrismTextLayout text-layout px py)]
      (.getCharIndex ^com.sun.javafx.scene.text.TextLayout$Hit hit-info)))
  ;; Keeping around public API version
  #_(let [jfont (get-java-font font)

          metrics (.getFontMetrics font-loader jfont)
          line-height (.getLineHeight ^FontMetrics metrics)
          line-no (loop [py py
                         line-no 0]
                    (if (> py line-height)
                      (recur (- py line-height)
                             (inc line-no))
                      line-no))
          lines (clojure.string/split-lines text)]
      (if (>= line-no (count lines))
        (count text)
        (let [line (nth lines line-no)]
          (apply +
                 line-no
                 (index-for-position-line jfont line px)
                 (map count (take line-no lines)))))))


(intern (the-ns 'membrane.ui) 'index-for-position index-for-position)

(defn run-app [app state]
  (assert (var? app))
  (assert (instance? clojure.lang.Atom state))
  (let [renderer
        (fx/create-renderer
         :middleware
         (fx/wrap-map-desc
          (fn [current-state]
            {:fx/type :stage
             :showing true
             :scene {:fx/type :scene
                     :root
                     {:fx/type :group
                      :children [(membrane-component app
                                                     current-state
                                                     #(swap! state %))]}}})))]
    (fx/mount-renderer state renderer)))

(defn -main [& args]
  (let [app-state (atom {:progress 0.3})
        renderer
        (fx/create-renderer
         :middleware
         (fx/wrap-map-desc
          (fn [all-state]
            {:fx/type :stage
             :showing true
             :scene {:fx/type :scene
                     :root {:fx/type :v-box
                            :padding 100
                            :spacing 50
                            :children [(membrane-component (requiring-resolve 'membrane.example.todo/todo-app)
                                                           (:todo-state all-state)
                                                           #(swap! app-state update :todo-state %))
                                       {:fx/type :slider
                                        :pref-width 100
                                        :min 0
                                        :max 1
                                        :value (:progress all-state)
                                        :on-value-changed #(swap! app-state assoc :progress %)}]}}})))]
    (fx/mount-renderer app-state renderer)))


