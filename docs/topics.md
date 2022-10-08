

{{table-of-contents/}}


# Introduction


_We're still figuring out best practices for membrane. If you tried one of these methods, please report back! Even if it didn't work, it would be good learn from the experience._

The following documents how to use membrane organized by topic. Each section is standalone. Feel free to scan, skip, or jump to any topic.

# Overview

![Overview](membrane-topics/overview-01.png)

**View Function** - A pure function which receives the relevant application state as an argument and returns data specifying _what_ to draw (how to draw the data will be provided elsewhere).

**View**: Data describing what to present to the user.

**Event Function**: A pure function which receives a view and an event and returns data specifying the user's intent.

**Intent**: Data representing a user intent. Examples of user intents are "delete a todo list item", "open a document", "navigate to a URL".

**Effect Handler**: An impure function that receives intents and should process these intents by affecting the world.

**Effect**: The carrying out of an intent.


# Layout

Membrane doesn't enforce any particular layout method. The main reason is that there's no need to. The flexibility comes from using immutable values. Most UI libraries rely on an underlying mutable, object-oriented system. In a mutable world, it's very difficult to paint a consistent picture of the world. In order to make the problem tractable, UI libraries are typically single threaded. Further, layout must happen at just the right time to capture a consistent snapshot before painting. These constraints mean that layout is managed by the UI library. Since layout is managed by the library, the library has to try and decide ahead of time what kinds of layouts it will support. Mutability can box a system into a series of unfortunate decisions leading to brittle and frustrating programs.

On the flip side, if views are values, it doesn't matter when layout happens. If it doesn't matter when layout happens, then the UI library doesn't have to choose ahead of time what layout systems should be supported.

Ok, so membrane doesn't have pre-ordained, blessed layout system. Good to know, but views still need some layout. What are the options for layout in membrane?

Layout is primarily about arranging views in space. A fundamental part of arranging views is measurement (ie. determining the width and height of a view). In membrane, the size of a view can be determined with `membrane.ui/bounds` which returns a two element vector of width and height, `[width, height]`. The `width` and `height` is with respect to the origin of the view which can be found with `membrane.ui/origin` (returns a two element vector of `[origin-x, origin-y]`). An important design goal for membrane was that measurement is just a regular, pure function. Measurement shouldn't require a special thread or any special context. Fortunately, clojure's emphasis on immutability makes it easy.

If measurement is just a pure function, then layout is also a pure function and normal functional techniques apply. Layout isn't special.

As an example, let's take a look at `membrane.ui/center`.

```clojure
(defn center
  "Centers `elem` within a space of `[width, height]`"
  [elem [width height]]
  (let [[ewidth eheight] (bounds elem)]
    (translate (int (- (/ width 2)
                       (/ ewidth 2)))
               (int (- (/ height 2)
                       (/ eheight 2)))
               elem)))
```

The `membrane.ui/center` function is just a normal function for arranging elements. In addition to `center`, `membrane.ui` also includes `vertical-layout`, `horizontal-layout`, and `table-layout`. More layout helpers will be added. Layout is an area where third party library can shine.

<!-- ![Coordinates](/docs/images/coordinates.png?raw=true) -->


# Effect Handlers

Membrane intentionally doesn't say a lot about how effect handling should be done. Each application should decide how effect handling should be done for its particular use case. There is no reason to couple effect handling to a specific UI library. Effect handling is a general problem. Any library that helps should work when building a user interface.


<!-- ## Effect handler design considerations -->

## Plugging your own Effect Handler
  
  So what is an effect handler? Membrane apps are created using `membrane.component/make-app`. A custom handler can be provided by passing a function as the third argument. If the handler is `nil` or not provided, than the default effect handler will be used. A hander is function that will receive one argument, a sequence of intents.

  Let's start with an example of using a custom effect handler. 

```clojure

(defui my-counter [{:keys [num]}]
  (ui/on
   :mouse-down
   (fn [_]
     [[:inc]])
   (ui/label num )))

(def my-handler prn)
(def my-app (component/make-app #'my-counter
                                {:num 0}
                                my-handler))
(comment
  (backend/run my-app)
  ,)

```

