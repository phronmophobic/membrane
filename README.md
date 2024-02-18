# Membrane

### A Simple UI Library That Runs Anywhere

![Architecture Overview](/docs/overview-01.png?raw=true)

Membrane provides all the tools you need to build a user interface. While all the pieces are built to work together, each piece is optional or can be mixed and matched with other implementations. For example, you could use [your favorite UI framework](https://github.com/phronmophobic/membrane-re-frame-example) and the other layers to reach another platform. Alternatively, you could provide your own ncurses graphics backend and leverage the ui framework and graphics model.

The three main pieces are:

1. A **UI framework**, `membrane.component`, that provides state management for GUIs
2. A platform agnostic **model** for graphics and events
3. Multiple **graphics backends** that provide concrete implementations for #2

For membrane to run on a platform, the only requirements are:
1) Drawing implementations for graphical primitives like shapes, text, and images
2) An event loop that forwards events (eg. mouse clicks, key presses) to membrane and repaints

#### Supported platforms:
- Mac OSX
- Linux
- the web via WebGL

#### Experimental platforms
- Windows (see skija, swing, and javafx backends)
- Terminal ([extra docs](/docs/terminal-uis.md),  [example project](https://github.com/phronmophobic/terminal-todo-mvc))
- virtual dom
- [skija](https://github.com/JetBrains/skija) (see [example project](https://github.com/phronmophobic/membrane-skija-example))
- javafx via cljfx (see [example project](https://github.com/phronmophobic/reveal-treemap))
- iOS via graalvm (see [example project](https://github.com/phronmophobic/greaseU))

#### Experimental UI framework integrations
- re-frame (see [example project](https://github.com/phronmophobic/membrane-re-frame-example))
- fulcro (see [example project](https://github.com/phronmophobic/membrane-fulcro))

#### Links

[Tutorial](/docs/tutorial.md)  
[API Reference](https://phronmophobic.github.io/membrane/api/)
[Documentation Organized by Topic](https://phronmophobic.github.io/membrane/membrane-topics.html)  
[Styled Text](https://phronmophobic.github.io/membrane/styled-text/index.html)  
[Examples](https://github.com/phronmophobic/membrane/tree/master/src/membrane/example)  
[Background and Theory](https://blog.phronemophobic.com/what-is-a-user-interface.html)  
[Distributing your desktop app](/docs/distribution.md)  
[Targeting WebGL](/docs/webgl.md)  
Questions? Comments? Connect with us on clojurians slack in [#membrane](https://clojurians.slack.com/archives/CVB8K7V50) (join [here](http://clojurians.net/)) or discuss on [twitter](https://twitter.com/phronmophobic).

<!-- Guides   -->
<!-- Design Philosophy   -->
<!-- FAQ   -->

## Rationale

Membrane was written because I wanted to build a desktop app with clojure, but I wanted all the cool functional features found in libraries targeting the browser, ie. react, reagent, re-frame, fulcro, etc.

Membrane does not build on top of any existing ui toolkits like Swing, JavaFX, HTML, UIKit, GTK, etc. These toolkits are fundamentally based on an object oriented model and have a deep impedance mismatch with idiomatic clojure code.

Views are values. Components and event handlers are pure functions.

For more info covering the design of membrane:  

### Talks
[![Simpler User Interfaces with Membrane](https://img.youtube.com/vi/Mjn92fODdaA/0.jpg)](https://www.youtube.com/watch?v=Mjn92fODdaA)  

### Posts

[What is a User Interface?](https://blog.phronemophobic.com/what-is-a-user-interface.html)  
[Implementing a Functional UI Model](https://blog.phronemophobic.com/ui-model.html)  
[Reusable UI Components](https://blog.phronemophobic.com/reusable-ui-components.html)

### Videos

[Interactive Programming with Clojure & Membrane](https://www.youtube.com/watch?v=ImBji-1bKkc) -  Peter Strömberg  

## Usage
Leiningen dependency:

```clojure
[com.phronemophobic/membrane "0.14.3-beta"]
```

deps.edn dependency:

```clojure
com.phronemophobic/membrane {:mvn/version "0.14.3-beta"}
```

## Built With Membrane

[membrane.term](https://github.com/phronmophobic/membrane.term) - A simple terminal emulator in clojure.  
[snowball](https://github.com/phronmophobic/snowball) - View the sizes of your dependencies.  
[grease](https://github.com/phronmophobic/grease) - An example of building a clojure library for iOS with graalvm native-image.  
[htmltoimage](https://github.com/phronmophobic/clj-cef/tree/main/examples/htmltoimage) - Example of using clj-cef to convert a URL to an image.  
[browser](https://github.com/phronmophobic/clj-cef/tree/main/examples/browser) - Example of building a very simplistic browser using membrane+skija.  
[clj-media](https://github.com/phronmophobic/clj-media) - View or create videos and gifs with clojure.  
[codetogif](https://github.com/phronmophobic/clj-media/tree/main/examples/codetogif) - A basic example of creating gifs using clj-media.  
[viscous](https://github.com/phronmophobic/viscous) - A visual data inspector/explorer that runs in constant space, time, and screen space.  
[treemap-clj](https://github.com/phronmophobic/treemap-clj) - An alternative to pprint for generically visualizing heterogeneous, hierarchical data. 

## Examples

### A Simple Example without the UI Framework

Screenshot:
![simple counter](/docs/images/counter1.gif?raw=true)

```clojure
(ns counter
  (:require [membrane.java2d :as java2d]
            [membrane.ui :as ui
             :refer [horizontal-layout
                     button
                     label
                     spacer
                     on]]))
(defonce counter-state (atom 0))

;; Display a "more!" button next to label showing num
;; clicking on "more!" will increment the counter
(defn counter [num]
  (horizontal-layout
   (on :mouse-down (fn [[mouse-x mouse-y]]
                     (swap! counter-state inc)
                     nil)
       (button "more!"))
   (spacer 5 0)
   (label num (ui/font nil 19))))

(comment
  ;; pop up a window that shows our counter
  (java2d/run #(counter @counter-state))
  ,)

```

### Simple Example using `membrane.component` UI Framework

Screenshot:
![simple counter](/docs/images/counter2.gif?raw=true)

```clojure
(ns counter
  (:require [membrane.java2d :as java2d]
            [membrane.ui :as ui
             :refer [horizontal-layout
                     vertical-layout
                     button
                     label
                     on]]
            [membrane.component :as component
             :refer [defui make-app defeffect]])
  (:gen-class))


;; Display a "more!" button next to label showing num
;; clicking on "more!" will dispatch a ::counter-increment effect
(defui counter [{:keys [num]}]
  (horizontal-layout
   (on :mouse-down (fn [[mouse-x mouse-y]]
                     [[::counter-increment $num]])
       (ui/button "more!"))
   (ui/label num)))

(defeffect ::counter-increment [$num]
  (dispatch! :update $num inc))

(comment
  ;; pop up a window showing our counter with
  ;; num initially set to 10
  (java2d/run (make-app #'counter {:num 10}))
  ,)
```

Here's an example of how you can use your `counter` component.

Screenshot:
![counting counter](/docs/images/counter3.gif?raw=true)

```clojure
;; Display an "Add Counter" button
;; on top of a stack of counters
;;
;; clicking on the "Add Counter" button will
;; add a new counter to the bottom of the stack
;; 
;; clicking on the counters' "more!" buttons will
;; update their respective numbers
(defui counter-counter [{:keys [nums]}]
  (apply
   vertical-layout
   (on :mouse-down (fn [[mx my]]
                     [[::add-counter $nums]])
       (ui/button "Add Counter"))
   (for [num nums]
     (counter {:num num}))))

(defeffect ::add-counter [$nums]
  (dispatch! :update $nums conj 0))

(comment
  ;; pop up a window showing our counter-counter
  ;; with nums initially set to [0 1 2]
  (java2d/run (make-app #'counter-counter {:nums [0 1 2]}))
  ,)
```

## Fun Features


```clojure
;; graphical elements are values
;; no need to attach elements to the dom to get layout info
(ui/bounds (vertical-layout
           (ui/label "hello")
           (ui/checkbox true)))
>> [30.79296875 27.0]


;; events are pure functions that return intents which are also values
(let [mpos [15 15]]
  (ui/mouse-down
   (ui/translate 10 10
                 (on :mouse-down (fn [[mx my]]
                                   ;;return a sequence of effects
                                   [[:say-hello]])
                     (ui/label "Hello")))
   mpos))
>> ([:say-hello])


;; horizontal and vertical centering!
(java2d/run #(let [rect (ui/with-style :membrane.ui/style-stroke
                        (ui/rectangle 200 200))]
             [rect
              (ui/center (ui/label "hello") (ui/bounds rect))]) )


;; save graphical elem as an image
(let [todos [{:complete? false
              :description "first"}
             {:complete? false
              :description "second"}
             {:complete? true
              :description "third"}]]
  (java2d/save-image "todoapp.png"
                   (todo-app {:todos todos :selected-filter :all})))

;; use spec to generate images of variations of your app
(doseq [[i todo-list] (map-indexed vector (gen/sample (s/gen ::todos)))]
  (java2d/save-image (str "todo" i ".png")
                   (ui/vertical-layout
                    (ui/label (with-out-str
                                (clojure.pprint/pprint todo-list)))
                    (ui/with-style :membrane.ui/style-stroke
                      (ui/path [0 0] [400 0]))
                    (todo-app {:todos todo-list :selected-filter :all}))))

```
## Screenshots

![Overview](/docs/images/overview.gif?raw=true) 

## More Info

That's it! For more in-depth info, check out the [tutorial](/docs/tutorial.md).

[Tutorial](/docs/tutorial.md)  
[API Reference](https://phronmophobic.github.io/membrane/api/)
[Documentation Organized by Topic](https://phronmophobic.github.io/membrane/membrane-topics.html)  
[Styled Text](https://phronmophobic.github.io/membrane/styled-text/index.html)  
[Examples](https://github.com/phronmophobic/membrane/tree/master/src/membrane/example)  
[Background and Theory](https://blog.phronemophobic.com/what-is-a-user-interface.html)  
[Distributing your desktop app](/docs/distribution.md)  
[Targeting WebGL](/docs/webgl.md)  
Questions? Comments? Connect with us on clojurians slack in [#membrane](https://clojurians.slack.com/archives/CVB8K7V50)
<!-- Guides   -->
<!-- Design Philosophy   -->
<!-- FAQ   -->

# License

Copyright 2021 Adrian Smith. Membrane is licensed under Apache License v2.0.








