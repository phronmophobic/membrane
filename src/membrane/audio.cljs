(ns membrane.audio)



(defonce audio-context
  (if (.-AudioContext js/window)
    (js/window.AudioContext.)
    (js/window.webkitAudioContext.)))


(defn load-audio [url]
  (let [req (js/XMLHttpRequest.)
        audio-ref (atom nil )]
    (.open req "GET" url true)
    (set! (.-responseType req) "arraybuffer")
    (set! (.-onload req)
          (fn []
            (.decodeAudioData audio-context
                              (.-response req)
                              (fn [buf]
                                (reset! audio-ref buf)))))
    (.send req)
    audio-ref))

(defn play-audio [audio-ref]
  (when-let [audio-buf @audio-ref]
    (let [src (.createBufferSource audio-context)]
      (set! (.-buffer src) audio-buf)
      (.connect src (.-destination audio-context))
      (.start src 0))))