When you run `my-app`. You see that it will print `([:inc [(keypath :num)]])` whenever you click on the label. 


  <!-- - builtin effect handling -->
  <!-- - side effects -->
  <!-- - async -->
  <!-- - repainting -->

## Modeling User Intents

The purpose of the event function is to translate raw user inputs like mouse and key events into user intents. The user intents are then passed to the effect handler to carry them out. For small applications, the separation of the event function and effect handler isn't that important. However, as an application grows, it's useful to be able to take an application apart for debugging, testing, development, etc.

The goal for modeling an intent is to describe _what_ the user wants rather than _how_ to do it. As an example, the resulting intent for clicking the checkbox on a todo item might look something like one of the following:
- `[:update $complete? not]`
- `[:toggle $complete?]`
- `[::toggle-complete $todo]`

I'm still not sure which version should be preferred and it might depend on the application. Let's consider some of the tradeoffs.

Pros and cons for the `:update` version:
- 👍 More direct
- 👎 Highly coupled to a specific implementation
- 👎 Harder to instrument and wrap
- 👍 Doesn't require a specific effect handler

Pros and cons for the `:toggle` version:
- 👍 Direct
- 👍 Not coupled to a specific implementation
- 👎 Slightly harder to instrument and wrap
- 👍 Doesn't require a specific effect handler

Pros and cons for the `::toggle-complete` version:
- 👎 Indirect
- 👍 Not coupled to a specific implementation
- 👍 Easier to instrument and wrap
- 👎 Requires a specific effect handler

Just counting the number of 👍 and 👎 may make it seem like `:toggle` is the winner, but I think the answer is more subtle. 

For most use cases, `:toggle` strikes a good balance between cohesion, decoupling, and directness.

However, for small apps, it can be useful to have the implementation colocated with the component. For large apps, it my be useful to have more decoupling and more hooks for instrumentation, debugging, and cross-cutting concerns.

<!-- # Components -->



<!--   - creating new components -->
<!--   - composing components -->
<!--   - collections -->
<!--   - context -->

# Defui Components

