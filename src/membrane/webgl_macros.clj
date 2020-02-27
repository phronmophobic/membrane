(ns membrane.webgl-macros
  (:require [membrane.uibuilder :as uib])
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



(defmacro add-image! [url-base image-path]
  (let [size (image-size-raw image-path)
        fname (.getName (clojure.java.io/file image-path))
        url-path (str url-base fname)]
    `(swap! ~'membrane.webgl/images assoc ~url-path
            {:image-obj (let [img# (js/Image.)]
                          (set! (.-src img#) ~url-path)
                          img#)
             :size ~size}
           )))



(defmacro run-webgl-project [project-name]
  (let [project-path (clojure.java.io/file uib/save-folder project-name)
        project (read-string (slurp project-path))
        media-dirs [(clojure.java.io/file "media" project-name)]
        code (membrane.uibuilder/webgl-code media-dirs (:elems project) (:active-elem-id project))]
    code))
