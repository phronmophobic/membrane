(ns membrane.component
  #?(:cljs (:require-macros [membrane.component :refer [defui path-replace-macro]]))
  (:require ;; [clojure.core.async :refer [go put! chan <! timeout dropping-buffer promise-chan]
   ;;  :as async]
   [com.rpl.specter :as spec
    :refer [ATOM ALL FIRST LAST MAP-VALS META]]
   cljs.analyzer.api
   [membrane.ui :as ui :refer [defcomponent draw children bounds]]))

(def ^:dynamic *root* nil)


(def special-syms
  {'ATOM spec/ATOM
   'ALL spec/ALL
   'FIRST spec/FIRST
   'LAST spec/LAST
   'MAP-VALS spec/MAP-VALS
   'META spec/META
   'END spec/END})


(defn path->spec [elem]
  (cond
    (vector? elem)
    (mapv path->spec elem)

    (seq? elem)
    (let [arg (second elem)]
      (case (first elem)
        nth
        (spec/nthpath arg)
        
        get
        (spec/keypath arg)

        path
        (spec/path arg)

        raw-path
        (spec/path (path->spec arg))

        keypath
        (spec/keypath arg)

        filter
        (spec/filterer (if (keyword? arg)
                         (fn [x]
                           (get x arg))
                         arg))

        take
        (spec/srange-dynamic (constantly 0) (fn [coll]
                                              (min arg (count coll))))

        drop
        (spec/srange-dynamic (constantly arg)
                             (fn [coll]
                               (count coll)))

        nil->val
        (spec/nil->val arg)

        (assert false (str "unrecognized method " (first elem) elem) )))

    (contains? special-syms elem)
    (get special-syms elem)

    :else
    elem))

(defn parse-path [form]
  (delay

   (cond
     (symbol? form)
     [form ;;`(spec/path)
      nil
      ]

     (seq? form)
     (let [f (first form)]
       (case f

         (clojure.core/nth nth)
         [(second form)
          `(list (quote ~'nth) ~(nth form 2))]
         
         (clojure.core/get get)
         (case (count form)
           3
           [(second form)
            `(list (quote ~'keypath) ~(nth form 2))]
           4
           [(second form)
            `[(list (quote ~'keypath) ~(nth form 2))
              (list (quote ~'nil->val) ~(nth form 3))]])
         
         (spec/select-one)
         [(nth form 2)
          `(list (quote ~'path) ~(nth form 1))]

         (clojure.core/filter filter)
         [(nth form 2)
          `(list (quote ~'filter) ~(nth form 1))]

         (clojure.core/take take)
         [(nth form 2)
          `(list (quote ~'take) ~(nth form 1))]
         
         (clojure.core/drop drop)
         [(nth form 2)
          `(list (quote ~'drop) ~(nth form 1))]

         root-deref
         [nil
          `(list (quote ~'raw-path) ~(nth form 1))]

         (clojure.core/or or)
         [(nth form 1)
          `(list (quote ~'nil->val) ~(nth form 2))]

         ;;else
         (if (keyword? f) 
           [(second form)
            `(list (quote ~'keypath) ~f)]
           `[nil (list (quote ~'fn-call) (quote ~form))])))

     (or (vector? form)
         (string? form)
         (boolean? form)
         (keyword? form)
         (number? form)
         (map? form)
         (vector? form)
         (set? form)
         (nil? form))
     `[nil (list (quote ~'constant) ~form)]

     
     :else
     (assert false (str "expected symbol or seq got " form) )
     )))

(defn calculate-path [deps k]
  (loop [deps deps
         k k
         path []]
    (if-let [[new-deps get-path] (get deps k)]
      (let [[new-k step] @get-path]
        (recur new-deps
               new-k
               (if (some? step)
                 (conj path step)
                 path)))
      (vec (reverse (if k
                      (conj path `(list (quote ~'keypath) (quote ~k)))
                      path))))))




#?(:clj
    ;; we would like to just identify defui methods with metadata,
   ;; but since macros in clojure script won't have access to that metadata
   ;; we have to keep track elsewhere
   ;; this is the elsewhere
   (defonce special-fns (atom {})))


#?
(:clj
 (defn fully-qualified [sym]
   (if cljs.env/*compiler*
     (if-let [result (cljs.analyzer.api/resolve {:ns (get-in @cljs.env/*compiler* [:cljs.analyzer/namespaces (symbol (ns-name *ns*))])}  sym)]
       (:name result))
     (if-let [v (resolve sym)]
       (symbol (name (ns-name (.ns v))) (name (.sym v)))))))

#?
(:clj

 (defn path-replace
   ([form]
    (path-replace form {}))
   ([form deps]
    ;; (println "----")
    ;; (println (keys deps))
    ;; (println form)
    (cond
      (= '() form) form

      (seq? form)
      (let [first-form (first form)]
        (case (first form)
          (clojure.core/let let loop)
          (let [[letsym bindings & body] form
                _ (do
                    (assert (vector? bindings) "a vector for its binding")
                    (assert (even? (count bindings)) "an even number of forms in binding vector"))
                [deps new-bindings]
                (loop [bindings (seq (partition 2 bindings))
                       deps deps
                       new-bindings []]
                  (if bindings
                    (let [[sym val] (first bindings)
                          val (path-replace val deps)
                          val-path (parse-path val)
                          deps (assoc deps sym [deps val-path])]
                      ;; (println "new val" sym val)
                      (recur (next bindings)
                             deps
                             (into new-bindings [sym val])))
                    [deps new-bindings]))
                
                body (map #(path-replace % deps) body)]
            `(~letsym ~new-bindings ~@body))
          

          (for clojure.core/for)
          (let [[seq-exprs body-expr] (next form)
                [deps seq-exprs]
                (loop [seq-exprs (seq (partition 2 seq-exprs))
                       deps deps
                       new-seq-exprs []]
                  (if seq-exprs
                    (let [[sym val :as binding] (first seq-exprs)]
                      (if (symbol? sym)
                        (let [index-sym (gensym "index-")
                              new-val `(map-indexed vector ~val)
                              binding [[index-sym sym] new-val]
                              deps (assoc deps sym [deps (delay [val
                                                                 `(list (quote ~'nth) ~index-sym)])])
                              ]
                          (recur (next seq-exprs)
                                 deps
                                 (into new-seq-exprs binding)))
                        (recur (next seq-exprs)
                               deps
                               (into new-seq-exprs binding))))
                    [deps new-seq-exprs]))]
            (with-meta
              `(~'for ~seq-exprs
                ~(path-replace body-expr deps))
              (meta form)))

          (fori )
          (let [[seq-exprs body-expr] (next form)
                [deps seq-exprs]
                (loop [seq-exprs (seq (partition 2 seq-exprs))
                       deps deps
                       new-seq-exprs []]
                  (if seq-exprs
                    (let [[[index-sym sym] val :as binding] (first seq-exprs)]
                      (if (symbol? sym)
                        (let [new-val `(map-indexed vector ~val)
                              binding [[index-sym sym] new-val]
                              deps (assoc deps sym [deps (delay [val
                                                                 `(list (quote ~'nth) ~index-sym)])])
                              ]
                          (recur (next seq-exprs)
                                 deps
                                 (into new-seq-exprs binding)))
                        (recur (next seq-exprs)
                               deps
                               (into new-seq-exprs binding))))
                    [deps new-seq-exprs]))]
            `(~'for ~seq-exprs
              ~(path-replace body-expr deps)))

          for-with-last
          (let [[[x-sym prev-sym xs-sym] first-body rest-body] (next form)
                index-sym (gensym "index-")
                deps (assoc deps x-sym [deps (delay [xs-sym
                                                     `(list (quote ~'nth) ~index-sym)])])]
            `(let [s# (seq ~xs-sym)]
               (when s#
                 (let [~index-sym 0
                       ~x-sym (first s#)
                       first-elem# ~(path-replace first-body deps)]
                   (loop [~index-sym (inc ~index-sym)
                          s# (next s#)
                          ~prev-sym first-elem#
                          elems# [first-elem#]]
                     (if s#
                       (let [~x-sym (first s#)
                             elem# ~(path-replace rest-body deps)]
                         (recur (inc ~index-sym) (next s#) elem# (conj elems# elem#)))
                       elems#))))))

          for-kv
          (let [[seq-exprs body-expr] (next form)
                [deps seq-exprs]
                (loop [seq-exprs (seq (partition 2 seq-exprs))
                       deps deps
                       new-seq-exprs []]
                  (if seq-exprs
                    (let [binding-row (first seq-exprs)
                          [left-side val :as binding] binding-row]
                      (if (and (vector? left-side)
                               (every? symbol? left-side))
                        (let [[key-sym val-sym] left-side
                              deps (assoc deps key-sym [deps (delay [val
                                                                     `(list (quote ~'map-key) ~key-sym)])])
                              deps (assoc deps val-sym [deps (delay [val
                                                                     `(list (quote ~'keypath) ~key-sym)])])]
                          (recur (next seq-exprs)
                                 deps
                                 (into new-seq-exprs binding)))
                        (recur (next seq-exprs)
                               deps
                               (into new-seq-exprs binding))))
                    [deps new-seq-exprs]))]
            `(~'for ~seq-exprs
              ~(path-replace body-expr deps)))

          ;; this doesn't cover all of the binding forms
          fn
          (let [sigs (rest form)
                fn-name (if (symbol? (first sigs)) (first sigs) nil)
                sigs (if fn-name (next sigs) sigs)
                sigs (if (vector? (first sigs)) 
                       (list sigs) 
                       (if (seq? (first sigs))
                         sigs
                         ;; Assume single arity syntax
                         (throw (if (seq sigs)
                                  (str "Parameter declaration " 
                                       (first sigs)
                                       " should be a vector")
                                  (str "Parameter declaration missing")))))
                fnsym (first form)
                replaced-sigs (list
                               (apply concat
                                      (for [[bindings & body] sigs
                                            :let [deps (apply dissoc deps bindings)]]
                                        (do
                                          (apply list
                                                 bindings
                                                 (map #(path-replace % deps) body))))))]
            
            (apply list
                   (concat
                    [fnsym]
                    (when fn-name
                      [fn-name])
                    
                    replaced-sigs)))
          

          . form


          quote form

          var form

          catch form

          reify*
          form

          (let [full-sym (delay
                          (fully-qualified first-form))
                special? (if (symbol? first-form)
                           (if-let [m (or (meta (resolve first-form))
                                          (meta first-form))]
                             (:special? m)
                             (contains? @special-fns @full-sym)))]
            (if special?
              (let [args (into {} (map vec (partition 2 (rest form))))
                    fn-meta (get @special-fns @full-sym
                                 (or (meta (resolve first-form))
                                     (meta first-form)
                                     ))

                    arglists (:arglists fn-meta)
                    first-arglist (first arglists)
                    [ampersand arg-map] first-arglist

                    defaults (:or arg-map)
                    
                    ;; sort by explicit arguments since
                    ;; they are used by the key prefix
                    all-args (sort-by
                              (fn [sym]
                                [(not (contains? args (keyword sym)))
                                 (.startsWith (name sym) "$")])
                              (distinct
                               (concat (:keys arg-map)
                                       (->> args
                                            (map (comp symbol name first)))
                                       (->> (:keys arg-map)
                                            (map name)
                                            (map #(str "$" %))
                                            (map symbol)))))

                    binding-syms
                    (into {}
                          (for [arg all-args
                                :let [arg-name (name arg)]]
                            [arg (gensym (str arg-name  "-"))]))

                    keypath-prefix
                    (vec
                     (for [k (keys args)
                           :let [arg-name (name k)
                                 dollar-arg? (.startsWith arg-name "$")]
                           :when (or dollar-arg?
                                     (not (contains? args (keyword (str "$" (name k))))))]
                       (if dollar-arg?
                         (get binding-syms (symbol arg-name))
                         (symbol (str "$"
                                      (name (get binding-syms (symbol arg-name))) )))))

                    bindings (for [arg all-args
                                   :when (contains? binding-syms arg)
                                   :let [binding-sym (get binding-syms arg)
                                         arg-val
                                         (if (.startsWith (name arg) "$")
                                           (if-let [arg-val (get args (keyword arg))]
                                             arg-val
                                             (let [val-sym (get binding-syms (symbol (subs (name arg) 1)))]
                                               (symbol (str "$"  (name val-sym)))))

                                           (get args (keyword arg)
                                                (if (= 'context arg)
                                                  'context
                                                  (if (-> arg meta ::contextual)
                                                    `(get ~'context ~(keyword (name arg)))
                                                    `(get ~'extra
                                                          [~keypath-prefix
                                                           ~(keyword (str "$" (name arg)))]
                                                          ~(when (contains? defaults arg)
                                                             (get defaults arg))
                                                          )))))]]
                               [binding-sym arg-val])

                    new-args
                    (apply
                     concat
                     (for [arg all-args
                           :let [arg-name (name arg)
                                 arg-key (keyword arg-name)]]
                       (if (.startsWith arg-name "$")
                         [arg-key
                          (get binding-syms (symbol arg-name))]
                         [arg-key (get binding-syms arg)])))]
                (with-meta
                  `(apply ~first-form
                          ~(path-replace
                            ;; identity
                            `(let [~@(apply concat bindings)]
                               (vector ~@new-args ))
                            deps))
                  (meta form)))
              
              ;; else
              (with-meta
                (map #(path-replace % deps) form)
                (meta form))))
          ))

      (symbol? form)
      (let [form-name (name form)]
        (if (.startsWith form-name "$")
          (let [k (symbol (subs form-name 1))]
            (if (contains? deps k)
              (calculate-path deps k)
              form))
          form))

      (string? form)
      form

      (map? form)
      (into (empty form)
            (for [[k v] form]
              [(path-replace k deps)
               (path-replace v deps)]))
      
      
      (seqable? form) (into (empty form)
                            (map #(path-replace % deps) form))

      :default form)))






 )




#?
(:clj
 (defmacro path-replace-macro
   ([deps form]
    (let [deps (into {}
                     (for [[sym dep] deps]
                       [sym [{} (delay [nil dep])]]))
          new-form (path-replace form deps)]
      ;; (clojure.pprint/pprint new-form)
      new-form))
   ([form]
    (let [new-form (path-replace form)]
      ;; (clojure.pprint/pprint new-form)
      new-form))))




#?(:clj
   (defn doall* [s] (dorun (tree-seq seqable? seq s)) s))

(def component-cache (atom {}))
#?
(:clj
 (defmacro defui [ui-name args & body]
   (let [
         
         
         [ampersand arg-map] args
         ;; arg-syms (get arg-map :keys)

         args-map-sym (get arg-map :as (gensym "m-"))

         arg-keys (:keys arg-map)
         arg-keys (if (some #(= 'extra %) arg-keys)
                    arg-keys
                    (conj arg-keys 'extra))

         arg-keys (if (some #(= 'context %) arg-keys)
                    arg-keys
                    (conj arg-keys 'context))
         
         defaults (:or arg-map)

         arg-path-syms (for [arg-name arg-keys]
                         (gensym (str "arg-path-" (name arg-name) "-")))
         arg-path-bindings (mapcat                                     
                            (fn [arg-sym path-sym]
                              (let [$arg-key (keyword (str "$" (name arg-sym)))]
                                [path-sym
                                 ;; should be same as below with one less vector wrap
                                 (let [fn-arg-path `(get ~args-map-sym ~$arg-key [::unknown])]
                                   (if-let [default (get defaults arg-sym)]
                                     [fn-arg-path [`((quote ~'nil->val) ~default)]]
                                     fn-arg-path))
                                 #_(vec
                                    (concat
                                     [`(get ~args-map-sym ~$arg-key [::unknown])]
                                     (when-let [default (get defaults arg-sym)]
                                       [`((quote ~'nil->val) ~default)])))
                                 ]))
                            arg-keys
                            arg-path-syms)
         deps (into {}
                    (for [[arg-name path-sym] (map vector arg-keys arg-path-syms)]
                      [arg-name
                       [{}
                        (delay
                         [nil path-sym])]]))
         ui-arg-map
         (merge {:keys arg-keys
                 :as args-map-sym}
                (when defaults
                  {:or defaults}))
         ui-name-meta {:special? true :arglists `([ ~'& ~ui-arg-map])}
]

     (let [component-name (symbol (clojure.string/capitalize (name ui-name)))
           component-map-constructor (symbol (str "map->" (name component-name)))
           draw-fn-name (symbol (str (name (str ui-name "-draw"))))
           ui-name-kw (keyword (name (ns-name *ns*))
                               (name ui-name))
           rerender-fn-name (symbol (str ui-name "-rerender!"))
           elem-sym (gensym "elem-")
           result 
           `(do


              (declare ~ui-name)
              (defn ~draw-fn-name [~ui-arg-map]
                (let [~@arg-path-bindings]
                  ;; force evaluation so *ns* variable is set
                  ;; correctly. we need to know the *ns* for clojurescript
                  ;; so we can correctly replace calls to other ui components
                  ;; with their provenance info
                  ~@(doall* (map #(path-replace % deps) body))))

              (defcomponent ~component-name [~@arg-keys]
                  membrane.ui/IOrigin
                  (~'-origin [this#]
                   [0 0])

                membrane.ui/IBounds
                (~'-bounds [this#]
                 (::bounds this#)
                 )

                membrane.ui/IHasMouseMoveGlobal
                (~'has-mouse-move-global [this#]
                 (::has-mouse-move-global this#))

                membrane.ui/IHasKeyPress
                (~'has-key-press [this#]
                 (::has-key-press this#))

                membrane.ui/IHasKeyEvent
                (~'has-key-event [this#]
                 (::has-key-event this#))

                membrane.ui/IKeyPress
                (~'-key-press [this# info#]
                 (when (::has-key-press this#)
                   (when-let [xs# (children this#)]
                     (mapcat #(membrane.ui/-key-press % info#) xs#))))

                membrane.ui/IKeyEvent
                (~'-key-event [this# key# scancode# action# mods#]
                 (when (::has-key-event this#)
                   (when-let [xs# (children this#)]
                     (mapcat #(membrane.ui/-key-event % key# scancode# action# mods#) xs#))))

                membrane.ui/IMouseMoveGlobal
                (~'-mouse-move-global [this# pos#]
                 (when (::has-mouse-move-global this#)
                   (membrane.ui/-default-mouse-move-global this# pos#)))

                membrane.ui/IChildren
                (~'-children [this#]
                 ;; [(~draw-fn-name this# )]
                 (::children this#)
                 )
                
                membrane.ui/IDraw
                (~'draw [this#]
                 ;; (draw (~draw-fn-name this#))
                 (draw (::rendered this#))
                 ))
              (alter-meta! (var ~ui-name) (fn [old-meta#]
                                            (merge old-meta# (quote ~ui-name-meta))))

              ;; i'm not sure there's a good reason to ever call this function
              ;; as parent components will also cache themselves and so unless
              ;; you wipe the full component cache, then rerendering a child component
              ;; won't be reflected in most cases since the parent component's cache
              ;; won't have been updated
              (defn ~rerender-fn-name [m#]
                (let [~elem-sym (~component-map-constructor m#)
                      key# [~ui-name-kw
                            [~@(for [k arg-keys]
                                 `(~(keyword k) ~elem-sym))

                             ~@(for [k arg-keys]
                                 `(~(keyword (str "$" (name k))) ~elem-sym))]]
                      rendered# (~draw-fn-name ~elem-sym)
                      ~elem-sym (-> ~elem-sym
                                    (assoc ::bounds (bounds rendered#))
                                    (assoc ::children [rendered#])
                                    (assoc ::rendered rendered#)
                                    (assoc ::has-key-event (membrane.ui/has-key-event rendered#))
                                    (assoc ::has-key-press (membrane.ui/has-key-press rendered#))
                                    (assoc ::has-mouse-move-global (membrane.ui/has-mouse-move-global rendered#)))]
                  ;; (println "need new " ~(name ui-name))
                  (swap! membrane.component/component-cache
                         assoc-in key# ~elem-sym)
                  ~elem-sym))

              (let [
                    ret#
                    (defn ~ui-name [ ~'& ~ui-arg-map]

                      (let [key# [~ui-name-kw
                                  [~@arg-keys
                                   ;; ideally, the provenance keys shouldn't need
                                   ;; to be included since the component really
                                   ;; is the same, but it currently doesn't
                                   ;; work as is because of the event handlers
                                   ~@(for [k arg-keys]
                                       `(~(keyword (str "$" (name k)))
                                         ~args-map-sym))]]
                            elem# (if-let [elem# (get-in @membrane.component/component-cache key#)]
                                    elem#
                                    (~rerender-fn-name ~args-map-sym)
                                    #_(let [elem# (~component-map-constructor ~args-map-sym)
                                            rendered# (~draw-fn-name elem#)
                                            elem# (-> elem#
                                                      (assoc ::bounds (bounds rendered#))
                                                      (assoc ::children [rendered#])
                                                      (assoc ::rendered rendered#)
                                                      (assoc ::has-key-event (membrane.ui/has-key-event rendered#))
                                                      (assoc ::has-key-press (membrane.ui/has-key-press rendered#)))]
                                        ;; (println "need new " ~(name ui-name))
                                        (swap! membrane.component/component-cache
                                               assoc-in key# elem#)
                                        elem#))]
                        elem#))]
                (reset! component-cache {})
                
                (alter-meta! (var ~ui-name) (fn [old-meta#]
                                              (merge old-meta# (quote ~ui-name-meta))))
                ret#)
              
              )]
       (swap! special-fns assoc (symbol (name (ns-name *ns*)) (name ui-name)) ui-name-meta)
       result))))


(defonce effects (atom {}))
(defmacro defeffect [type args & body]
  `(def ~(symbol (str "effect-" (name type)))
     (let [effect# (fn [~'dispatch! ~@args]
                          ~@body)]
       (swap! effects assoc ~type effect#)
       effect#)))


#?(:clj
   (do
     (defeffect :clipboard-copy [_ s]
       (-> (java.awt.Toolkit/getDefaultToolkit )
           (.getSystemClipboard)
           (.setContents (java.awt.datatransfer.StringSelection. s) nil)))
     (defeffect :clipboard-cut [s]
       (-> (java.awt.Toolkit/getDefaultToolkit )
           (.getSystemClipboard)
           (.setContents (java.awt.datatransfer.StringSelection. s) nil))))
   )





(defn db-handler [atm]
  {:update
   (fn [_ path f & args ]
     (swap! atm
            (fn [old-state]
              (spec/transform (path->spec path)
                              (fn [& spec-args]
                                (apply f (concat spec-args
                                                 args)))
                              old-state))))
   :set
   (fn [_ path v]
     (swap! atm
            (fn [old-state]
              old-state
              (spec/transform (path->spec path) (constantly v) old-state))))
   :delete
   (fn [_ path]
     (swap! atm
            (fn [old-state]
              (spec/transform (path->spec path) (constantly spec/NONE) old-state))))})


(defui top-level-ui [& {:keys [extra
                               state
                               body
                               arg-names
                               defaults
                               handler]}]
  (binding [*root* state]
    (let [[focus-paths focus-fn :as focus] (::focus state)
          extra (::extra state)
          context (::context state)
          args (apply concat
                      (for [nm arg-names
                            :let [kw (keyword nm)
                                  $kw (keyword (str "$" (name kw)))]]
                        [kw
                         (get state kw
                              (get defaults nm))

                         $kw
                         (into
                          `[(~'keypath ~kw)]
                          (when (contains? defaults nm)
                            [(list 'nil->val (get defaults nm))]))]))
          main-view (apply
                     @body
                     :extra extra
                     :$extra $extra
                     :context context
                     :$context $context
                     args)
          
          ]
      (membrane.ui/on-scroll
       (fn [offset]
         (let [steps (membrane.ui/scroll main-view offset)]
           (run! #(apply handler %) steps)))
       (membrane.ui/on-mouse-move-global
        (fn [pos]
          (let [steps (membrane.ui/mouse-move-global main-view pos)]
            (run! #(apply handler %) steps)))
        (membrane.ui/on-mouse-move
         (fn [pos]
           (let [steps (membrane.ui/mouse-move main-view pos)]
             (run! #(apply handler %) steps)))
         (membrane.ui/on-mouse-event
          (fn [pos button mouse-down? mods]
            (let [steps (membrane.ui/mouse-event main-view pos button mouse-down? mods)]
              (if (seq steps)
                (run! #(apply handler %) steps)
                (when mouse-down?
                  (handler :set [:context :focus] nil)))))
          (ui/on-keypress
           (fn [s]
             (let [steps (membrane.ui/key-press main-view s)]
               (run! #(apply handler %) steps))
             )
           (membrane.ui/on-key-event
            (fn [key scancode action mods]
              (let [steps (membrane.ui/key-event main-view key scancode action mods)]
                (run! #(apply handler %) steps))
              )
            (membrane.ui/on-clipboard-cut
             (fn []
               (let [steps (membrane.ui/clipboard-cut main-view)]
                 (run! #(apply handler %) steps)))
             (membrane.ui/on-clipboard-copy
              (fn []
                (let [steps (membrane.ui/clipboard-copy main-view)]
                  (run! #(apply handler %) steps)))
              (membrane.ui/on-clipboard-paste
               (fn [s]
                 (let [steps (membrane.ui/clipboard-paste main-view s)]
                   (run! #(apply handler %) steps)))
               main-view))))))))))))


(defn make-top-level-ui
  ([ui-var state-atom handler]
   (let [arglist (-> ui-var
                     meta
                     :arglists
                     first)
         m (second arglist)
         arg-names (disj (set (:keys m))
                         'emit! 'extra)
         defaults (:or m)
         top-level (fn []
                     (top-level-ui :state @state-atom :$state nil
                                   :body ui-var
                                   :arg-names arg-names
                                   :defaults defaults
                                   :handler handler))]
     top-level)))

(defn default-handler [type & args]
  (println "No handler for " type))

(defn make-handler [effects]
  (fn dispatch! [type & args]
    (let [handler (get effects type)]
      (if handler
        (apply handler dispatch! args)
        (println "no handler for " type)))))

(defn run-ui
  ([ui-var]
   (run-ui ui-var {}))
  ([ui-var initial-state]
   (run-ui ui-var initial-state nil))
  ([ui-var initial-state handler]
   (let [state-atom (if (instance? #?(:clj clojure.lang.Atom
                                      :cljs cljs.core.Atom)
                                   initial-state)
                      initial-state
                      (atom initial-state))
         handler (if handler
                   handler
                   (let [app-effects (merge (db-handler state-atom) @effects)]
                     (make-handler app-effects)))
         top-level (make-top-level-ui ui-var state-atom handler)
         ]
     (membrane.ui/run top-level)
     state-atom)))

#?
(:clj
 (defn run-ui-sync
   ([ui-var]
    (run-ui-sync ui-var {}))
   ([ui-var initial-state]
    (run-ui-sync ui-var initial-state nil))
   ([ui-var initial-state handler]
    (let [state-atom (if (instance? #?(:clj clojure.lang.Atom
                                       :cljs cljs.core.Atom)
                                    initial-state)
                       initial-state
                       (atom initial-state))
          handler (if handler
                   handler
                   (let [app-effects (merge (db-handler state-atom) @effects)]
                     (make-handler app-effects)))
          top-level (make-top-level-ui ui-var state-atom handler)]
      (membrane.ui/run-sync top-level)
      state-atom))))

(defn test-args [a & some-args])

