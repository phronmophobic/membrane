(ns membrane.terminal
  (:require [membrane.skia :as skia]
            [membrane.component :as component
             :refer [defui]]
            [membrane.ui :as ui
             :refer [vertical-layout
                     horizontal-layout
                     button
                     checkbox
                     on]]
            [clojure.core.async :refer [go
                                        put!
                                        chan
                                        <!
                                        timeout
                                        dropping-buffer
                                        promise-chan
                                        close!
                                        alts!
                                        thread]
             :as async])
  (:import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal
           com.googlecode.lanterna.TerminalSize
           com.googlecode.lanterna.input.DefaultKeyDecodingProfile
           com.googlecode.lanterna.input.KeyStroke
           com.googlecode.lanterna.screen.TerminalScreen
           com.googlecode.lanterna.screen.ScreenTextGraphics
           com.googlecode.lanterna.graphics.TextGraphicsWriter

           com.sun.jna.Memory))

;; https://vt100.net/emu/
;; https://vt100.net/emu/dec_ansi_parser
;; http://ascii-table.com/ansi-escape-sequences.php
;; https://en.wikipedia.org/wiki/ANSI_escape_code
;; http://ascii-table.com/ansi-escape-sequences-vt-100.php
;; https://github.com/xtermjs/xterm.js/blob/63fde0355222a18a64332ed0102c8da37b20b7d6/src/Types.d.ts#L82

;; (def term (com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal. ;; (TerminalSize. 80 120)
;;                                                                             ))

;; (def text-graphics (.newTextGraphics term))
;; (def graphics-writer (TextGraphicsWriter. text-graphics))







(comment (def strs (read-to-text-to-str pty )))

#_(.putString graphics-writer o(.replace  (first strs) "\r" "")
            )


(def ab (byte \a))
(def zb (byte \z))
(def Ab (byte \A))
(def Zb (byte \Z))


(defrecord Cursor [row col])
(defn cursor
  ([]
   (Cursor. 0 0))
  ([row]
   (Cursor. row 0))
  ([row col]
   (Cursor. row col)))

(defrecord Cell [c attrs])
(defn cell
  ([]
   (Cell. " " nil))
  ([c]
   (Cell. c nil))
  ([c attrs]
   (Cell. c attrs)))

#_(defrecord TerminalLine [])
(defn term-line [cols]
  (vec (repeat cols (cell))))

(defn init-term
  ([]
   (init-term 40 80))
  ([rows cols]
   {:cursor (cursor)
    :lines (vec (repeat rows (term-line cols)))
    :attrs {}
    :scroll-region [0 rows]
    :size [rows cols]}))

(defn rand-term [rows cols]
  {:cursor (cursor)
   :lines (vec (repeatedly rows
                           #(vec
                             (repeatedly cols
                                        (fn []
                                          (cell (rand-nth (seq "abcdefghijklmnopqrstuvwxyz"))))))))
    :attrs {}
    :size [rows cols]})


(defn set-cursor [ts cur]
  (assoc ts :cursor cur))



(defn set-byte [ts b]
  (let [
        [rows cols] (:size ts)
        [top bottom] (:scroll-region ts)

        {:keys [row col] :as cur} (:cursor ts)

        ts (update ts :cursor
                   (fn [cur]
                     (if (> (inc (:col cur)) cols)
                       (cursor (inc (:row cur)) 0)
                       cur)))
        ts (if (>= (-> ts :cursor :row) bottom)
             (-> ts
                 (update-in [:cursor :row] dec)
                 (update :lines
                         (fn [lines]
                           (vec
                            (concat
                             (subvec lines 0 top)
                             (subvec lines (inc top) bottom)
                             [(term-line cols)]
                             (subvec lines bottom)))
                           #_(conj (subvec lines 1)
                                 (term-line cols)))))
             ts)
        {:keys [row col] :as cur} (:cursor ts)]
    (-> ts
        (update-in [:lines row]
                   (fn [line]
                     (let [col (:col cur)
                           line-length (count line)]
                       (if (> col line-length)
                         (conj (vec line) (cell b (:attrs ts)))

                         (assoc line col (cell b (:attrs ts)))))))
        
        (update-in [:cursor :col]
                   (fn [col]
                     (min (inc col) cols))))
    
    ))


(let [semi (int \;)
      zero (int \0)]
 (defn parse-csi-params [params]
   (loop [params (seq params)
          num nil
          parsed []]
     (if params
       (let [b (first params)]

         (if (= b semi)
           (recur (next params)
                  nil
                  (conj parsed num))
           (recur (next params)
                  (let [current-num (- b zero)]
                    (if num
                      (+ current-num (* 10 num))
                      current-num))
                  parsed)))
       (conj parsed num)))))


(def nlb (byte \newline))
(def rb (byte \return))
(def escapeb (byte 27))

