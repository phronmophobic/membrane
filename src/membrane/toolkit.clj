(ns membrane.toolkit)

(defprotocol IToolkit
  "Empty protocol to mark toolkits")

(defn toolkit? [o]
  (satisfies? IToolkit o))

(defprotocol IToolkitRun
  (run
    [toolkit view-fn]
    [toolkit view-fn options]
    "Run a user interface with `view-fn` to draw.

  `view-fn` should be a 0 argument function that returns an object satisfying `IDraw`.
  `view-fn` will be called for every repaint.

  `options` is a map with extra options. available options will depend on the specific toolkit."))

(defprotocol IToolkitRunSync
  (run-sync
    [toolkit view-fn]
    [toolkit view-fn options]
     "Run a user interface synchronously with `view-fn` to draw.

  `view-fn` should be a 0 argument function that returns an object satisfying `IDraw`.
  `view-fn` will be called for every repaint.

  `options` is a map with extra options. available options will depend on the specific toolkit."))

(defprotocol IToolkitFontExists
  (font-exists? [toolkit font]
    "Returns true if the font can be found by the toolkit."))
(defprotocol IToolkitFontMetrics
  (font-metrics [toolkit font]
    "Returns the font metrics for font."))
(defprotocol IToolkitFontAdvanceX
  (font-advance-x [toolkit font s]
    "Returns the advance-x for font."))
(defprotocol IToolkitFontLineHeight
  (font-line-height [toolkit font]
    "Returns the line height for font."))

(defprotocol IToolkitSaveImage
  (save-image
    [toolkit dest elem]
    [toolkit dest elem [w h :as size]]
    "Saves an image of elem to file with name `dest`.

  `dest`: the filename to write the image to.
  `elem`: the graphical element to draw
  `size`: the width and height of the image. If size is nil, the bounds and origin of elem will be used."))

(extend-type clojure.lang.Namespace
  IToolkit

  IToolkitRun
  (run
    ([toolkit view-fn]
     ((ns-resolve toolkit 'run) view-fn))
    ([toolkit view-fn options]
     ((ns-resolve toolkit 'run) view-fn options)))

  IToolkitRunSync
  (run-sync [toolkit view-fn]
    ((ns-resolve toolkit 'run-sync) view-fn))
  (run-sync [toolkit view-fn options]
    ((ns-resolve toolkit 'run-sync) view-fn options))

  IToolkitFontExists
  (font-exists? [toolkit font]
    ((ns-resolve toolkit 'font-exists?) font))

  IToolkitFontMetrics
  (font-metrics [toolkit font]
    ((ns-resolve toolkit 'font-metrics) font))

  IToolkitFontAdvanceX
  (font-advance-x [toolkit font s]
    ((ns-resolve toolkit 'font-advance-x) font s))

  IToolkitFontLineHeight
  (font-line-height [toolkit font]
    ((ns-resolve toolkit 'font-line-height) font))

  IToolkitSaveImage
  (save-image
    ([toolkit dest elem]
     ((ns-resolve toolkit 'save-image) elem))
    ([toolkit dest elem [w h :as size]]
     ((ns-resolve toolkit 'save-image) h :as size))))


(comment
  (require 'membrane.ui
           'membrane.java2d)

  (run (the-ns 'membrane.java2d)
    (constantly (membrane.ui/label "woohoo!" )))

  (require 'membrane.ui
           'membrane.skia)
  (run (the-ns 'membrane.skia)
    (constantly (membrane.ui/label "woohoo!" )))

  ,)