Components are sugar. You can build user interfaces with only regular functions (this is more or less what [elm](https://elm-lang.org/) does). The purpose of `defui` is to package some common features together for ease of use. `defui` components will work with non `defui` components and even within other state management systems.

## Background

When designing an interface, I like to start with pencil and paper. Stepping away from the keyboard helps focus on the fundamentals of the problem rather than whatever programming environment I'm in. If you imagine what a pencil and paper design for a todo list component might look like, it might look something like:

```
(add todo) | type here | 

- [X] drink coffee
- [ ] write documentation
- [ ] write tests
```

Given only this shorthand, most programmers could turn this into a functioning prototype. Since that's the case, would it be possible to have a sufficiently smart compiler (or macro) turn the shorthand into a working program? If not, what details are missing from the shorthand?

That's the background for the inspiration of `defui`. Here's what the above todo list looks like in membrane:

```clojure
(defeffect :add-todo [$todos next-todo]
  (dispatch! :update $todos conj {:description next-todo
                                  :complete? false}))

(defui my-todo-component [{:keys [todos next-todo]}]
  (ui/vertical-layout
   (ui/horizontal-layout
    (basic/button {:text "add todo"
                   :on-click (fn []
                               [[:add-todo $todos next-todo]
                                [:set $next-todo ""]])})
    (basic/textarea {:text next-todo}))

   (apply
    ui/vertical-layout
    (for [{:keys [complete? description]} todos]
      (ui/horizontal-layout
       (basic/checkbox {:checked? complete?})
       (basic/textarea {:text description}))))))

(skia/run (component/make-app #'my-todo-component {:todos [{:complete? true
                                                            :description "drink coffee"}]
                                                   :next-todo ""}))
```
It's definitely more verbose, but it's not too bad. One of the key ideas behind `defui` is that the checkbox call, `(basic/checkbox {:checked? complete?})`, doesn't require writing any code, callback, or specific event handler in order to know how to update the `:complete?` boolean of the todo item.


## Component State

On top of trying to match the mental model of how we tend to think about user interfaces, there were few additional design goals:

1. No side effects in view or event functions
2. Receive data only through function arguments
    * corollary: No hidden/local state and don't use global state

Along with functional programming techniques, the clojure ecosystem provides strong support for avoiding side effects. The main challenge when designing `defui` was making it easy to support all the state required to build a real user interface without cheating. User interfaces do have lots of state. It's worth taking a deeper look at the kinds of state user interfaces commonly require.

For our purposes, we'll separate the kinds of state we require into three dimensions:
1. essential/incidental state
2. public/private state
3. contextual/non-contextual state


To make things a bit more concrete, we'll use a text input as our example component. If you're ever trying to evaluate a UI state management solution, I highly recommend implementing a text input from scratch. I know that most platforms provide a builtin text input option, but implementing the text input yourself should provide a good testing ground for the support of the different types of state.

## Essential/Incidental State

Usually, the essential state for a text input is the text itself. Typically, the cursor position, text selection, focus, etc. are all incidental. As long as the user can edit the text, that's the only thing the application cares about. However, sometimes the application does care about the text selection, focus, or the cursor. A key insight is that component properties aren't inherently incidental. Whether or not a property is incidental depends on the application! It is a mistake for the component author to decide which state is incidental and which state is essential.

<!-- Membrane handles this by... -->

## Public/Private State

Another lens to view state is whether or not the state is public or private. A component author may want to have some state, but not make any promises about its structure or that it will remain consistent across versions of the component. For our text input example, the cursor, text selection, and focus might be public, but the text input might want to have a private internal representation of the text, data about the current mouse position used for text selection, internal timing data related to double clicks, spellcheck, etc. By definition, private state is incidental, but public state may or may not be incidental depending on the use case. However, even if some state is incidental, it may be useful for the application to treat the state as an opaque value for debugging, testing, or otherwise.

<!-- Membrane handles this by... -->

## Contextual State

Contextual state is shared for a whole branch of a component tree. The most common example is focus. Only one component can have focus and receive key events. Since contextual state is shared across many components, it shouldn't be overused. Too much contextual state can have negative performance implications. Similar to global state, contextual state can also introduce unnecessary coupling and make it harder to reason about components.

However, supporting contextual state is important because sometimes it is the right tool for the job. Some other examples of contextual state:
- For drag and drop, there can only be one currently selected drag object and any component could potentially be a drop target. 
- Modals and right click menus can have at most one instance visible per window.
- Contextual state can be useful for passing styling info to a whole tree of components (eg. dark mode)


## Defui Limitations

Defui tries to reduce boilerplate by wiring incidental state and context. It does this by automatically passing any incidental state to calls to other `defui` components as well as replacing any binding that that starts with a `$` with a corresponding reference. As long as a binding derives from a component's arguments, a valid reference should be possible. However there are a few limitations.

Each binding derived from a component argument can only be derived one function call at a time.

```clojure
(defui my-component [{:keys [my-prop]}]
  (let [;; ok
        foo (:foo my-prop)

        ;; multiple calls not supported
        baz (:baz (:biz my-prop))]

    (my-other-component {:foo foo
                         :bar bar
                         :baz baz})))
```

The `defui` macro supports many common data operations, but it's currently not an open set. The following data operations are supported:

```clojure
(defui my-component [{:keys [my-prop]}]
  (let [;; these work
        foo (:foo my-prop)
        foo (:foo my-prop :my-default)
        foo (nth my-prop 0)
        foo (nth my-prop 0 :my-default)
        foo (get-in my-prop [:foo :bar])
        foo (get-in my-prop [:foo :bar] :my-default)
        foo (take 42 my-prop)
        foo (drop 42 my-prop)
        foo (or my-prop 42)
        
        ;; destructuring also works
        {foo :foo
         bar :bar
         [a b & other-foos] :foos} my-prop]
    (my-other-component {:foo foo
                         :bar bar
                         :a a
                         :b b
                         :other-foos other-foos})))
```

In addition to the above data operations, there is special support for the `for` comprehension.

```clojure
(defui my-component [{:keys [my-prop]}]
  (apply
   ui/vertical-layout
   ;; works
   (for [[k v] my-prop
         :let [{a :my-a
                b :my-b} v]
         :while a]
     (my-other-component {:v v
                          :a a
                          :b b}))))
```

The `map` function is not supported.

```clojure
  
(defui my-component [{:keys [my-props]}]
  (apply
   ui/vertical-layout
   ;; doesn't work
   (map other-component my-props)))
```

The `defui` macro detects calls to other `defui` components to thread the incidental state by looking at the metadata of the resolved var. That means that if symbol that represents the component function wouldn't be resolved to the var, then the function call will be treated like a normal function call and the incidental state won't be added.

```clojure
(defui my-component [{:keys [my-prop]}]
  (let [f basic/textarea]
    ;; incidental state not added
    (f {:text my-prop})))
```

In some cases, the component won't be known (or even exist) at compile time. In those cases, then all of the state can be provided manually. Better support is  [planned](https://github.com/phronmophobic/membrane/issues/61).

## Advanced Usage

  Defui components are designed to be used mostly like regular functions, while also taking care of incidental state and context so you don't have to. For some less common use cases, it's useful to reuse existing components, but manually wire them together.

### Components as Values

Functions defined by `defui` return records. They can be used similar to normal map-like values. For example, using the definition from `membrane.example.todo/todo-app`, the component can be inspected, reused, measured, copied, drawn, etc.

```clojure

(def my-app (membrane.example.todo/todo-app
             {:todos
              [{:complete? false
                :description "first"}
               {:complete? false
                :description "second"}
               {:complete? true
                :description "third"}]
              :next-todo-text ""}))

;; find a mouse coordinate that toggles a todo
(defn toggle-checkbox? [[intent-type & _]]
  (= :membrane.basic-components/toggle
     intent-type))
(->> (for [x (range 300)
           y (range 300)]
       [x y])
     (some (fn [pos]
             (when (some toggle-checkbox?
                         (ui/mouse-down my-app pos))
               pos))))
;; [26 72]

;; save image of my-app with 10px padding
(skia/save-image "my-app.png" (ui/padding 10 my-app) )
;; same as above, but mark the first todo complete
(skia/save-image "my-app2.png" (ui/padding 10 (update-in my-app [:todos 0 :complete?] not)))

;; Open a window with the current view of the app
;; side by side with the same app having a different
;; filter selected
(skia/run
  (constantly
   (ui/horizontal-layout
    my-app
    (assoc my-app :selected-filter :active))))


;; Find text in my-app
(->> (tree-seq ui/children ui/children my-app)
     (keep :text))
;; ("Add Todo" "Add Todo" "" "" "" "" "all" "active" "complete?" "first" "first" "first" "first" "second" "second" "second" "second" "third" "third" "third" "third")

```

<!-- ### Dynamic Components -->



<!-- ### Higher Order Components -->




### Explicitly Providing References

The way the `defui` macro produces references is that it keeps track of how bindings are derived from function arguments. For this to work, the reference path for each argument must be provided to each call to a `defui` component. The path key for a given property is just the keyword with a `$` prefix. For example, for a call to `(basic/textarea {:text "foo"})`, the reference key is `:$foo`. The `defui` macro will add this extra info for you, but it may be useful to provided it explicitly, `(basic/textarea {:text "foo" :$text foo-ref})`.

### Incidental State Identity

When a property isn't explicitly provided to a `defui` component call, then it is assumed to be incidental state. The incidental state will automatically be added for you by the `defui` macro. However, the state must be stored _somewhere_. Further, the place where the state is stored must be stable so that when the view tree is reconstructed, the same state gets passed to the same logical component even if some views have shifted a bit. In react, the process of matching local state with a component is called [reconciliation](https://reactjs.org/docs/reconciliation.html). Membrane doesn't have local state in the same way as React, but the process of matching incidental state with a component is fairly similar. Generally, react reconciles components based on the structure of the DOM. Using the structure of the DOM is fairly crude, but react provides another option for find a logically stable way to reconcile data, "The developer can hint at which child elements may be stable across different renders with a key prop." This is essentially what membrane does for you. To reconcile component state across renders, membrane generates a key based on the identities of the properties that are explicitly provided to a component.

For example, if you have a component that uses a textarea like `(basic/textarea {:text s})`, then the key for the incidental state you don't care about (like cursor position, selection, etc), is logically equivalent to the reference for `s`, since that's the property that was explicitly provided. If the textarea shifts in the view tree, it will still get the same incidental state associated with the identity of `s`.

Usually, this works as expected, but there are some cases where explicitly providing a key is needed. If you run the following example, you'll notice that all of the buttons share the same hover state. The reason is that they all have the same key for the incidental hover state. Since every button was passed the same exact props, membrane uses the same key for all of them.

```clojure
(defui my-component [{}]
  (apply
   ui/vertical-layout
   (for [i (range 10)]
     (ui/horizontal-layout
      (basic/button {:text "send"})
      (ui/label (str "num: " i))))))

(skia/run (component/make-app #'my-component {}))
```
The fix in this example is to explicitly provide a key to differentiate the different buttons. The key that we'll use to fix is `[:button i]`. The key is a little arbitrary. Logically, it should be related to `i` since that's the related value, but I also like to provide a related name (`:button` in this example). It usually doesn't matter, but it can be useful for debugging as well as prevent collisions if our component grows and adds another row of buttons somewhere.

```clojure
(defui my-component [{}]
  (apply
   ui/vertical-layout
   (for [i (range 10)
         :let [my-key [:button i]]]
     (ui/horizontal-layout
      (basic/button {:text "send"
                     :extra (get extra my-key)})
      (ui/label (str "num: " i))))))

(skia/run (component/make-app #'my-component {}))
```

# Life Cycle



React.js has various lifecycle methods that get called when "mounting" as well as updating state. One major difference between membrane components and react components is that react components are only useful for passing to the react framework. React components aren't meant to be called except within a very specific context managed by react. In contrast, membrane components are just regular, pure functions that can be called whenever, without any setup or particular context.

That's not to say that lifecycle methods don't make sense when using membrane, but their role shifts. It's also important to make sure that life cycle handlers don't impact usages outside of rendering.

There's no builtin support for lifecycle methods in membrane. I'm still gathering use cases and trying to figure out what best practices might look like. However, I have some ideas on how to cover common uses cases.

## Effects on State Transition

React has various life cycle methods that trigger on state updates, <https://reactjs.org/docs/react-component.html#updating>. Generally speaking, effects or updates that trigger on state transitions should be enforced by the application regardless of whether a user interface (or interfaces) are visible. Coupling the update with a particular UI component is bad practice. Below are some recommended ways to implement effects on state transitions:
- Add a watch (ie. `add-watch`) to your app's state atom. Perform the dependent computations in the watch callback when the relevant source data changes.
- Provide your own handler to `membrane.component/make-app` to enforce constraints.

## Effects on "Mounting" or "Unmounting"

In some cases, you may want to initiate effects either right before or after a component will be viewed by the user (eg. lazy image loading). It doesn't make sense to perform these functions when a component is called because components can be called not only when being displayed, but whenever (eg. testing, debugging, offscreen rendering, development, tooling, etc.). 

Below is an example of how a mounting event could be implemented with membrane. Implementing unmounting would follow the same basic pattern and is left as an exercise for the reader.

```clojure
(defprotocol IOnPresent
  :extend-via-metadata true  
  (-on-present [elem]))

(extend-protocol IOnPresent
  nil
  (-on-present [this] nil)

  #?(:clj Object
     :cljs default)
  (-on-present [this]
    (mapcat #(-on-present %) (ui/children this))))


(defn present [elem]
  (-on-present elem))

(defn on-present [on-present elem]
  (vary-meta elem
             assoc `-on-present (fn [_]
                                  (on-present))))
