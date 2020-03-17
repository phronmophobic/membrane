# Membrane with webgl target

The basic idea is that everything in `membrane.component` and `membrane.ui` is platform agnostic. As long as you build your GUI using the tools in those namespaces, your app should work on other platforms.

Below are a few tips for setting up your project when targeting opengl.

## Hello World

```
(ns helloworld
  ;; typical requires
  (:require-macros [membrane.webgl-macros
                    :refer [add-image!]])
  (:require [membrane.component :refer [defui]]
            [membrane.webgl :as webgl]
            membrane.basic-components
            [membrane.ui :as ui
             :refer [horizontal-layout
                     vertical-layout]]))

;; Must be an <canvas/> element
(def canvas (.getElementById js/document "canvas"))
(defonce start-app (membrane.webgl/run #(ui/label "Hello World") {:canvas canvas}))
```

To receive key events, your canvas needs to a "tabindex" attribute set to zero or greater (https://developer.mozilla.org/en-US/docs/Web/HTML/Global_attributes/tabindex). If you don't know what value to set it to, then set it to zero. 

You can also create your canvas in clojurescript and add it to the page. Creating a canvas element using `membrane.webgl/create-canvas` will set the tabindex to "0" for you.

```
(let [canvas (webgl/create-canvas 300 400)]
  (.appendChild (.-body js/document) canvas)
  (defonce start-app (membrane.webgl/run #(ui/label "Hello World") {:canvas canvas})))
```

## Images

Images must be loaded using `(add-image! url-path image-path-or-bounds)` where `url-path` is the url the image should be loaded from and `image-path-or-bounds` is either a 2 element vector of `[width height]` or the path to an image on the local filesystem that is read to figure out the image's size.

The images can then be used like

```
(ui/image url-path)
```

## Fonts

Font layout information is needed to correctly layout fonts. By default, the Ubuntu font is loaded and used. Better font support coming soon!

## Components

Components can still be run using `membrane.component/run-ui`.

For example:
```
(defonce start-todo-app (membrane.component/run-ui #'todo/todo-app todo/todo-state nil {:canvas canvas}))
```
