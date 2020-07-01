(ns membrane.eval
  (:require [cljs.js :as cljs]
            [cljs.core.async :refer [put! chan <! timeout dropping-buffer promise-chan]
             :as async])
  
  (:import [goog.net XhrIo]
           goog.string))

(defn wrap-js-eval [resource]
  (try
    ;; (println (:source resource))
    ;; {:value (cljs/js-eval resource)}
    (cljs/js-eval resource)
    (catch js/Object e
      ;; (.log js/console e)
      {:error e})))

(let [cache (atom {})]
  (defn get-file [url cb]
    (if (contains? @cache url)
      (cb (get @cache url))
      (.send XhrIo url
             (fn [e]
               (let [response (.. e -target getResponseText)]
                 (swap! cache assoc url response)
                 (cb response)))))))

(def fake-spec

  "
  (ns com.rpl.specter)

  (declare ATOM ALL FIRST LAST MAP-VALS META)

  ")


(def macros-clj
  "(ns membrane.macros)



(defmacro test-macro [& body]
  `(do
     (prn \"comp\" 42 ~@body)
     [42 ~@body]))
"
)


(defmacro test-macro [& body] `(do (prn "comp" 42 ~@body) [42 ~@body]))



(defn default-load-fn [{:keys [name macros path] :as m} cb]
  (prn "trying to load" m)
  ;; (assert (not macros) "can't compile macros because I'm not serving them. -adrian")

  (if (= path "membrane/macros")
    {:lang :clj
     :cache true
     :source macros-clj}
    (if (not= (.indexOf path "spec") -1)
      (do
        (cb {:lang :clj
             :cache true
             :source fake-spec}))
      (if-let [path ({"goog/string" "goog/string/string.js"
                      "goog/string/StringBuffer" "goog/string/stringbuffer.js"
                      "com/rpl/specter/util_macros" "/util_macros.clj"
                      "goog/object" "goog/object/object.js"
                      "goog/array" "goog/array/array.js"} path)]
        (let [url (if (.startsWith path "/" )
                    path
                    (str "/js/compiled/out.autouitest/" path))]
          (get-file url
                    (fn [source]
                      (println "got url" url)
                      (cb {:lang (if (.endsWith url ".js")
                                   :js
                                   :clj)
                           :cache true
                           :source source}))))
      
        (let [macro-map {"cljs/tools/reader/reader_types" "reader_types.clj"
                         "cljs/reader" "reader.clj"
                         "cljs/env/macros" "macros.clj"
                         "cljs/analyzer/macros" "macros.clj.1"}]
          (if (and macros
                   (macro-map path))
            (let [url (str "/"
                           (macro-map path))]
              (get-file url
                        (fn [source]
                          (println "got url" url)
                          (cb {:lang (if (.endsWith url ".js")
                                       :js
                                       :clj)
                               :cache true
                               :source source}))))
            (if (or macros
                    (#{"com/rpl/specter"
                       "com/rpl/specter/protocols"
                       "com/rpl/specter/impl"
                       "com/rpl/specter/navs"
                       "cljs/analyzer/api"
                       "cljs/analyzer"
                       "cljs/env"
                       "cljs/tagged_literals"
                       "membrane/ui"
                       "membrane/component"
                       } path))
              (let [url (str "/js/compiled/out.autouitest/" path ".cljc")]
                (get-file url
                          (fn [source]
                            (println "got url" url)
                            (cb {:lang :clj
                                 :cache true
                                 :source source}))))
            
              (let [url (str "/js/compiled/out.autouitest/" path ".cljs")]
                (get-file url
                          (fn [source]
                            (println "got url" url)
                            (cb {:lang :clj
                                 :cache true
                                 :source source}))))

              )))

        )))
  )

(def default-compiler-options {:source-map true
                               :ns 'membrane.autoui
                               ;; :context :statement
                               ;; :verbose false
                               :load default-load-fn
                               :def-emits-var true
                               :eval wrap-js-eval})



(defn eval-async
  ([form]
   (eval-async (cljs/empty-state) form
               default-compiler-options))
  ([state form]
   (let [ch (promise-chan)]
     (try
       (cljs/eval state form
                  default-compiler-options
                  #(put! ch %))
       (catch js/Object e
         (put! ch {:error e})))
     ch))
  ([state form opts]
   (let [ch (promise-chan)]
     (try
       (cljs/eval state form opts
                  #(put! ch %))
       (catch js/Object e
         (put! ch {:error e})))
     ch)))