```

Example usage:
```clojure

(defui lazy-image [{:keys [url image-status]}]
  (on-present
   (fn []
     [[::load-image $image-status url]])
   (let [{:keys [status
                 img]} image-status]
     (if img
       (ui/image img)
       (ui/label (if status
                   status
                   "loading..."))))))

(defeffect ::load-image [$image-status url]
  (dispatch! :update $image-status
             (fn [image-status]
               (if (and image-status
                        (= url (:url image-status)))
                 image-status
                 {:url url
                  :fut (future
                         (println "downloading" url)
                         (dispatch! :update $image-status assoc :status "downloading")
                         (with-open [is (clojure.java.io/input-stream url)
                                     out (java.io.ByteArrayOutputStream.)]
                           (clojure.java.io/copy is out)
                           (dispatch! :update $image-status
                                      assoc
                                      :status "done!"
                                      :img (.toByteArray out))))}))))

(def lazy-image-atm (atom {:url "https://clojure.org/images/clojure-logo-120b.png"}))


(def my-lazy-image (lazy-image @lazy-image-atm))

@lazy-image-atm
;; {:url "https://clojure.org/images/clojure-logo-120b.png"}

(present my-lazy-image)
;; ([:membrane.example.todo/load-image [(keypath :img)] "https://clojure.org/images/clojure-logo-120b.png"])

