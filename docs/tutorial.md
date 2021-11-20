
# Tutorial

- [Getting started](/docs/tutorial.md#getting-started)
- [Hello World](/docs/tutorial.md#hello-world)
- [Coordinates](/docs/tutorial.md#coordinates)
- [Graphics](/docs/tutorial.md#graphics)
    - [Text](/docs/tutorial.md#text)
    - [Lines and Shapes](/docs/tutorial.md#lines-and-shapes)
    - [Color](/docs/tutorial.md#color)
    - [Groups](/docs/tutorial.md#groups)
    - [Transforms](/docs/tutorial.md#transforms)
        - [Translate](/docs/tutorial.md#translate)
        - [Scale](/docs/tutorial.md#scale)
    - [Basic Layout](/docs/tutorial.md#basic-layout)
    - [Common UI Elements](/docs/tutorial.md#common-ui-elements)
- [Events](/docs/tutorial.md#events)
    - [Event flow](/docs/tutorial.md#event-flow)
        - [Ignore all events from a child element](/docs/tutorial.md#ignore-all-events-from-a-child-element)
        - [Filter some events](/docs/tutorial.md#filter-some-events)
        - [Transform some events](/docs/tutorial.md#transform-some-events)
    - [Event Handlers](/docs/tutorial.md#event-handlers)
        - [:mouse-down](/docs/tutorial.md#mouse-down)
        - [:mouse-up](/docs/tutorial.md#mouse-up)
        - [:mouse-move](/docs/tutorial.md#mouse-move)
        - [:mouse-event](/docs/tutorial.md#mouse-event)
        - [:key-event](/docs/tutorial.md#key-event)
        - [:key-press](/docs/tutorial.md#key-press)
        - [:clipboard-copy](/docs/tutorial.md#clipboard-copy)
        - [:clipboard-cut](/docs/tutorial.md#clipboard-cut)
        - [:clipboard-paste](/docs/tutorial.md#clipboard-paste)
    - [Effect bubbling](/docs/tutorial.md#effect-bubbling)
    - [Side effects in event handlers](/docs/tutorial.md#side-effects-in-event-handlers)
- [Components](/docs/tutorial.md#components)
    - [Running components](/docs/tutorial.md#running-components)
    - [File Selector example](/docs/tutorial.md#file-selector-example)

## Getting started

Add this dependency to your project:

Leiningen dependency:

```clojure
[com.phronemophobic/membrane  "0.9.22-beta"]
```

deps.edn dependency:

```clojure
com.phronemophobic/membrane {:mvn/version "0.9.22-beta"}
```

## Hello World

All examples below will use the following namespace requires. 

```clojure
(ns tutorial
  (:require [membrane.java2d :as java2d]
            [membrane.ui :as ui
             :refer [vertical-layout
                     translate
                     horizontal-layout
                     button
                     label
                     with-color
                     bounds
                     spacer
                     on]]
            [membrane.component :as component
             :refer [defui defeffect]]
            [membrane.basic-components :as basic]))
```

Below is the "Hello World!" program for membrane:

`(java2d/run #(ui/label "Hello World!"))`

This will pop up a window that says "Hello World!". 

`membrane.java2d/run` takes a 0 argument function that returns a value that implements the IDraw protocol.
Implementations of the IDraw protocol can be tricky and platform dependent. Thankfully, the 
implementations for many graphical primitives are provided by the membrane graphics backends.

## Coordinates

![Coordinates](/docs/images/coordinates.png?raw=true)

Coordinates are represented as a vector of two numbers `[x, y]`.


## Graphics

The `membrane.ui` namespace provides graphical primitives you can use to specify your UI.

See below for quick reference of some of the graphical primitives available in `membrane.ui`.
For more examples, check out the [kitchen sink](/src/membrane/example/kitchen_sink.clj) or if you have the membrane library as a dependency, run it with `lein run -m membrane.example.kitchen-sink`.


### Text

![Label Default](/docs/images/label-default.png?raw=true)

```clojure
;; label using default font
(ui/label "Hello\nWorld!")
```


![Label with font](/docs/images/label-font.png?raw=true)
```clojure
;; label with specified font
(ui/label "Hello\nWorld!" (ui/font "Menlo" 22))
```

![Label with font](/docs/images/label-font.png?raw=true)
```clojure
;; Use the default font, but change the size
(ui/label "Hello\nWorld!" (ui/font nil 22))
```
### Lines and Shapes

![Star](/docs/images/star.png?raw=true)
```clojure
;; filled polygon
(ui/path [24.20 177.98]
         [199.82 37.93]
         [102.36 240.31]
         [102.36 15.68]
         [199.82 218.06]
         [24.20 78.01]
         [243.2 127.99]
         [24.20 177.98])
```


![Star](/docs/images/sawtooth.png?raw=true)
```clojure
;; line
(ui/with-style :membrane.ui/style-stroke
  (ui/with-stroke-width 3
    (ui/with-color [1 0 0]
      (apply
       ui/path
       (for [i (range 10)]
         [(* 30 i)
          (if (even? i) 0 30)])))))
```


![rectangle](/docs/images/rectangle.png?raw=true)
```clojure
;; draw a filled  rectangle
(ui/with-style :membrane.ui/style-stroke
  (ui/with-stroke-width 3
    (ui/with-color [0.5 0 0.5]
      (ui/rectangle 100 200))))
```

![rounded rectangle](/docs/images/rounded-rectangle.png?raw=true)
```clojure
;; rounded rectangle
(ui/rounded-rectangle 200 100 10)
```

### Color

![label with color](/docs/images/label-color.png?raw=true)
```clojure
;; colors are vectors of [red green blue] or [red green blue alpha]
;; with values from 0 - 1 inclusive

(ui/with-color [1 0 0]
  (ui/label "Hello"))
```

### Groups

To draw multiple elements, simply use a vector. The elements will be drawn in order.

![label with color](/docs/images/group.png?raw=true)
```clojure
[(ui/with-color [1 0 0 0.75]
   (ui/label "red"))
 (ui/with-color [0 1 0 0.75]
   (ui/label "green"))
 (ui/with-color [0 0 1 0.75]
   (ui/label "blue"))]
```

### Transforms

#### Translate
`translate [x y drawable]`

![translate](/docs/images/translate.png?raw=true)
```clojure
[(ui/with-style :membrane.ui/style-stroke
   [(ui/path [0 0] [0 100])
    (ui/path [0 0] [60 0])])
 (ui/rectangle 30 50)
 (ui/translate 30 50
               (ui/rectangle 30 50))]
```

#### Scale

`scale [sx sy & drawables]` 

![scale](/docs/images/scale.png?raw=true)
```clojure
(ui/scale 3 10
    (ui/label "sx: 3, sy: 10"))
```

### Basic Layout

`vertical-layout [& elems]`
![vertical layout](/docs/images/vertical-layout.png?raw=true)

```clojure
(ui/vertical-layout
 (ui/button "hello")
 (ui/button "world"))
```

`horizontal-layout [& elems]`
![horizontal layout](/docs/images/horizontal-layout.png?raw=true)

```clojure
(ui/horizontal-layout
 (ui/button "hello")
 (ui/button "world"))
```

![horizontal layout spacing](/docs/images/horizontal-layout-spacing.png?raw=true)

```clojure
(apply ui/horizontal-layout
       (interpose
        (spacer 10 0)
        (for [i (range 1 5)]
          (ui/with-color [0 0 0 (/ i 5.0)]
            (ui/rectangle 100 50)))))
```


![centering](/docs/images/center2.png?raw=true)
```clojure
;; most layouts can be created just by using bounds
(defn center [elem [width height]]
  (let [[ewidth eheight] (bounds elem)]
    (translate (int (- (/ width 2)
                       (/ ewidth 2)))
               (int (- (/ height 2)
                       (/ eheight 2)))
               elem)))
(ui/with-style :membrane.ui/style-stroke
  (let [rect (ui/rectangle 100 50)
        line (center (ui/path [0 0] [75 0])
                     (ui/bounds rect))]
    [rect line]))
```



### Common UI Elements

Typically, the graphics for elements and their event handling code is intertwined. These elements are simply views and have no default behavior associated with them. If you do want both the default behavior, check out `membrane.component/button` and `membrane.component/checkbox`.

![checkbox](/docs/images/checkbox-elem.png?raw=true)

```clojure
(ui/horizontal-layout
 (ui/padding 10 10
             (ui/checkbox false))
 (ui/padding 10 10
             (ui/checkbox true)))
```

![button](/docs/images/button-elem.png?raw=true)
```clojure
(ui/horizontal-layout
 (ui/padding 10 10
             (ui/button "button"))
 (ui/padding 10 10
             (ui/button "button" nil true)))
```

## Events

With membrane, events handlers are pure functions that take events and return a sequence of effects.
Effects are a data description of what to do rather than a side effect.

```clojure
;; mouse-down event at location [15 15]
(let [mpos [15 15]]
  (ui/mouse-down
   (ui/translate 10 10
                 (ui/on :mouse-down (fn [[mx my]]
                                   ;;return a sequence of effects
                                   [[:say-hello]])
                     (ui/label "Hello")))
   mpos))
>> ([:say-hello])
```

### Event flow

Conceptually, event handling is hierarchical. When an event occurs, it asks the root element what effects should take place. The root element may
1) simply return effects
2) fully delegate to its child elements by asking them what effects should take place and returning those effects
3) partially delegate by asking its child elements what effects should take place and transforming or filtering those effects before returning

The default behavior for most container components is #2, delegating to their child components. However, the parent component always has the final say over what effects should occur over its child components. This is the functional equivalent of the browser's `event.preventDefault` and `event.stopPropagation`.

Here are few illustrative examples:

```clojure
(let [elem (ui/on
            ;; return nil for any mouse-down event
            :mouse-down (fn [_] nil)
            (ui/button "Big Red Button"
                       (fn []
                         [[:self-destruct!]])))]
     (ui/mouse-down elem [20 20]))
>>> nil
```

#### Ignore all events from a child element

It's useful for graphical GUI builders and testing to be able to silence components. Use `membrane.ui/no-events` to silence all child elems.

```clojure
(let [elem (ui/no-events
            (ui/button "Big Red Button"
                       (fn []
                         [[:self-destruct!]])))]
  (ui/mouse-down elem [20 20]))
>>> nil
```

#### Filter some events

Here, we'll only ask `child-elem` what effects should occur if the key pressed is a lower case letter. No symbols, numbers, or upper case letters allowed!

```clojure
(let [lower-case-letters (set (map str "abcdefghijklmnopqrstuvwxyz"))
      child-elem (ui/on :key-press
                        (fn [s]
                          [[:child-effect1 s]
                           [:child-effect2 s]])
                        (ui/label "child elem"))
      elem (ui/on
            :key-press (fn [s]
                         (when (contains? lower-case-letters s)
                           (ui/key-press child-elem s)))
            child-elem)]
  {"a" (ui/key-press elem "a")
   "." (ui/key-press elem ".")})
>>> {"a" [[:child-effect1 "a"] [:child-effect2 "a"]], 
     "." nil}
```

#### Transform some events

```clojure
(let [child-elem (ui/on :key-press
                        (fn [s]
                          [[:child-effect1 s]
                           [:child-effect2 s]])
                        (ui/label "child elem"))
      elem (ui/on
            :key-press (fn [s]
                        (if (= s ".")
                          [[:do-something-special]]
                          (ui/key-press child-elem s)
                          ))
            child-elem)]
  {"a" (ui/key-press elem "a")
   "." (ui/key-press elem ".")})
>>> {"a" [[:child-effect1 "a"] [:child-effect2 "a"]],
     "." [[:do-something-special]]}
```


### Event Handlers

Event handlers can be provided using `membrane.ui/on`.

#### :mouse-down

:mouse-down [[mx my]]
mpos is a vector of `[mx, my]` in the elements local coordinates.

Will only be called if [mx my] is within the element's bounds
```clojure
(on :mouse-down (fn [[mx my :as mpos]]
                  ;;return a sequence of effects
                  [[:hello mx my]])
    (ui/label "Hello"))
```

#### :mouse-up
:mouse-up [[mx my]]
mpos is a vector of `[mx, my]` in the elements local coordinates.

Will only be called if [mx my] is within the element's bounds

```clojure
(on :mouse-up (fn [[mx my :as mpos]]
                ;;return a sequence of effects
                [[:hello mx my]])
    (ui/label "Hello"))
```

#### :mouse-move

:mouse-move [[mx my]]
mpos is a vector of `[mx, my]` in the elements local coordinates.

Will only be called if [mx my] is within the element's bounds

```clojure
(on :mouse-move (fn [[mx my :as mpos]]
                  ;;return a sequence of effects
                  [[:hello mx my]])
    (ui/label "Hello"))
```


#### :mouse-event
:mouse-event [mpos button mouse-down? mods]
`mpos` is a vector of `[mx, my]` in the elements local coordinates.
`button` is 0 if left click, 1 if right click. greater than 1 for more exotic mouse buttons
`mouse-down?` is true if button is pressed down, false if the button is being released
`mods` is an integer mask. masks are
```
SHIFT   0x0001  
CONTROL   0x0002  
ALT   0x0004  
SUPER   0x0008  
CAPS_LOCK   0x0010  
NUM_LOCK   0x0020  
```

Will only be called if `[mx my]` is within the element's bounds

```clojure
(on :mouse-event (fn [[[mx my] button mouse-down? mods]]
                   ;;return a sequence of effects
                   [[:hello mx my]])
    (ui/label "Hello"))
```

#### :key-event
:key-event [key scancode action mods]

`key` corresponds to a keyboard key. `key-event` is a lower level event compared to `key-press` and will not try to "interpret" the key pressed and only report which key on the keyboard was pressed. The `key` value may vary across graphics backends. For java2d, `key` will correspond to the glfw keys found at https://www.glfw.org/docs/latest/group__keys.html.

`scancode` The scancode is unique for every key, regardless of whether it has a key token. Scancodes are platform-specific but consistent over time, so keys will have different scancodes depending on the platform but they are safe to save to disk.
`action` one of :press, :repeat, :release, or :unknown if the underlying platform documentation has lied.
`mods` is an integer mask. masks are

```
SHIFT   0x0001
CONTROL   0x0002
ALT   0x0004
SUPER   0x0008
CAPS LOCK   0x0010
NUM LOCK   0x0020
```

```clojure
(on :key-event (fn [key scancode action mods]
                 [[:hello key scancode action mods]])
    (ui/label "Hello"))
```

#### :key-press
:key-press [key]
`key` is a string if it is printable
otherwise one of
```clojure
:grave_accent :world_1 :world_2 :escape :enter :backspace :insert :delete :right :left :down :up :page_up :page_down :home :end :caps_lock :scroll_lock :num_lock :print_screen :pause :f1 :f2 :f3 :f4 :f5 :f6 :f7 :f8 :f9 :f10 :f11 :f12 :f13 :f14 :f15 :f16 :f17 :f18 :f19 :f20 :f21 :f22 :f23 :f24 :f25 :kp_0 :kp_1 :kp_2 :kp_3 :kp_4 :kp_5 :kp_6 :kp_7 :kp_8 :kp_9 :kp_decimal :kp_divide :kp_multiply :kp_subtract :kp_add :kp_enter :kp_equal :left_shift :left_control :left_alt :left_super :right_shift :right_control :right_alt :right_super :menu
```

```clojure
(on :key-press (fn [key]
                [[:respond-to-key key]])
    (ui/label "Hello"))
```


#### :clipboard-copy
:clipboard-copy []
Called when a clipboard copy event occurs.

#### :clipboard-cut
:clipboard-cut
Called when a cliboard cut event occurs.

#### :clipboard-paste
:clipboard-paste [s]
`s` is the string being pasted
Called when a clipboard paste event occurs.

### Effect bubbling

Another benefit of having a value based event system is that you can also easily transform and filter effects.


```clojure
(defn search-bar [s]
  (horizontal-layout
   (on
    :mouse-down (fn [_]
                  [[:search s]])
    (ui/button "Search"))
   (ui/label s)))

(let [selected-search-type :full-text
      bar (search-bar "clojure")
      elem (on :search
               (fn [s]
                 [[:search selected-search-type s]])
               bar)]
  (ui/mouse-down elem
                 [10 10]))
>>> ([:search :full-text "clojure"])
```




### Side effects in event handlers

The effects returned by event handlers are meant to be used in conjuction with UI frameworks. If you're making a simple UI, then you can just put your side effects in the event handler. Just note that you'll want to return nil from the handler since the event machinery expects a sequence of events. Nothing bad will happen if you don't, but you may see IllegalArgumentExceptions in the logs with the message "Don't know how to create ISeq from: ...".

```clojure
(ui/translate 10 10
              (on :mouse-down (fn [[mx my]]
                                ;; side effect
                                (println "hi" mx my)
                                
                                ;; return nil to prevent error messages
                                nil)
                  (ui/label "Hello")))
```


## Components

While using membrane does not require any specific UI framework, it does provide its own builtin UI framework, `membrane.component`.


The purpose of a UI framework is to provide tools to handle events and manage state. If the job was simply visualizing data, then all you would need is a function that accepts data and returns what to display. It's the interactivity that makes things more complicated.

For an interactive interface, you want to combine what to draw with how to respond to events.

To explain how `membrane.component` helps, let's a take a simple example of creating an ugly checkbox.

Without using any framework, your code might look something like this:

```clojure
(def app-state (atom false))

(defn checkbox [checked?]
  (on
   :mouse-down
   (fn [_]
     (swap! app-state not)
     nil)
   (ui/label (if checked?
               "X"
               "O"))))

(comment (java2d/run #(checkbox @app-state)))

```

This works great for this simple example, but this checkbox has to know exactly how to update the `checked?` value. Changing the data model would require changing the checkbox code and we would really like our checkbox to be more reusable.

Let's see what this same ugly checkbox would look like with `membrane.component`

```clojure
(defui checkbox [ {:keys [checked?]}]
  (on
   :mouse-down
   (fn [_]
     [[::toggle $checked?]])
   (ui/label (if checked?
               "X"
               "O"))))

(defeffect ::toggle [$checked?]
  (dispatch! :update $checked? not))

(defui checkbox-test [{:keys [x y z]}]
  (vertical-layout
   (checkbox {:checked? x})
   (checkbox {:checked? y})
   (checkbox {:checked? z})
   (ui/label
    (with-out-str (clojure.pprint/pprint
                   {:x x
                    :y y
                    :z z})))))

(comment (java2d/run (component/make-app #'checkbox-test {:x false :y true :z false})))
```
Here's what the above looks like

![checkboxes](/docs/images/checkbox.gif)

You'll notice a few differences in the code.

1) The `defn`s have been replaced with `defui`s.
2) The positional parameters have been converted to keyword parameters.
3) The mouse down handler no longer has any side effects. Instead, it returns a value.
4) The value returned by the mouse down handler includes a mysterious symbol, `$checked?`
5) There is a `::toggle` effect defined by `defeffect` 

The most interesting part of this example is the `:mouse-down` event handler which returns `[[::toggle $checked?]]`. Loosely translated, this means "when a `:mouse-down` event occurs, the checkbox proposes 1 effect which toggles the value of `checked?`." It does not specify _how_ the `::toggle` effect is implemented.

What exactly is `$checked?`? The symbol `$checked?` is replaced by the `defui` macro with a value that specifies the path to `checked?`. In fact, the `defui` macro will replace all symbols that start with "$" that derive from a keyword parameter with a value that represents the path of the corresponding symbol.

You can find the implementation of the `::toggle` effect by checking its `defeffect`.
```clojure
(defeffect ::toggle [$checked?]
  (dispatch! :update $checked? not))
```

`membrane.component/defeffect: [type args & body]` is a macro that does 3 things:
1) It registers a global effect handler of `type` (in this case, `::toggle`). `type` should be a keyword and since it is registered globally, should be namespaced
2) It will define a var in the current namespace of `effect-*type*` where *type* is the name of the type keyword (in this example, "toggle"). This can be useful if you want to be able to use your effect functions separately from the ui framework (for example, testing).
3) It will implicitly add an additional argument as the first parameter named `dispatch!`

The arglist for `dispatch!` is `[type & args]`. Calling `dispatch!` will invoke the effect of `type` with `args`.
The role of `dispatch!` is to allow effects to define themselves in terms of other effects. Effects should not be called directly because while the default for an application is to use all the globally defined effects, this can be overridden for testing, development, or otherwise.



### Running components

Every component can be run on its own. The goal is to build complex components and applications out of simpler components.

To run a component, simply call the backend's (eg. java2d) `run` function with `(component/make-app #'component-var initial-state)`


### File Selector example

For this example, we'll build a file selector. Usage will be as follows: Call `(file-selector "/path/to/folder")` to open up a window with a file selector. The selected filenames will be returned as a set when the window is closed.

First, we'll build a generic item selector. Our item selector will display a row of checkboxes and filenames.

Let's create the component to display and select individual items.

![item row](/docs/images/item-row.png?raw=true)

```clojure
(defui item-row [ {:keys [item-name selected?]}]
  (on
   :mouse-down
   (fn [_]
     [[:update $selected? not]])
   ;; put the items side by side
   (horizontal-layout
    (translate 5 5
               ;; checkbox in `membrane.ui` is non interactive.
               (ui/checkbox selected?))
    (spacer 5 0)
    (ui/label item-name))))

(comment
 ;; It's a very common workflow to work on sub components one piece at a time.
  (java2d/run (component/make-app #'item-row {:item-name "my item" :selected? false})))
```

Next, we'll build a generic item selector. For our item selector, we'll have a vertical list of items. Additionally, we'll have a textarea that let's us filter for only names that have the textarea's substring.

![item selector](/docs/images/item-selector.gif?raw=true)


```clojure
(defui item-selector
  "`item-names` a vector of choices to select from
`selected` a set of selected items
`str-filter` filter out item names that don't contain a case insensitive match for `str-filter` as a substring
"
  [{:keys [item-names selected str-filter]
    :or {str-filter ""
         selected #{}}}]
  (let [filtered-items (filter #(clojure.string/includes? (clojure.string/lower-case %) str-filter) item-names)]
    (apply
     vertical-layout
     (basic/textarea {:text str-filter})
     (for [iname filtered-items]
       ;; override the default behaviour of updating the `selected?` value directly
       ;; instead, we'll keep the list of selected items in a set
       (on :update
           (fn [& args]
             [[:update $selected (fn [selected]
                                   (if (contains? selected iname)
                                     (disj selected iname)
                                     (conj selected iname)))]])
           (item-row {:item-name iname :selected? (get selected iname)}))))))

(comment
  (java2d/run (component/make-app #'item-selector {:item-names (->> (clojure.java.io/file ".")
                                (.listFiles)
                                (map #(.getName %)))} )))
```

Finally, we'll define a file-selector function using our `item-selector` ui.

```clojure
(defn file-selector [path]
  (let [state (atom {:item-names
                     (->> (clojure.java.io/file path)
                          (.listFiles)
                          (map #(.getName %))
                          sort)})]
    (java2d/run-sync (component/make-app #'item-selector state))
    (:selected @state)))
```

Since `run-sync` will wait until the window is closed, all we need to do is dereference the state atom and grab the value for the `:selected` key.


<!-- ## Examples of form `defui` understands -->




<!-- ## Component gotchas -->

<!-- using a declared `component` rather than a defined one. -->





