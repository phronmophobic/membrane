(ns membrane.gol
  (:require [membrane.ui :as ui]
            [membrane.java2d :as backend])
  (:import java.awt.event.WindowEvent))



(def state-atm
  (atom
   {:board #{[1 0] [1 1] [1 2]}}))

(defn neighbours [[x y]]
  (for [dx [-1 0 1] dy (if (zero? dx) [-1 1] [-1 0 1])] 
    [(+ dx x) (+ dy y)]))

(defn step [cells]
  (set (for [[loc n] (frequencies (mapcat neighbours cells))
             :when (or (= n 3) (and (= n 2) (cells loc)))]
         loc)))

(def grid-size 20)


(defn button [text]
  (let [[text-width text-height] (ui/bounds (ui/label text))
        padding 12
        rect-width (+ text-width padding)
        rect-height (+ text-height padding)
        border-radius 3]
    [
     (ui/with-style ::ui/style-fill
       (ui/with-color [1 1 1]
         (ui/rounded-rectangle rect-width rect-height border-radius)))
     (ui/with-style ::ui/style-stroke
       [
        (ui/with-color [0.76 0.76 0.76 1]
          (ui/rounded-rectangle (+ 0.5 rect-width) (+ 0.5 rect-height) border-radius))
        (ui/with-color [0.85 0.85 0.85]
          (ui/rounded-rectangle rect-width rect-height border-radius))])

     (ui/translate (/ padding 2)
                (- (/ padding 2) 2)
                (ui/label text))]))

(defn view [{:keys [board running?] :as state}]
  (ui/translate
   30 30
   (ui/on
    :mouse-move
    (fn [[x y]]
      (swap! state-atm update :board
             conj [(int (/ x grid-size))
                   (int (/ y grid-size))])
      nil)
    (ui/wrap-on
     :mouse-down
     (fn [handler [x y]]
       (swap! state-atm update :board
              conj [(int (/ x grid-size))
                    (int (/ y grid-size))])
       (handler [x y]))
     [(ui/spacer 600 600)
      
      (into []
            (map (fn [[x y]]
                   (ui/translate (* x grid-size) (* y grid-size)
                                 (ui/rectangle grid-size grid-size))))
            board)
      (ui/translate 0 400
                    (ui/on
                     :mouse-down
                     (fn [_]
                       (swap! state-atm
                              update :running? not)
                       nil)
                     (button (if running?
                                  "Stop"
                                  "Start"))))])))
  )

(defn add-random []
  (swap! state-atm
         update :board
         (fn [board]
           (into board
                 (repeatedly 30 (fn []
                                  [(rand-int 20)
                                   (rand-int 30)])))))
  )

(add-watch state-atm ::run-gol (fn [k ref old updated]
                                 (when (and (:running? updated)
                                            (not (:running? old)))
                                   (future
                                     (while (:running? @state-atm)
                                       (swap! state-atm
                                              update :board step)
                                       (Thread/sleep 30))))))

(add-random)


(defn run-gol [nsteps]
  (dotimes [i nsteps]
    (swap! state-atm
           update :board step)
    (Thread/sleep 30)))

(defn show! []
  (let [window-info (backend/run #(view @state-atm))]
    (when-let [repaint (::backend/repaint window-info)]
      (add-watch state-atm
                 ::repaint
                 (fn [r k old new]
                   (when (not= old new)
                     (repaint)))))))

(defn -main [& args]
  (let [window-info (backend/run #(view @state-atm))]
    (when-let [repaint (::backend/repaint window-info)]
      (add-watch state-atm
                 ::repaint
                 (fn [r k old new]
                   (when (not= old new)
                     (repaint)))))

    (let [p (promise)
          ^javax.swing.JFrame frame (::backend/frame window-info)]
      (.addWindowListener frame
                          (reify java.awt.event.WindowListener
                            (^void windowActivated [this ^WindowEvent e])
                            (^void windowClosed [this ^WindowEvent e])
                            (^void windowClosing [this ^WindowEvent e]
                             (deliver p window-info))
                            (^void windowDeactivated [this ^WindowEvent e])
                            (^void windowDeiconified [this ^WindowEvent e])
                            (^void windowIconified [this ^WindowEvent e])
                            (^void windowOpened [this ^WindowEvent e])))
      @p
      (.dispose frame)
      (swap! state-atm assoc :running? false)
      (shutdown-agents))))