(def lazy-image-handler (component/default-handler lazy-image-atm))
(lazy-image-handler (present my-lazy-image))

(-> @lazy-image-atm
    :image-status
    (select-keys [:img :status]))
;; {:img #object["[B" 0x6bd0b106 "[B@6bd0b106"], :status "done!"}

```

There are various ways to support on `on-present` in your app.

### Wrap App

The backend `run` functions (eg. `membrane.skia/run`) accept a function that receives no arguments and returns a view. The common usage of `defui` apps is to build an app using `membrane.component/make-app` and pass that to the `run` function. The `present` event can be triggered by wrapping the app with a call to `present` before returning the resulting view.


An example of this method is below:

```clojure
(defui test-lazy-image [{:keys [show? url next-url]}]
  (ui/vertical-layout
   (ui/horizontal-layout
    (basic/button {:text "download"
                   :on-click (fn []
                               [[:set $url next-url]])})
    (basic/textarea {:text next-url}))
   (basic/checkbox {:checked? show?})
   (if (and show? url)
     (lazy-image {:url url}))))

(def lazy-image-test-atm (atom {}))
(def test-lazy-image-app
  (let [handler (component/default-handler lazy-image-test-atm)
        app (component/make-app #'test-lazy-image lazy-image-test-atm handler)]
    (fn []
      (let [ui (app)]
        (handler (present ui))
        ui))))

(skia/run test-lazy-image-app)
```

The key features of this design are:
- Components like `lazy-image` are still pure.
- Effects can be tested independently of the view
- Contexts like debugging, testing, offscreen rendering, etc. are still supported.

#### Some Caveats
There are currently no guarantees to how how many times a `run` function will the view function for each render. In practice, most backends can be expected to call the view function once per render. Depending on how best practices develop, it may make sense to require stronger guarantees from backends as to when and how often view functions are called. Another possibility is that the tools for building a backend might be simplified so it's easier for each application to build a backend that works exactly as needed while still offering good defaults.

The processing of the `present` event will happen on the main thread. However, it would be easy for an application to move the `present` event to background thread if desired.

### add-watch

Instead of augmenting the view function passed to a backend `run` function, another option is to trigger the `present` event within an `add-watch` callback.

Here's what that looks like.

```clojure

(def lazy-image-test-atm (atom {}))
(def lazy-image-handler (component/default-handler lazy-image-test-atm))
(def test-lazy-image-app (component/make-app #'test-lazy-image lazy-image-test-atm lazy-image-handler))

(add-watch lazy-image-test-atm ::present
           (fn [k ref old new]
             (when (not= old new)
               (let [intents (present (-> (test-lazy-image-app)
                                          (assoc :state new)))]
                 (lazy-image-handler intents)))))

(skia/run test-lazy-image-app)
```

Differences between the `add-watch` approach and the app wrap approach:
- The `add-watch` method runs off the main thread.
- The `add-watch` runs based solely off the state and doesn't require that the app is being run or viewed by a user.
- The `add-watch` approach can potentially make updates before a frame is rendered to avoid flickering.

### Summary

Hopefully, one of the two example approaches provides a solution for your life-cycle event needs. If not, hopefully it shows an approach to implementing the right solution.

# Handling Errors in Rendering and Drawing

Errors during layout and rendering can be caught using normal try/catch, but there may be cases where rendering succeeds, but an error is thrown during drawing that the application wants to catch.

The way to catch drawing exception is by wrapping a view with `membrane.ui/try-draw`. The `try-draw` function accepts an element to try and draw and a function that is called if an exception occurs. The function callback is weird by membrane standards because it is expected to be an impure function. It is called with two arguments, a `draw` function that can be used to draw an element and the exception that was thrown.

Example:
```clojure
(backend/run
  (constantly
   (ui/try-draw 
    (ui/rectangle "NaN" "NaN" )
    (fn [draw e]
      (draw (ui/label (ex-message e)))))))
```

Notice that the callback doesn't return a label, but draws it by calling `draw`.

<!-- # text handling -->
# Backends

## Skia

The most polished backend is the `membrane.skia` backend. However, it requires native dependencies and currently only runs on Mac OSX and Linux. This is usually the backend I recommend if you're building a user interface for yourself.

## java2d/Swing

The second most polished backend is the `membrane.java2d` backend. It works on all desktop platforms and doesn't require any native dependencies. This is the backend I recommend if you're building a user interface for distributing to others.

## WebGL

The webGL backend is the most polished backend that runs in a web browser. However, since there are several high quality clojurescript libraries for building user interfaces on the web, membrane development on the desktop backends is prioritized over backends that run in the browser. Having said that, the webGL backend will continue to be fully supported.

## Other Backends

All of the other backends are experimental. If you'd like a particular backend to receive more attention, please reach out!

## Multi Backend Apps

Most of the membrane apps run on multiple backends with little to no tweaking. Making apps work exactly the same on all platforms is a non-goal, but behavior should be consistent across backends when reasonable. Additionally, making it easy for applications to fully leverage the underlying platform for each backend is a goal.

# Thread Safety and Background Threads

All public functions should be thread safe. Nothing special is required to do work in the background.

# Optimization

The goal for membrane is to act just like a regular clojure library. Layout, event handling, and effect handling are decoupled and can be run independently without any special setup.

One of the main tools for optimizing membrane user interfaces is memoization. Typically, the user is interacting with a subset of the screen at any given moment and everything else on the screen is mostly static. It's not like video games where every entity on the screen might be updated every frame. That makes memoization an effective option for user interfaces.

Membrane does some amount of memoization for you under the hood at the component level. If you're having performance problems, the first tip to try is refactoring large components into smaller components.

## Drawing

The three main techniques for optimizing drawing bottle necks are:

{{quote}}1{{/quote}}. Cache complex draws using `membrane.ui/->Cached`.

If there is a section of the view tree that does a lot of drawing, but is mostly static, then wrapping the element with `ui/->Cached` can make a huge difference. The drawing implementation for `ui/->Cached` will draw the element to an image buffer. If the same element needs to be drawn again, it will instead just redraw the image buffer in the cache.

Drawing is usually pretty fast and overuse of `ui/->Cached` can make drawing performance worse, so use judiciously.

{{quote}}2{{/quote}}. Provide an optimized drawing implementation

Backend implementations are allowed to do whatever they want, but they generally all follow the convention of creating an IDraw protocol (eg. `membrane.java2d/IDraw`) that provides drawing implementations that match the underlying graphics library. An optimized drawing implementation can be provided by creating a new view type that implements the corresponding IDraw protocol(s). Note that this method may couple an application to a particular graphics backend which may or may not be worth the trade-off.

{{quote}}3{{/quote}}. Background Rendering

Background rendering is similar to using `ui/->Cached`, but allows rendering to happen off the main thread. Most backends offer a `save-image` function (eg. `membrane.java2d/save-image`) that will save an image to a file. The file can be viewed with `(ui/image image-path)`. Additionally, some backends provide a function to draw directly to an in memory image buffer (eg. `membrane.java2d/draw-to-image`).

Background rendering works well for dynamic or complicated drawings that might take several frames to compose. Background rendering is usually used in conjuection with some placeholder to show the user while the view is being built.


## Event Handling

Each event has a corresponding event function (eg. `membrane.ui/mouse-move`, `membrane.ui/mouse-down`) which means event handling can be profiled in isolation. Just call the relevant event function in a loop while running the profiler (eg. `(mouse-move my-view my-synthetic-event)`).

Membrane has a few optimizations for event handling, but most of the event functions can be thought of as having O(n) complexity. That means if there is a part of the view that has lots of child nodes, it may slow down all event handling, even if many of the child nodes don't have any event handlers attached.

The main culprit of laggy UIs is slow `mouse-move` event handlers. Since `mouse-move` events happen at a more frequent pace than other events, slow `mouse-move` event handlers can have a disproportionate impact on performance.

Another potential performance pitfall is overuse of `mouse-move-global` events. Whereas `mouse-move` will only search views under the cursor, `mouse-move-global` will search the whole view tree.

If it is known ahead of time that a complex view doesn't respond to any events, then the view can be wrapped with `membrane.ui/no-events`, which will effectively ignore that subset of the view tree when handling events.

If a complex view does need to respond to events, then it is easy to replace the event handler for that view by simply wrapping it in the corresponding event handler. A smarter algorithm can then be used to handle events for that subset of the tree. As an example, treemap-clj replaces the `mouse-move` and `mouse-down` events with an implementation that relies on a pre-calculated rtree,  [source](https://github.com/phronmophobic/treemap-clj/blob/master/src/treemap_clj/view.cljc#L661).

Below is a small example of replacing the event handler for a grid with a more efficient implementation.

```clojure

(defn wrap-grid-events [cell-size grid]
  (ui/on
   :mouse-move
   (fn [[mx my :as mpos]]
     [[:grid-hover (long (/ mx cell-size)) (long (/ my cell-size))]])
   :mouse-down
   (fn [[mx my :as mpos]]
     [[:grid-select (long (/ mx cell-size)) (long (/ my cell-size))]])
   ;; ignore all other events for this view
   (ui/no-events grid)))


;; usage
(def cell-size 2)
(def grid
  (ui/->Cached
   (vec
    (for [x (range 500)
          y (range 500)
          :when (and (even? x)
                     (even? y))]
      (ui/translate (* x cell-size) (* y cell-size)
                    (ui/filled-rectangle [0 0 0] cell-size cell-size))))))

(def grid-ui
  (ui/on
   :grid-hover
   (fn [gx gy]
     (prn "hover" gx gy))
   :grid-select
   (fn [gx gy]
     (prn "select" gx gy))
   (wrap-grid-events cell-size grid)))

(skia/run (constantly grid-ui))

```

### Key Events

Unlike mouse events which can be filtered by only searching views under the current mouse position, the only way to know if a view responds to key events is to search the whole tree to see if any view provides one of the key event handlers. Under the hood, membrane components will cache which components respond to key events and shortcut on future events. If your user interface doesn't add key event handlers, then this tip isn't something you need to worry about. However, if you do add key events, then you probably want to use either `membrane.ui/maybe-key-event` or `membrane.ui/maybe-key-press`. These macros will help membrane components cache which components respond to key events.

```clojure
;; don't do this
(defn bad-view [focus?]
  (ui/on
   :key-press
   (fn [s]
     (when focus?
       [[:do-something!]]))))

;; do this instead
(defn good-view [focus?]
  (ui/maybe-key-press
   focus?
   (ui/on
    :key-press
    (fn [s]
      [[:do-something!]]))))
```

## Optimizing Layout

Layout is pure function of view -> view with elements moved around. As mentioned, the main tools for improving layout performance are memoization, using more efficient algorithms for the specific job, or moving the layout work off the main thread.



<!-- # Internals? -->
<!-- # Comparison Table vs other UI Frameworks -->
<!-- ## re-frame -->
<!-- ## react -->
<!-- ## cljfx -->
<!-- ## humbleUI? -->
<!-- ## seesaw -->
<!-- ## other? -->