(defn -csi-sgr [ts code]
  (case code

    0 (assoc ts :attrs {})
    1 (assoc-in ts [:attrs :bold] true)
    2 (assoc-in ts [:attrs :dim] true)
    
    3 (assoc-in ts [:attrs :italic] true)


    4 (assoc-in ts [:attrs :underline] true)
    5 (assoc-in ts [:attrs :blink] :slow)
    6 (assoc-in ts [:attrs :rapid] :slow)
    ;; swap foreground and background colors, aka invert
    7 (assoc-in ts [:attrs :inverse] true)

    ;;  	aka Hide, not widely supported.
    8 (assoc-in ts [:attrs :conceal] true)

 	;; aka Strike, characters legible, but marked for deletion.
    9 (assoc-in ts [:attrs :strike] true)

    10 (assoc-in ts [:attrs :font] nil)

    (11 12 13 14 15 16 17 18 19)
    (assoc-in ts [:attrs :font] code)

    
    20 (assoc-in ts [:attrs :fraktur] true)

    ;; double underline or bold off? https://en.wikipedia.org/wiki/Talk:ANSI_escape_code#SGR_21%E2%80%94%60Bold_off%60_not_widely_supported
    21 (assoc-in ts [:attrs :underline] 2)
    22 (assoc-in ts [:attrs :dim] true)
    23 (assoc-in ts [:attrs :italic] false)
    24 (assoc-in ts [:attrs :underline] false)
    25 (assoc-in ts [:attrs :blink] false)
    27 (assoc-in ts [:attrs :inverse] false)
    28 (assoc-in ts [:attrs :conceal] nil)
    29 (assoc-in ts [:attrs :strike] false)

    30 (assoc-in ts [:attrs :fg] :black)
    31 (assoc-in ts [:attrs :fg] :red)
    32 (assoc-in ts [:attrs :fg] :green)
    33 (assoc-in ts [:attrs :fg] :yellow)
    34 (assoc-in ts [:attrs :fg] :blue)
    35 (assoc-in ts [:attrs :fg] :magenta)
    36 (assoc-in ts [:attrs :fg] :cyan)
    37 (assoc-in ts [:attrs :fg] :white)
    39 (update ts :attrs dissoc :fg)


    40 (assoc-in ts [:attrs :bg] :black)
    41 (assoc-in ts [:attrs :bg] :red)
    42 (assoc-in ts [:attrs :bg] :green)
    43 (assoc-in ts [:attrs :bg] :yellow)
    44 (assoc-in ts [:attrs :bg] :blue)
    45 (assoc-in ts [:attrs :bg] :magenta)
    46 (assoc-in ts [:attrs :bg] :cyan)
    47 (assoc-in ts [:attrs :bg] :white)
    
    49 (update ts :attrs dissoc :bg)

    51 (assoc-in ts [:attrs :framed] true)

    90 (assoc-in ts [:attrs :fg] :bright-black)
    91 (assoc-in ts [:attrs :fg] :bright-red)
    92 (assoc-in ts [:attrs :fg] :bright-green)
    93 (assoc-in ts [:attrs :fg] :bright-yellow)
    94 (assoc-in ts [:attrs :fg] :bright-blue)
    95 (assoc-in ts [:attrs :fg] :bright-magenta)
    96 (assoc-in ts [:attrs :fg] :bright-cyan)
    97 (assoc-in ts [:attrs :fg] :bright-white)

    ;; else
    (throw (Exception. (str "unknown sgr " code)) )
    

    ))
