## Terminal UIs

Terminal UIs can be created using the `membrane.lanterna` backend. For a full example, check out the example projects:
- https://github.com/phronmophobic/terminal-todo-mvc
- Using re-frame https://github.com/phronmophobic/membrane-re-frame-example
- Using Fulcro https://github.com/phronmophobic/membrane-fulcro

### Differences

Compared to most of the other backends, there are a few major differences:
- Terminal UIs have an integer coordinate system. Most other backends have a floating point coordinate system
- Terminals don't really have a way to draw multiple elements on top of each other. There is a foreground and a background, but the background is just a solid color
- Terminals often have a limited color palette
- Text is always monospaced and 1 unit high

Many of the utilities found in `membrane.ui` can be used as-is for building terminal UIs. For example: `bounds`, `origin`, `vertical-layout`, `on`, `translate`, `with-color`, and others all work as you would expect. However, some of the `membrane.ui` primitives don't really make sense for terminals UIs. For example, `label`, `checkbox`, `rectangle`, and some others will not work for terminal user interfaces because of the differences from most other backends. For primitives in `membrane.ui` that don't make sense for terminal UIs, you can find counterparts in the `membrane.lanterna` namespace.

Supporting user interfaces that simultaneously work in terminals and with other backends is a non-goal. While it might be tempting to try to make the exact same primitives work for these two different mediums, the differences would make the development experience unfun in the general case. However, the primitives in `membrane.ui` and `membrane.lanterna` are just regular clojure data structures. Building a reduced set of elements for a constrained use case that work in terminal and for desktop is possible, but is left as an exercise for the reader.

### Repl Driven Development

Repl driven development requires a config step since the default repl will consume `System/in` and `System/out`.

Below is the setup for cider/nrepl. If you need support for a different REPL, please file an issue or drop a request in [#membrane](https://clojurians.slack.com/archives/CVB8K7V50) on the clojurians slack.

#### Cider/nREPL

##### 1. Start your repl

To setup a repl driven workflow, start up the nrepl server in the terminal that will be displaying your UI. Probably something like:
```sh
clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version "0.8.3"} cider/cider-nrepl {:mvn/version "0.25.6"}}}' -M:dev:nrepl --middleware '["cider.nrepl/cider-middleware" "membrane.lanterna/preserve-system-io"]' --port 7888
```
This command will start an nrepl server on port 7888. The default nrepl middleware will replace `System/in` and `System/out`. Add the `membrane.lanterna/preserve-system-io` to store `System/in` and `System/out` in the respective vars: `membrane.lanterna/in` and `membrane.lanterna/out`.

##### 2. Connect to your repl
To connect to nrepl server in emacs, use `M-x cider-connect` and pass `localhost` as the host and `7888` for the port.

##### 3. Run your UI

To run a UI that uses the correct I/O streams, pass the `:in` and `:out` as options like so:
```clojure
(lanterna/run #'app-root
      {:in membrane.lanterna/in
       :out membrane.lanterna/out})
```

##### Stopping/Restarting your UI

In some cases, you may want to restart the UI or display a different UI. You can stop a UI by passing a channel as the `:close-ch` option that will stop rendering when it receives a value.

```clojure
(require '[clojure.core.async :as async])

;; Rich comment block
(comment
  ;; start the ui
  (do
    (def close-ch (async/chan))
    (lanterna/run #'app-root
      {:in membrane.lanterna/in
       :out membrane.lanterna/out
       :close-ch close-ch}))

  ;; Some time later, stop the UI. You can call `lanterna/run` again to start a new UI.
  (async/close! close-ch)
  
  ,)
```










