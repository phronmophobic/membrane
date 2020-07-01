(ns membrane.webgl-macros
  (:import javax.imageio.ImageIO))

(defmacro push-state [ctx & body]
  `(let [ctx# ~ctx]
    (try
      (.save ctx#)
      ~@body
      (finally
        (.restore ctx#)))))


(defn image-size-raw [image-path]
  (let [image-file (clojure.java.io/file image-path)]
    (when (.exists image-file)
      (try
        (let [buffered-image (ImageIO/read image-file)]
          [(.getWidth buffered-image)
           (.getHeight buffered-image)])
        (catch Exception e
          [0 0])))))



(defmacro add-image! [url-path image-path-or-bounds]
  (let [size (cond
               (and (vector? image-path-or-bounds)
                    (= (count image-path-or-bounds) 2))
               image-path-or-bounds

               (string? image-path-or-bounds)
               (image-size-raw image-path-or-bounds)

               :else
               (throw (Exception. "image-path-or size should either be a vector with [width height] or a path to an image on the local filesystem.")))]
    `(swap! ~'membrane.webgl/images assoc ~url-path
            {:image-obj (let [img# (js/Image.)]
                          (set! (.-src img#) ~url-path)
                          img#)
             :url ~url-path
             :size ~size}
           )))



