# Distributing your app

Regardless of how you distribute your app, you'll probably want to use `run-ui-sync` to prevent the jvm from shutting down. Below is an example of `-main`.

```clojure
(defn -main [ & args]
  (let [initial-state {:foo "bar"}]
    (run-ui-sync #'app-root initial-state)))
```


## Uberjar

You can always distribute your app as an uberjar. It will require java 1.8+ to be installed on the target platform, but it will work regardless of which platform the app was developed on.


## Mac OS X .app

For distrbuting to Mac OS X, appbundler does a great job of turning your uberjar into a .app.

https://github.com/TheInfiniteKind/appbundler/
