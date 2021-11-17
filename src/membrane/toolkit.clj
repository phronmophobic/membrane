(ns membrane.toolkit)

(defprotocol IToolkitRun
  (run
    [this view-fn]
    [this view-fn options]
    "Run a user interface with `view-fn` to draw.

  `view-fn` should be a 0 argument function that returns an object satisfying `IDraw`.
  `view-fn` will be called for every repaint.

  `options` is a map with extra options. available options will depend on the specific toolkit."))

(defprotocol IToolkitRunSync
  (run-sync
    [this view-fn]
    [this view-fn options]
     "Run a user interface synchronously with `view-fn` to draw.

  `view-fn` should be a 0 argument function that returns an object satisfying `IDraw`.
  `view-fn` will be called for every repaint.

  `options` is a map with extra options. available options will depend on the specific toolkit."))

(defprotocol IToolkitFontExists
  (font-exists? [this font]
    "Returns true if the font can be found by the toolkit."))
(defprotocol IToolkitFontMetrics
  (font-metrics [this font]
    "Returns the font metrics for font."))
(defprotocol IToolkitFontAdvanceX
  (font-advance-x [this font]
    "Returns the advance-x for font."))
(defprotocol IToolkitFontLineHeight
  (font-line-height [this font]
    "Returns the line height for font."))

(defprotocol IToolkitSaveImage
  (save-image
    [this path elem]
    [this path elem [w h :as size]]
    "Saves an image of elem to file with name `path`.

  `path`: the filename to write the image to
  `elem`: the graphical element to draw
  `size`: the width and height of the image. If size is nil, the bounds and origin of elem will be used."))

(extend-type clojure.lang.Namespace
  IToolkitRun
  (run
    ([this view-fn]
     ((ns-resolve this 'run) view-fn))
    ([this view-fn options]
     ((ns-resolve this 'run) view-fn options)))

  IToolkitRunSync
  (run-sync [this view-fn]
    ((ns-resolve this 'run-sync) view-fn))
  (run-sync [this view-fn options]
    ((ns-resolve this 'run-sync) view-fn options))

  IToolkitFontExists
  (font-exists? [this font]
    ((ns-resolve this 'font-exists?) font))

  IToolkitFontMetrics
  (font-metrics [this font]
    ((ns-resolve this 'font-metrics) font))

  IToolkitFontAdvanceX
  (font-advance-x [this font]
    ((ns-resolve this 'font-advance-x) font))

  IToolkitFontLineHeight
  (font-line-height [this font]
    ((ns-resolve this 'font-line-height) font))

  IToolkitSaveImage
  (save-image
    ([this path elem]
     ((ns-resolve this 'save-image) elem))
    ([this path elem [w h :as size]]
     ((ns-resolve this 'save-image) h :as size))))


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