(defn csi-sgr [ts params]
  (if-let [code (first params)]
    (case code
      144
      ;; 62
      ;; <ESC>[4:0m  # this is no underline
      ;; <ESC>[4:1m  # this is a straight underline
      ;; <ESC>[4:2m  # this is a double underline
      ;; <ESC>[4:3m  # this is a curly underline
      ;; <ESC>[4:4m  # this is a dotted underline (not implemented in kitty)
      ;; <ESC>[4:5m  # this is a dashed underline (not implemented in kitty)
      ;; <ESC>[4m    # this is a straight underline (for backwards compat)
      ;; <ESC>[24m   # this is no underline (for backwards compat)
      ;; underline and stuff
      ts

      
      48  (assoc-in ts [:attrs :bg] [:custom (rest params)] )
      38  (assoc-in ts [:attrs :fg] [:custom (rest params)] )

      ;; else
      (recur (-csi-sgr ts code) (rest params)))
    ts)

)

(defn csi-set-mode [ts {:keys [params intermediate final] :as csi-command}]
  (cond
    ;; [?1034h
    ;; https://unix.stackexchange.com/questions/111541/passing-escape-sequences-to-shells-within-ansi-term-in-emacs
    ;; https://invisible-island.net/ncurses/man/terminfo.5.html#h3-Miscellaneous
    ;; set meta mode!
    (= params [63 49 48 51 52])
    ;; settings meta mode!!
    ts


    ;; [?1049h
    ;; https://invisible-island.net/xterm/xterm.faq.html
    ;; search for 1049
    (= params [63 49 48 52 57])
    (assoc (init-term)
           :alternate-screen ts)

    ;; [?12h
    ;; https://github.com/JavaQualitasCorpus/netbeans-7.3/blob/66a5fed947b5bd62f3ef968a67ed123c552abfa8/lib.terminalemulator/doc-files/sequences
    ;; blink cursor?
    (= params [63 49 50])
    ts

    ;; [?12;25h
    ;; cursor visible
    (= params [63 49 50 59 50 53])
    ;; make the cursor visible!
    ts


    ;; [?1h
    ;; http://ascii-table.com/ansi-escape-sequences-vt-100.php
    (= params [63 49])
    ;; cursor is now application!
    ts

    ;; [34hor [?25h
    (or (= params [51 52])
        (= params [63 50 53]))
    ;; cursor is normal now
    ts

    ;; [?2004h
    ;; turn on bracketed paste mode. whatever that means
    (= params [63 50 48 48 52])
    ts
    
    :else
    (throw (Exception. (str "unknown csi set mode: " csi-command))))
  )

(defonce csi-commands (atom {}))
(defmacro defn-csi [fname [csi-letter csi-num] args & body]
  (assert (= csi-num (int (.charAt csi-letter 0)))
          (format "Csi letter doesn't match csi num: %s(%d) != %d" csi-letter (int (.charAt csi-letter 0)) csi-num))
  (let [params# (gensym "params-")
        raw-fn-sym (symbol (str fname "-raw"))]
    `(do
       (defn ~fname [~'ts ~@(for [arg args]
                              (if (map? arg)
                                (-> arg first first)
                                arg))
                     ;;~'{:keys [params intermediate final] :as csi-command}
                     ]
         ~@body)
     
       (defn ~raw-fn-sym [~'ts ~'{:keys [params intermediate final] :as csi-command}]
         (let [~params# (parse-csi-params ~'params)]
           (~fname ~'ts ~@(let []
                            (for [[i arg] (map-indexed vector args)
                                  :let [default (if (map? arg)
                                                  (-> arg first second)
                                                  0)]]
                              `(or (nth ~params# ~i ~default) ~default))))))
       (swap! csi-commands assoc ~csi-num ~raw-fn-sym)
       (var ~fname)))
  
  )


(defn-csi insert-blank-chars ["@" 64] [{num 1}]
  (let [cur (:cursor ts)
        {:keys [row col]} cur
        [rows cols] (:size ts)]
    (update-in ts [:lines row]
               (fn [line]
                 (let [line-count (count line)
                       end1 (min col line-count cols)
                       blank-count (min num (- cols end1))

                       chars-left (- cols (+ end1 blank-count))
                       end2 (min line-count (+ end1 chars-left))]

                   (vec
                    (concat
                     (subvec line 0 end1)
                     (repeat blank-count (cell " "))
                     (subvec line
                             end1
                             end2))))))))

   ;; * CSI Ps c  Send Device Attributes (Primary DA).
   ;; *     Ps = 0  or omitted -> request attributes from terminal.  The
   ;; *     response depends on the decTerminalID resource setting.
   ;; *     -> CSI ? 1 ; 2 c  (``VT100 with Advanced Video Option'')
   ;; *     -> CSI ? 1 ; 0 c  (``VT101 with No Options'')
   ;; *     -> CSI ? 6 c  (``VT102'')
   ;; *     -> CSI ? 6 0 ; 1 ; 2 ; 6 ; 8 ; 9 ; 1 5 ; c  (``VT220'')
   ;; *   The VT100-style response parameters do not mean anything by
   ;; *   themselves.  VT220 parameters do, telling the host what fea-
   ;; *   tures the terminal supports:
   ;; *     Ps = 1  -> 132-columns.
   ;; *     Ps = 2  -> Printer.
   ;; *     Ps = 6  -> Selective erase.
   ;; *     Ps = 8  -> User-defined keys.
   ;; *     Ps = 9  -> National replacement character sets.
   ;; *     Ps = 1 5  -> Technical characters.
   ;; *     Ps = 2 2  -> ANSI color, e.g., VT525.
   ;; *     Ps = 2 9  -> ANSI text locator (i.e., DEC Locator mode).
   ;; *
   ;; * @vt: #Y CSI DA1   "Primary Device Attributes"     "CSI c"  "Send primary device attributes."
   ;; *
   ;; *
   ;; * TODO: fix and cleanup response
(declare write-ch)
(defn-csi send-device-attributes ["c" 99] [ignored]
  (async/put! write-ch (into [0x1b] (.getBytes "[?1;2c")))
  ts)

;; d
;; vertical position absolute
;; 100
(defn-csi vertical-position-absolute ["d" 100] [{new-row 1}]
  (let [new-row (dec new-row)]
    (-> ts
        (assoc-in [:cursor :row] new-row))))

   ;; * CSI Ps A
   ;; * Cursor Up Ps Times (default = 1) (CUU).
   ;; *
   ;; * @vt: #Y CSI CUU   "Cursor Up"   "CSI Ps A"  "Move cursor `Ps` times up (default=1)."
   ;; * If the cursor would pass the top scroll margin, it will stop there.
(defn-csi cursor-up-times ["A" 65] [{num 1}]
  (let [[top bottom] (:scroll-region ts)]
    (update-in ts [:cursor :row]
               (fn [row]
                 (max top (- row num))))))


      ;; * CSI Ps C
      ;; * Cursor Forward Ps Times (default = 1) (CUF).
      ;; *
      ;; * @vt: #Y CSI CUF   "Cursor Forward"    "CSI Ps C"  "Move cursor `Ps` times forward (default=1)."
(defn-csi cursor-forward ["C" 67] [{num 1}]
  (let [[rows cols] (:size ts)]
    (update-in ts [:cursor :col]
               (fn [col]
                 (min (+ col num)
                      cols)))))


      ;; H
      ;; cursor position
(defn-csi cursor-position ["H" 72] [{row 1} {col 1}]
  (assoc ts :cursor (cursor (dec row) (dec col))))

;; J
;; erase in display
(defn-csi erase-in-display ["J" 74] [{mode 0}]
  (case mode
    0
    (let [{:keys [row col]} (:cursor ts)
          lines (:lines ts)
          [rows cols] (:size ts)
          line (nth lines row)
          line (vec
                (concat
                 (subvec line 0 col)
                 (repeat (- cols col) (cell))))
          
          lines (vec
                 (concat
                  (subvec lines 0 row)
                  [line]
                  (repeat (- rows row 1) (term-line cols))))]
      (assoc ts :lines lines))
    
    1 (throw (Exception. (str "unsupported J option of clearning to beginning of screen to cursor.")))

    2 (let [[rows cols] (:size ts)]
        (assoc ts
               :cursor (cursor)
               :lines (vec (repeat rows (term-line cols)))))

    ;; erase scrollback
    3 ts))

;; K
;; erase in line
;; 75
;; * | Ps | Effect                                                   |
;; * | -- | -------------------------------------------------------- |
;; * | 0  | Erase from the cursor through the end of the row.        |
;; * | 1  | Erase from the beginning of the line through the cursor. |
;; * | 2  | Erase complete line.                                     |
(defn-csi erase-in-line ["K" 75] [{mode 0}]
  (case mode
    0 (let [cur (:cursor ts)
            col (:col cur)
            cols (-> ts :size second)]
        (update-in ts [:lines (:row cur)]
                   (fn [line]
                     (vec
                      (concat
                       (subvec line 0 col)
                       (repeat (- cols col) (cell)))))))

    1 (throw (Exception. (str "unsupported K option of erasing from beginning of line")))

    2 (assoc-in ts [:lines (get-in ts [:cursor :row])] [])))


;; L
;; 76
;; * CSI Ps L
;; * Insert Ps Line(s) (default = 1) (IL).
;; *
;; * @vt: #Y CSI IL  "Insert Line"   "CSI Ps L"  "Insert `Ps` blank lines at active row (default=1)."
;; * For every inserted line at the scroll top one line at the scroll bottom gets removed.
;; * The cursor is set to the first column.
;; * IL has no effect if the cursor is outside the scroll margins.
(defn-csi insert-line ["L" 76] [{num 1}]
  (let [cur (:cursor ts)
        {:keys [row col]} cur
        [rows cols] (:size ts)
        [top bottom] (:scroll-region ts)]
    (-> ts
        (update :lines
                (fn [lines]
                  (let [insert-count (if (<= (+ num row)
                                             bottom)
                                       num
                                       (- bottom row))]
                    (vec
                     (concat
                      (subvec lines 0 row)
                      (repeat insert-count (term-line cols))
                      (subvec lines row (max row
                                             (- bottom insert-count)))
                      (subvec lines bottom))))))
        (update :cursor assoc :col 0))))


  ;;  * CSI Ps M
  ;;  * Delete Ps Line(s) (default = 1) (DL).
  ;;  *
  ;;  * @vt: #Y CSI DL  "Delete Line"   "CSI Ps M"  "Delete `Ps` lines at active row (default=1)."
  ;;  * For every deleted line at the scroll top one blank line at the scroll bottom gets appended.
  ;;  * The cursor is set to the first column.
  ;;  * DL has no effect if the cursor is outside the scroll margins.
(defn-csi delete-line ["M" 77] [{num 1}]
  (let [{:keys [row col]} (:cursor ts)
        [rows cols] (:size ts)
        [top bottom] (:scroll-region ts)]
    (-> ts
        (update :lines (fn [lines]
                         (let [last-index (min bottom (+ row num))]
                           (vec
                            (concat
                             (subvec lines 0 row)
                             (subvec lines row last-index)
                             (repeat (- bottom last-index) (term-line cols))
                             (subvec lines bottom))))))
        (assoc-in [:cursor col] 0))))


   ;; * CSI Ps n  Device Status Report (DSR).
   ;; *     Ps = 5  -> Status Report.  Result (``OK'') is
   ;; *   CSI 0 n
   ;; *     Ps = 6  -> Report Cursor Position (CPR) [row;column].
   ;; *   Result is
   ;; *   CSI r ; c R
   ;; * CSI ? Ps n
   ;; *   Device Status Report (DSR, DEC-specific).
   ;; *     Ps = 6  -> Report Cursor Position (CPR) [row;column] as CSI
   ;; *     ? r ; c R (assumes page is zero).
   ;; *     Ps = 1 5  -> Report Printer status as CSI ? 1 0  n  (ready).
   ;; *     or CSI ? 1 1  n  (not ready).
   ;; *     Ps = 2 5  -> Report UDK status as CSI ? 2 0  n  (unlocked)
   ;; *     or CSI ? 2 1  n  (locked).
   ;; *     Ps = 2 6  -> Report Keyboard status as
   ;; *   CSI ? 2 7  ;  1  ;  0  ;  0  n  (North American).
   ;; *   The last two parameters apply to VT400 & up, and denote key-
   ;; *   board ready and LK01 respectively.
   ;; *     Ps = 5 3  -> Report Locator status as
   ;; *   CSI ? 5 3  n  Locator available, if compiled-in, or
   ;; *   CSI ? 5 0  n  No Locator, if not.
   ;; *
   ;; * @vt: #Y CSI DSR   "Device Status Report"  "CSI Ps n"  "Request cursor position (CPR) with `Ps` = 6."
(defn-csi device-status ["n" 110] [num & args]
  (case num
    5 (async/put! write-ch (into [0x1b] (.getBytes "[0n")))
    6 (let [{:keys [row col]} (:cursor ts)]
        (async/put! write-ch (into [0x1b] (.getBytes (format "[%d;%dR" (inc row) (inc col))))))

    ;;else
    (throw (Exception. (str "Unknown device status request " num " "  args )))
    )
  ts)



      ;; r
      ;; * CSI Ps ; Ps r
      ;; *   Set Scrolling Region [top;bottom] (default = full size of win-
      ;; *   dow) (DECSTBM).
      ;; *
      ;; * @vt: #Y CSI DECSTBM "Set Top and Bottom Margin" "CSI Ps ; Ps r" "Set top and bottom margins of the viewport [top;bottom] (default = viewport size)."
      ;; 114
(defn-csi set-scrolling-region ["r" 114] [{top 1} {bottom nil}]
  (let [bottom (if (nil? bottom)
                 (-> (:size ts) :rows)
                 bottom)]
    ;; (assert false "setting scroll region not supported")
    (assoc ts :scroll-region [(dec top) bottom])))


;; * CSI Ps P
;; * Delete Ps Character(s) (default = 1) (DCH).
;; *
;; * @vt: #Y CSI DCH   "Delete Character"  "CSI Ps P"  "Delete `Ps` characters (default=1)."
;; * As characters are deleted, the remaining characters between the cursor and right margin move to the left.
;; * Character attributes move with the characters. The terminal adds blank characters at the right margin.
(defn-csi delete-character ["P" 80] [{num 1}]
  (let [{:keys [row col] :as cur} (get ts :cursor)
        [_ cols] (:size ts)]
    (update-in ts [:lines row]
               (fn [line]
                 (let [line-count (count line)]
                   (vec
                    (concat
                     (subvec line 0 col)
                     (subvec line (min (+ col num)
                                       cols))
                     (repeat (if (<= (+ col num) cols)
                               num
                               (- cols col))
                             (cell)))))))))



(defn -csi-command [ts {:keys [params intermediate final] :as csi-command}]

  (let [command-byte (bit-and 0xff (long final))]
    
    (case command-byte
      ;; h
      104
      (csi-set-mode ts csi-command)


      
      ;; "m" , sgr
      109
      (csi-sgr ts (parse-csi-params params))

      ;; "l" reset mode
      108
      (cond

        (= params [63 49])
        ;; Set cursor key to cursor
        ;; http://ascii-table.com/ansi-escape-sequences-vt-100.php
        ts

        (= params [63 49 50])
        ;; set mode to something almost assuredly bogus
        ts

        ;; [34l
        (= params [51 52])
        ;; reset cursor mode visible
        ts

        ;; [?1049
        ;; https://invisible-island.net/xterm/xterm.faq.html
        ;; reset alternate screen mode?
        (= params [63 49 48 52 57])
        #_ts (:alternate-screen ts)

        (or (= params [51 52])
            (= params [63 50 53]))
        ;; normal cursor
        ts

        ;; turn off bracketed paste mode. whatever that means
        (= params [63 50 48 48 52])
        ts

        :else
        (throw (Exception. (str "unsupported l command: " command-byte " " (pr-str (char final)) " " csi-command)))
        )

      

      ;; t
      ;; https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h2-Functions-using-CSI-_-ordered-by-the-final-character_s_
      116
      (let [params (parse-csi-params params)]
        (if (#{22 23} (first params))
          ;; saving and restoring window title and icon
          ts

          ;;else
          (throw (Exception. (str "unsupported t command: " command-byte " " (pr-str (char final)) " " csi-command)))))


      ;;else
      (if-let [handler (get @csi-commands final)]
        (handler ts csi-command)
        (throw (Exception. (str "unknown csi command: " command-byte " " (pr-str (char final)) " " csi-command))))))
  )

(defn csi-command [ts csi-command]
  (dissoc (-csi-command ts csi-command)
          :csi
          :next-process))




(defn csi-parse-process-byte [ts b]

  (cond

    (and (>= b 0x30)
         (<= b 0x3f))
    (update-in ts [:csi :params] (fn [params]
                                   (conj (or params [])
                                         b)))

    (and (>= b 0x20)
         (<= b 0x2f))
    (update-in ts [:csi :intermediate] (fn [intermediate]
                                         (conj (or intermediate [])
                                               b)))

    (and (>= b 0x40)
         (<= b 0x7e))
    (let [csi (get ts :csi)]
      (csi-command ts (assoc (:csi ts)
                             :final b)))

    (= 10 b)
    (dissoc ts :next-process)
    
    :else
    (throw (Exception. (str "could not parse csi: " b (pr-str (char b)) (:csi ts))))
    
    )

  
  )

(defn osc-process [ts osc]
  ;; (println "osc " (pr-str osc))
  ts)

(defn osc-parse-process-byte [ts b]
  ;; stop on BEL (0x07)
  ;; should also stop on ST?
  (if (= 7 b)
    (dissoc (osc-process ts (:osc ts))
            :osc
            :next-process)
    (update ts :osc #(conj (or [] %) b))))

(defn escape-process-byte [ts b]
  (let [ts (dissoc ts :next-process)]
    (case (bit-and 0xff (long b))
      ;; [ CSI sequence
      91
      (assoc ts :next-process csi-parse-process-byte)

      ;; ESC=
      ;; https://vt100.net/docs/vt510-rm/DECKPAM.html
      ;; enables the numeric keypad to send application sequences to the host. 
      61
      ts

      ;; ESC>
      ;; keypad numeric mode
      62
      ts

      ;; ESC ] OSC command
      93
      (assoc ts :next-process osc-parse-process-byte)

      ;; ESCM
      77
      (let [cur (:cursor ts)]
        (when (zero? (:row cur))
          (throw (Exception. "Unsupported ESCM at top of line. need to scroll down")))
        (update-in ts [:cursor :row] dec))

      (throw (Exception. (str "could not Escape process: " b "  " (pr-str (char b)))))))
  )




(defn process-newline [ts]
  (let [cur (:cursor ts)
        row (:row cur)
        [rows cols] (:size ts)
        [top bottom] (:scroll-region ts)]
    (if (< (inc row) bottom)
      (assoc ts :cursor (update cur :row inc))
      (update ts :lines
              (fn [lines]
                (vec
                 (concat
                  (subvec lines 0 top)
                  (subvec lines (inc top) bottom)
                  [(term-line cols)]
                  (subvec lines bottom)))
                #_(conj (subvec lines 1)
                      (term-line cols))))))
  )

(defn process-byte [ts b]
  
  (if-let [next-process (:next-process ts)]
    (next-process ts b)
    (cond
      (< b 0)
      ;; ignore for now
      ts
      
      (= b rb)
      (update ts :cursor (fn [cur]
                           (assoc cur :col 0)))

      (= b nlb)
      (process-newline ts)
      

      (= b 7)
      ;; bell
      ts

      ;; tab
      (= b 9)
      (update-in ts [:cursor :col]
                 (fn [col]
                   (let [tab-width 8]
                     (min (second (:size ts))
                          (+ col (- tab-width (mod col tab-width)))))))

      ;; backspace
      (= b 8)
      (update-in ts [:cursor :col]
                 (fn [col]
                   (max 0 (dec col))))

      (= b escapeb)
      (assoc ts :next-process escape-process-byte)

      (and (>= b 32)
           (<= b 127))
      (set-byte ts (char b))

      :else (throw (Exception. (str  "could not process: " (bit-and 0xff (long b)) " " (pr-str (char (bit-and 0xff (long b))))))))))

(def term-font (ui/font "Menlo.ttc" 11))

(defn term-color [color]
  (case color
    :white           [1     1     1   ]
    :black           [0     0     0   ]
    :red             [0.76  0.21  0.13]
    :green           [0.14  0.74  0.14]
    :yellow          [0.68  0.68  0.15]
    :blue            [0.29  0.18  0.88]
    :magenta         [0.83  0.22  0.83]
    :cyan            [0.20  0.73  0.78]
    :bright-black    [0.46  0.46  0.46]
    :bright-red      [0.91  0.28  0.34]
    :bright-green    [0.09  0.78  0.05]
    :bright-yellow   [0.98  0.95  0.65]
    :bright-blue     [0.23  0.47  1   ]
    :bright-magenta  [0.71  0     0.62]
    :bright-cyan     [0.38  0.84  0.84]
    :bright-white    [0.95  0.95  0.95]

    ;;else
    (case (first color)

      :custom
      (let [[_ [five num]] color]
        (if (< num 256)
          (case num
            0 (term-color :black)
            1 (term-color :red)
            2 (term-color :green)
            3 (term-color :yellow)
            4 (term-color :blue)
            5 (term-color :magenta)
            6 (term-color :cyan)
            7 (term-color :white)

            8  (term-color :bright-black)
            9  (term-color :bright-red)
            10 (term-color :bright-green)
            11 (term-color :bright-yellow)
            12 (term-color :bright-blue)
            13 (term-color :bright-magenta)
            14 (term-color :bright-cyan)
            15 (term-color :bright-white)

            ;; else
            (cond

              ;; const v = [0x00, 0x5f, 0x87, 0xaf, 0xd7, 0xff];
              ;; for (let i = 0; i < 216; i++) {
              ;;          const r = v[(i / 36) % 6 | 0];
              ;;          const g = v[(i / 6) % 6 | 0];
              ;;          const b = v[i % 6];
              ;;          colors.push({
              ;;                       css: channels.toCss(r, g, b),
              ;;                       rgba: channels.toRgba(r, g, b)
              ;;                       });
              ;;          }
              (and (>= num 16)
                   (<= num 231))
              (let [num (- num 16)
                    v [0x00, 0x5f, 0x87, 0xaf, 0xd7, 0xff]
                    r (nth v (int (mod (/ num 36.0) 6)))
                    g (nth v (int (mod (/ num 6.0) 6)))
                    b (nth v (int (mod num 6.0)))]
                [(/ r 255.0)
                 (/ g 255.0)
                 (/ b 255.0)])

              (and (>= num 232)
                   (<= num 255))
              (let [gray (/ (+ 8 (* 10 (- num 232))) 255.0)]
                [gray gray gray])

              :else
              (throw (Exception. (str "Unknown color:" color))))
            )
          
          ;;else
          (throw (Exception. (str "Unknown color:" color)))
          ))

      ;;else
      (throw (Exception. (str "Unknown color:" color))))
    ))




(defui terminal-line [& {:keys [tline]}]
  (vec
   (for [[j pos] (map-indexed vector tline)
         :let [c (:c pos)]
         :when (not= " " (:c pos))
         :let [attrs (:attrs pos)
               fg (:fg attrs)
               bg (:bg attrs)
               lbl (ui/label c term-font)
               lbl (if fg
                     (ui/with-color (term-color fg)
                       lbl)
                     lbl)]]
     (ui/translate (* j 7) 0
                   [(when bg
                      (ui/filled-rectangle (term-color bg)
                                           7 14))
                    lbl])
     )))
  

(defui terminal [& {:keys [term]}]
  (into
   [(let [{:keys [row col]} (:cursor term)]
      (ui/translate (* col 7) (* row 10)
                    (ui/filled-rectangle [0.5725490196078431
                                          0.5725490196078431
                                          0.5725490196078431
                                          0.4]
                                         7 14)))]
   #_(for [[i row] (map-indexed vector (:lines term))]
       (ui/translate 0 (* i 10)
                     (ui/label (apply str (map :c row))
                               fnt))
       )
   (for [[i tline] (map-indexed vector (:lines term))]
     (ui/translate 0 (* i 10)
                   (skia/->Cached
                    (terminal-line :tline tline))))))



(def history (atom []))
(def term (atom
           (-> (init-term)
               (process-byte (byte \a))
               (set-cursor (cursor 0 0))
               (process-byte (byte \w))
               (process-byte (byte \newline))
               (process-byte (byte \a))

               )))

(declare writec-str pty writec-bytes)
(def write-ch (chan 100))
(def write-loop
  (go
    (loop [bts (<! write-ch)]
      (writec-bytes pty bts)
      (recur (<! write-ch)))))
#_(writec-bytes pty [97])


(let [writec-bytes (fn [pty bts]
                     (async/put! write-ch bts))
      writec-str (fn [pty s]
                   (let [buf (.getBytes s "utf-8")]
                     (async/put! write-ch buf)))]
  (defui test-term-ui [& {:keys [hindex term history]}]
    (vertical-layout
     (horizontal-layout
      (button "clear"
              (fn []
                [[:set $hindex nil]]))
      (button "<"
              (fn []
                [[:update $hindex
                  (fn [hindex]
                    (dec (or hindex (dec (count history)))))]
                 ]))
      (button ">"
              (fn []
                [[:update $hindex
                  (fn [hindex]
                    (when hindex
                      (min (inc hindex)
                           (dec (count history)))))]]))
      (ui/label (or hindex (count history)))
)

     (on
      :key-event
      (fn [key scancode action mods]

        (when (#{:press :repeat} action)
          (case key
            ;; backspace
            259 (writec-bytes pty [0x7f])

            ;; escape
            256 (writec-bytes pty [0x1b])

            ;; tab
            258 (writec-bytes pty [(int \tab)])

            
            262 ;; right
            (writec-bytes pty (map int [033 \[ \C]))

            #_left 263
            (writec-bytes pty (map int [033 \[ \D]))
            
            264 (writec-bytes pty (map int [033 \[ \B]))
            ;; down

            ;; up
            265
            (writec-bytes pty (map int [0x1b \[ \A]))

            ;; default
            nil
            )


          (when (not (zero? (bit-and skia/GLFW_MOD_CONTROL mods)))
            (when (< key 128)
              (let [b (inc (- key (int \A) ))]
                (writec-bytes pty [b]))))

          (when (not (zero? (bit-and skia/GLFW_MOD_ALT mods)))
            (when (< key 128)
              (let [key (if (not (zero? (bit-and skia/GLFW_MOD_SHIFT mods)))
                          (- key (- (int \A) (int \a)))
                          key)]
                (writec-bytes pty [0x1b key]))))
          )
        nil
        )
      :key-press
      (fn [s]
        (when-let [s (if (keyword? s)
                       (case s 
                         :enter "\r"

                         ;; default
                         nil)
                       s)]
          (let [bts (.getBytes s)]
            (when (pos? (first bts))
              (async/put! write-ch bts)))
          
          #_(writec-bytes pty )
          #_(writec-str pty s))
        
        nil)
      
      (terminal :term (if hindex
                      (-> history (nth hindex) second)
                      term))
      #_(skia/->Cached
         (draw-term (if hindex
                      (-> history (nth hindex) second)
                      term)))))
    ))

(defn initial-term-state []
  {:term (init-term 40 80)
   :history []
   :hindex nil})
(defonce test-term-state (atom (initial-term-state)))

(defn run-test-term-ui []
  (component/run-ui #'test-term-ui test-term-state)
  nil)

(defn cursor-check! [term]
  (let [[rows cols] (:size term)
        {:keys [col row]} (:cursor term)]
    (assert (<= row rows) (format "Row out of bounds %d > %d" row rows))
    (assert (<= col cols) (format "Col out of bounds %d > %d" col cols))
    term))

(defn size-check! [term]
  (let [[rows cols] (:size term)]
   (assert (= (count (:lines term))
              rows)
           (format "Invariant of num rows not changing failed. %d -> %d" rows (count (:lines term))))
   (assert (every? #(= cols (count %))
                   (:lines term))
           (let [col-counts
                 (for [[i line] (map-indexed vector (:lines term))
                       :let [cnt (count line)]
                       :when (not= cnt cols)]
                   [i cnt])]
             (format "Invariand of num cols not changing failed. %d -> %s %s"
                     cols
                     (pr-str (vec (take 5 col-counts)))
                     (when (> (count col-counts) 5)
                       "..."))))
   term))

(declare read-to-seq pty writec-str readc-byte)

(defn update-term! []
  (let [b (readc-byte pty)]
    (let [
          tt-state @test-term-state
          term (:term tt-state)
          history (:history tt-state)
          ;; _ (printf "processing %d 0x%x %s \n" b b (pr-str (char (bit-and 0xff (long b)))))
          new-term (try
                     (process-byte term b)
                     (catch Exception e
                       ;; (clojure.pprint/pprint term)
                       (throw e)))]
      (size-check! new-term)
      (cursor-check! new-term)
      (swap! test-term-state assoc :term new-term)
      (swap! test-term-state update :history conj [b new-term])))

  (skia/glfw-post-empty-event)
  )

(defonce running (atom false))

(defn run-term! []
  (reset! running true)
  (reset! test-term-state (initial-term-state))
  (let [[rows cols] (:size (:term @test-term-state))]
    (def pty (skia/fork-pty 40 80)))
  ;; (writec-str pty "vim\n")
  (try
    (while @running
      (update-term!))
    #_(while @running
     (clojure.core/let
         [threadmx-bean__16420__auto__
          (java.lang.management.ManagementFactory/getThreadMXBean)
          before-time__16421__auto__
          (.getCurrentThreadUserTime threadmx-bean__16420__auto__)
          ret__16422__auto__
          (do (update-term!))
          after-time__16423__auto__
          (.getCurrentThreadUserTime threadmx-bean__16420__auto__)
          diff (clojure.core//
                (clojure.core/-
                 after-time__16423__auto__
                 before-time__16421__auto__)
                1000000.0)]
       (when (> diff 0.1)
           (clojure.core/println
            "timing "
            "((update-term!))"
            diff))
         ret__16422__auto__))

    (finally
      (reset! running false)))
  
  )


#_(defn draw-term [term]
    (skia/->Cached
     (let [ts (.getTerminalSize term)
           row-count (.getRows ts)
           col-count (.getColumns ts)
        
           rows (volatile! (transient []))]
       (.forEachLine term 0 row-count
                     (reify com.googlecode.lanterna.terminal.virtual.VirtualTerminal$BufferWalker
                       (onLine [this row buffer-line]
                         (vswap! rows
                                 conj!
                                 (ui/translate 0 (* row 16)
                                               (ui/label (String.
                                                          (into-array
                                                           Character/TYPE
                                                           (for [i (range col-count)
                                                                 :let [tc (.getCharacterAt buffer-line i)]]
                                                             (.getCharacter tc))))
                                                         (ui/font "Menlo.ttc" 14))))
                         )))
       (persistent! @rows))))

;; tw.putString("\033[m");

;; String[] cmd = { "/bin/sh", "-l" };
;; // The initial environment to pass to the PTY child process...
;; String[] env = { "TERM=xterm" };


#_(.start
   (java.lang.Thread.
    (fn []
      (def pty (skia/fork-pty 100 40))
      (println (readc pty)))))

(def clib (com.sun.jna.NativeLibrary/getInstance "c"))

(def size-t-size
  com.sun.jna.Native/SIZE_T_SIZE)
(let [[coerce->size_t SIZE_T]
      (cond
        (= size-t-size (com.sun.jna.Native/getNativeSize Short/TYPE))
        [short Short/TYPE]

        (= size-t-size (com.sun.jna.Native/getNativeSize Integer/TYPE))
        (int Integer/TYPE)
        
        (= size-t-size (com.sun.jna.Native/getNativeSize Long/TYPE))
        [long Long/TYPE]

        :else
        (throw (Exception. "Can't match size_t size.")))]
  (def coerce->size_t coerce->size_t)
  (def SIZE_T SIZE_T))

(if
    clib
    (clojure.core/let
        [cfn17659 (.getFunction clib "read")]
        (clojure.core/defn
          _read
          [fd buf nbyte]
          (.invoke cfn17659 SIZE_T (clojure.core/to-array [fd buf nbyte]))))
    (clojure.core/defn
      _read
      [fd buf nbyte]
      (throw
       (java.lang.Exception. (clojure.core/str "read" " not loaded.")))))
(defn readc [fd mem]
  (assert (instance? Integer fd) "Invalid file descriptor.")
  (let [bytes-read (_read fd mem (coerce->size_t (.size mem)))]
    bytes-read))

(if clib
  (clojure.core/let
      [cfn17682 (.getFunction clib "write")]
    (clojure.core/defn
      _write
      [fd buf nbyte]
      ;; (println "writing " (seq buf) " " (type buf) nbyte)
      (.invoke cfn17682 SIZE_T (clojure.core/to-array [fd buf nbyte]))))
  (clojure.core/defn
    _write
    [fd buf nbyte]
    (throw
     (java.lang.Exception.
      (clojure.core/str "write" " not loaded.")))))
(defn writec [fd mem len]
  (assert (instance? Integer fd) "Invalid file descriptor.")
  (_write fd mem (coerce->size_t len)))


(defn print-readc [pty]
  (let [mem (Memory. 1024)]
    (loop [r (readc pty mem)]
      (when (> r 0)
        (do (doseq [i (range r)]
              (let [b (.getByte mem i)]
                (println "read: "i ", " (char b) ", " b)))
            (recur (readc pty mem)))))))

(defn writec-bytes [pty bytes]
  (let [buf (byte-array bytes)]
    (writec pty buf (alength buf))))

(defn writec-str [pty s]
  (let [buf (.getBytes s "utf-8")]
    (writec pty buf (alength buf))))


(defn read-to-text-graphics [pty gw]
  (let [mem (Memory. 1024)]
    (loop [r (readc pty mem)]
      (when (> r 0)
        (let [bytes (make-array Byte/TYPE r)]
          (.read mem 0 bytes 0 r)
          (let [s (String. (into-array Character/TYPE
                                       (for [b  bytes
                                             :let [c (char (bit-and 0xff b))]
                                             ;;:when (not= c \return)
                                             ]
                                         (do
                                           (println (pr-str c) (= c \return))
                                           c))))]
            (println s)
            (.putString gw s))
          (recur (readc pty mem)))))))

(defn read-to-seq [pty]
  (let [mem (Memory. 1024)]
    (loop [r (readc pty mem)
           bs []]
      (if (> r 0)
        (recur (readc pty mem)
               (loop [i 0
                      bs bs]
                 (if (>= i r)
                   bs 
                   (recur (inc i)
                          (conj bs (.getByte mem i))))))
        bs))))

(let [buf (Memory. 1)]
  (defn readc-byte [pty]
    (readc pty buf)
    (.getByte buf 0)))

(defn read-to-text-to-str [pty]
  (let [mem (Memory. 1024)]
    (loop [r (readc pty mem)
           strs []]
      (if (> r 0)
        (let [bytes (make-array Byte/TYPE r)]
          (.read mem 0 bytes 0 r)
          (let [s (String. (into-array Character/TYPE
                                       (for [b  bytes
                                             :let [c (char (bit-and 0xff b))]
                                             ;;                                             :when (not= c \return)
                                             ]
                                         c)))]
            (recur (readc pty mem) (conj strs s))))
        strs))))




(defn -debug-ui []
  (let [term (:term @test-term-state)
        attrs (-> term
                  :lines
                  flatten
                  (->> (remove #{(cell) (cell \space)}))
                  (->> (take 100)))]
    (ui/label (pr-str (:scroll-region term)))
    (ui/label (with-out-str
                (clojure.pprint/pprint attrs)))))

(defn debug-ui []
  (skia/run #'-debug-ui))


#_(def testui (test-term-ui @test-term-state))
