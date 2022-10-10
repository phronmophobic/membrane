(ns membrane.component
  #?(:cljs (:require-macros [membrane.component :refer [defui path-replace-macro]]))
  (:require ;; [clojure.core.async :refer [go put! chan <! timeout dropping-buffer promise-chan]
   ;;  :as async]
   [com.rpl.specter :as spec
    :refer [ATOM ALL FIRST LAST MAP-VALS META]]
   #?(:clj [clojure.core.cache :as cache]
      :cljs [cljs.cache :as cache])
   #?(:clj [clojure.core.cache.wrapped :as cw]
      :cljs [cljs.cache.wrapped :as cw])
   #?(:cljs cljs.analyzer.api)
   #?(:cljs [cljs.analyzer :as cljs])
   #?(:cljs cljs.env)
   com.rpl.specter.impl
   [membrane.ui :as ui :refer [children bounds origin]]))

#?
(:clj
 (defmacro building-graalvm-image? []
   (try
     (import 'org.graalvm.nativeimage.ImageInfo)
     `(org.graalvm.nativeimage.ImageInfo/inImageBuildtimeCode)
     (catch ClassNotFoundException e
       false))))

;; clojurescript is an optional dependency
;; also, graalvm chokes on cljs requires
#?
(:clj
 (let [mock-cljs-env
       (fn []
         (def cljs-resolve (constantly nil))
         (def cljs-resolve-var (constantly nil))
         (def cljs-env-compiler (constantly nil)))]
   (if (building-graalvm-image?)
     (mock-cljs-env)
     (try
       (def cljs-resolve (requiring-resolve 'cljs.analyzer.api/resolve))
       (def cljs-resolve-var (requiring-resolve 'cljs.analyzer/resolve-var))
       (let [cljs-compiler (requiring-resolve 'cljs.env/*compiler*)]
         (def cljs-env-compiler (fn [] @cljs-compiler)))
       (catch Exception e
         (mock-cljs-env)))))
 :cljs
 (do
   (def cljs-resolve cljs.analyzer.api/resolve)
   (def cljs-resolve-var cljs.analyzer/resolve-var)
   (def cljs-env-compiler (fn [] cljs.env/*compiler*))))

;; support for sci
(def ^:dynamic *sci-ctx* nil)
#?(:clj
   (do
     (def sci-eval-form (delay (try
                                 (requiring-resolve 'sci.core/eval-form)
                                 (catch Exception e
                                   nil))))
     (defn resolve-sci-meta [sym]
       (when-let [sci-eval @sci-eval-form]
         (when *sci-ctx*
           (sci-eval *sci-ctx*
                     `(-> (resolve (quote ~sym))
                          meta))))))

   :default (defn resolve-sci-meta [sym]
              nil))

(def special-syms
  {'ATOM spec/ATOM
   'ALL spec/ALL
   'FIRST spec/FIRST
   'LAST spec/LAST
   'MAP-VALS spec/MAP-VALS
   'META spec/META
   'END spec/END})

(defn- do-map-nth-transform [vals structure idx next-fn]
  (let [prev-entry (nth (seq structure) idx)
        newv (next-fn vals prev-entry)]
    (if (identical? newv com.rpl.specter.impl/NONE)
      (dissoc structure (key prev-entry))
      (let [;; guaranteed map entry
            old-k (key prev-entry)
            ;; possibly vector
            new-k (nth newv 0)]
        (if (= old-k new-k)
          (conj structure newv)
          (if prev-entry
            (-> structure
                (dissoc old-k)
                (conj newv))
            (conj newv)))))))

(spec/defrichnav
  ^:private
  map-nth
  [idx]
  (select* [this vals structure next-fn]
           (next-fn vals (nth (seq structure) idx)))
  (transform* [this vals structure next-fn]
    (do-map-nth-transform vals structure idx next-fn)))


(defn nthpath+
  "Similar to spec/nthpath, but preserves maps."
  [idx]
  (spec/if-path map?
   (map-nth idx)
   (spec/nthpath idx)))

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

        seq-nth
        (nthpath+ arg)

        path
        (spec/path arg)

        raw-path
        (spec/path (path->spec arg))

        collect-one
        (spec/collect-one (path->spec arg))

        keypath
        (spec/keypath arg)

        keypath-list
        (apply spec/keypath arg)

        rest-args-map
        (spec/parser (fn [xs]
                       (apply hash-map xs))
                     (fn [m]
                       (eduction cat m)))

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

(defn- parse-path
  "create a lens that path-replace can use. "
  [form]
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

         (clojure.core/get-in get-in)
         (case (count form)
           3
           [(second form)
            `(list (quote ~'keypath-list) ~(nth form 2))]
           4
           [(second form)
            `[(list (quote ~'keypath-list) ~(nth form 2))
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

         ;; There are some good and bad reasons to do this
         ;; - Associng onto a map doesn't logically change its path
         ;; - there are some questions as to if it shouldn't modify its
         ;;   path at all or if it should include the key and value
         ;;   that was assoced. see also https://github.com/phronmophobic/membrane/issues/59
         #_#_(clojure.core/assoc assoc)
         [(nth form 1)
          `(list ~(quote ~'assoc)
                 ~(nth form 1)
                 ~(nth form 2))]

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
  (let [path
        (loop [deps deps
               k k
               path []]
          (if-let [[new-deps get-path] (get deps k)]
            (let [[new-k intent] @get-path]
              (recur new-deps
                     new-k
                     (if (some? intent)
                       (conj path intent)
                       path)))
            (vec (reverse (if k
                            (conj path `(list (quote ~'keypath) (quote ~k)))
                            path)))))]
    ;; special case to reduce nesting
    (if (and (symbol? (first path))
             (::flatten? (meta (first path))))
      `(into ~(first path)
             ~(vec (rest path)))
      path)))




;; we would like to just identify defui methods with metadata,
;; but since macros in clojure script won't have access to that metadata
;; we have to keep track elsewhere
;; this is the elsewhere
(defonce special-fns (atom {}))

(def ^:dynamic *env* nil)
(defn fully-qualified [sym]
  (if (cljs-env-compiler)
    (if-let [result (cljs-resolve {:ns (get-in @(cljs-env-compiler) [:cljs.analyzer/namespaces (symbol (ns-name *ns*))])}  sym)]
      (:name result))
    #?(:clj (if-let [v (resolve sym)]
              (symbol (name (ns-name (.ns ^clojure.lang.Var v))) (name (.sym ^clojure.lang.Var v)))))))

(declare destructure-deps)
(defn- destructure-deps-vector [bind]
  (let [[nth-binds tail] (split-with (complement '#{:as &}) bind)
        [rest-bind tail] (if (= '& (first tail))
                           [(second tail)
                            (nthrest tail 2)]
                           [nil tail])
        as-bind (when (= ':as (first tail))
                  (second tail))]
    (concat
     ;; nth binds
     (eduction
      (comp (map-indexed
             (fn [idx bind]
               (map (fn [[subbind path]]
                      [subbind (cons (list 'quote (list 'seq-nth idx)) path)])
                    (destructure-deps bind))))
            cat)
      nth-binds)

     ;; rest-bind
     (when rest-bind
       (let [drop-n (count nth-binds)
             path-prefix (if (map? rest-bind)
                           [(list 'quote (list 'drop drop-n))
                            (list 'quote (list 'rest-args-map))]
                           (list 'quote (list 'drop drop-n)))]
         (eduction
          (comp (map (fn [[subbind path]]
                       [subbind (cons path-prefix path)])))
          (destructure-deps rest-bind))))

     ;; as-bind
     (when as-bind
       [[as-bind []]]))))

(defn- destructure-deps-map [bind]
  (let [ors (:or bind)]
    (eduction
     (comp (map (fn [[bind k]]
                  (case bind
                    :as [[k []]]

                    :keys
                    (eduction
                     (map (fn [bind]
                            (let [k (-> bind name keyword)
                                  sym (-> bind name symbol)
                                  default (get ors sym)
                                  ]
                              [sym [(list 'quote (list 'keypath k default))]])))
                     k)

                    :strs
                    (eduction
                     (map (fn [bind]
                            (let [k (name bind)
                                  sym (-> bind name symbol)
                                  default (get ors sym)]
                              [sym [(list 'quote (list 'keypath k default))]])))
                     k)

                    :syms
                    (eduction
                     (map (fn [bind]
                            (let [sym (-> bind name symbol)
                                  default (get ors sym)]
                              [sym [(list 'quote (list 'keypath sym default))]])))
                     k)

                    :or []

                    ;; normal binding
                    (eduction
                     (map (fn [[subbind path]]
                            [subbind (cons (list 'quote (list 'keypath k))
                                           path)]))
                     (destructure-deps bind)))))
           cat)
     bind)))

(defn- destructure-deps [bind]
  (cond
    (symbol? bind)
    [[bind ()]]

    (vector? bind)
    (destructure-deps-vector bind)

    (map? bind)
    (destructure-deps-map bind)

    :else (throw (ex-info "Unrecognized binding form"
                          {:form bind}))))

(declare path-replace)
(defn- path-replace-let-bindings [deps bindings]
  (loop [bindings (seq (partition 2 bindings))
         deps deps
         new-bindings []]
    (if bindings
      (let [[bind val] (first bindings)
            val (path-replace val deps)
            val-path (parse-path val)

            val# (gensym "val#_")
            deps (assoc deps val# [deps val-path])
            deps (into deps
                       (for [[subbind subpath] (destructure-deps bind)]
                         [subbind [deps (delay [val#
                                                (vec subpath)])]]))]


        (recur (next bindings)
               deps
               (into new-bindings [bind val])))
      [deps new-bindings])))

(defn- path-replace-fn-call-map-literal [deps form fn-meta]
  (let [first-form (first form)
        call-arg (second form)

        arglists (:arglists fn-meta)
        first-arglist (first arglists)
        arg-map (first first-arglist)

        defaults (:or arg-map)

        ;; sort by explicit arguments since
        ;; they are used by the key prefix
        all-args (sort-by
                  (fn [sym]
                    [(not (contains? call-arg (keyword sym)))
                     (.startsWith (name sym) "$")])
                  (distinct
                   (concat (:keys arg-map)
                           (->> call-arg
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
         (for [k (keys call-arg)
               :let [arg-name (name k)
                     dollar-arg? (.startsWith arg-name "$")]
               :when (or dollar-arg?
                         (not (contains? call-arg (keyword (str "$" (name k))))))]
           (if dollar-arg?
             (get binding-syms (symbol arg-name))
             (symbol (str "$"
                          (name (get binding-syms (symbol arg-name))) )))))

        bindings (for [arg all-args
                       :when (contains? binding-syms arg)
                       :let [binding-sym (get binding-syms arg)
                             arg-val
                             (if (.startsWith (name arg) "$")
                               (if-let [arg-val (get call-arg (keyword arg))]
                                 arg-val
                                 (let [val-sym (get binding-syms (symbol (subs (name arg) 1)))]
                                   (symbol (str "$"  (name val-sym)))))

                               (get call-arg (keyword arg)
                                    (if (= 'context arg)
                                      'context
                                      (if (-> arg meta ::contextual)
                                        ;; should contextual state also check defaults?
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
      `(~first-form
        ~(path-replace
          `(let [~@(apply concat bindings)]
             ~(apply hash-map new-args))
          deps))
      (meta form))))

(defn- path-replace-fn-call*
  "handles the case where the fn call is a non-literal map

  Still assumes the form for the arg represents a map"
  [deps form fn-meta]
  (let [first-form (first form)
        call-arg (second form)

        arglists (:arglists fn-meta)
        first-arglist (first arglists)
        arg-map (first first-arglist)

        defaults (:or arg-map)

        m# (gensym "m#_")
        $m# (symbol (str "$" (name m#)))

        ;; add missing :$keys
        ;; expressions will go inside of a (cond-> ~'m# ...)
        missing-:$keys
        (eduction
         (mapcat (fn [sym]
                   (let [k (keyword sym)
                         $k (->> sym
                                 name
                                 (str "$")
                                 keyword)
                         contextual (-> sym meta ::contextual)]
                     [`(not (contains? ~m# ~$k))
                      `(assoc! ~$k
                               ~(cond
                                  (= 'context sym)
                                  '[$context]

                                  contextual
                                  `[~'$context (quote (~'keypath ~k))]

                                  :else
                                  (if-let [default (get defaults sym)]
                                    `[~$m#
                                      (quote (~'keypath ~k))
                                      (quote (~'nil->val ~default))]
                                    `[~$m# (quote (~'keypath ~k))])))])))
         (:keys arg-map))


        ;; add missing context and default values
        ;; expressions will go inside of a (cond-> ~'m# ...)
        ;; still not sure if extra should just go directly in the map
        ;;    or if it should be put somewhere else (like the parent extra).
        ;;    currently just putting extra directly in the map.
        missing-defaults-and-context
        (eduction
         (keep (fn [sym]
                 (let [k (keyword sym)
                       contextual (-> sym meta ::contextual)
                       default (get defaults sym)]
                   (cond
                     (= 'context sym)
                     [`(not (contains? ~m# ~k))
                      `(assoc! ~k ~'context)]

                     contextual
                     [`(not (contains? ~m# ~k))
                      `(assoc! ~k (get ~'context ~k))]

                     default
                     [`(not (contains? ~m# ~k))
                      `(assoc! ~k ~default)]))))
         cat
         (:keys arg-map))]

    (with-meta
        `(~first-form
          ~(path-replace
            `(let [~m# ~call-arg
                   full-m#
                   (persistent!
                    (cond-> (transient ~m#)
                      ~@missing-:$keys
                      ~@missing-defaults-and-context))]
               full-m#)
            deps))
        (meta form))))

(defn- path-replace-fn-call [deps form]
  (let [first-form (first form)]
    (let [full-sym (delay
                     (fully-qualified first-form))

          recursive-call-meta
          (when (and (simple-symbol? first-form)
                     (not (contains? deps first-form)))
            (get @special-fns
                 (symbol (name (ns-name *ns*))
                         (name first-form))))

          special? (if (symbol? first-form)
                     ;; should change `(meta first-form) to be first
                     (or recursive-call-meta
                         (if-let [m (or (when (cljs-env-compiler)
                                          (:meta (cljs-resolve-var *env* first-form)))
                                        #?(:clj (meta (resolve first-form)))
                                        (resolve-sci-meta first-form)
                                        (meta first-form))]
                           (::special? m)
                           (contains? @special-fns @full-sym))))]
      (if (not special?)
        ;; other fn call
        (with-meta
          (map #(path-replace % deps) form)
          (meta form))

        ;; call to defui component
        (let [call-arg (second form)
              fn-meta (or recursive-call-meta
                          (get @special-fns @full-sym
                               (or #?(:clj (meta (resolve first-form)))
                                   (resolve-sci-meta first-form)
                                   (meta first-form))))]
          (if (map? call-arg)
            (path-replace-fn-call-map-literal deps form fn-meta)
            (path-replace-fn-call* deps form fn-meta)))))))

(comment
  (destructure-deps 'a)
  (destructure-deps '[[[a]] b c & bar :as xs ] )
  (destructure-deps '[a b c [x y z] & bar :as xs ] )
  (destructure-deps '{a :a})
  (destructure-deps '[a b c & [d e] :as xs] )
  
  ,)

;; deps in `path-replace is a map of the form
;; {binding-sym [previous-deps
;;               (delay [dependent-binding
;;                       path-segment])]}
;; binding-sym: name of a binding
;; previous-deps: the existing dependency when at the time of binding.
;;                the previous deps are required because a binding-sym may be rebound and
;;                you want to be able to look up the next path segment based on the deps
;;                at the time a binding occurs. For example, (let [a {} b (:b a) a "something else"] $b).
;; delay wrapping: I don't quite rememeber why the path segment is wrapped in a delay, but
;;                 I think it's to simplify the implementation of `parse-path`. `parse-path`
;;                 may be asked to parse the path of an arbitrary form and we only care about
;;                 path segments that eventually produce a $reference. ie. Most bindings won't
;;                 need their paths calculated.
;; dependent-binding: the previous binding the current path-segment depends on.
;; path-segment: how the current binding was derived from the dependent-binding
 (defn path-replace
   "Given a form, walk and replace all $syms with the lens (or path) for the sym with the same name."
   ([form]
    (path-replace form {}))
   ([form deps]
    ;; (println "----")
    ;; (println (keys deps))
    ;; (println form)
    (cond
      (= '() form) form

      (nil? form) form

      (seq? form)
      (let [first-form (first form)]
        (case (first form)
          (clojure.core/let let loop clojure.core/when-let when-let)
          (let [[letsym bindings & body] form
                _ (do
                    (assert (vector? bindings) "a vector for its binding")
                    (assert (even? (count bindings)) "an even number of forms in binding vector"))
                [deps new-bindings]
                (path-replace-let-bindings deps bindings)
                
                body (map #(path-replace % deps) body)]
            `(~letsym ~new-bindings ~@body))

          (clojure.core/if-let if-let)
          (let [[letsym bindings then else] form
                _ (do
                    (assert (vector? bindings) "a vector for its binding")
                    (assert (even? (count bindings)) "an even number of forms in binding vector"))


                [then-deps new-bindings]
                (path-replace-let-bindings deps bindings)

                then (path-replace then then-deps)]
            (if (= (count form) 4)
              (let [;; else uses old original deps
                    else (path-replace else deps)]
                `(~letsym ~new-bindings ~then ~else))
              `(~letsym ~new-bindings ~then)))
          

          (for clojure.core/for)
          (let [[seq-exprs body-expr] (next form)
                [deps seq-exprs]
                (loop [seq-exprs (seq (partition 2 seq-exprs))
                       deps deps
                       new-seq-exprs []]
                  (if seq-exprs
                    (let [[bind val :as binding] (first seq-exprs)]
                      (if (keyword? bind)
                        (case bind
                          (:while :when)
                          (recur (next seq-exprs)
                                 deps
                                 (into new-seq-exprs binding))

                          :let
                          (let [[_ let-bindings] binding

                                [deps new-bindings]
                                (path-replace-let-bindings deps let-bindings)]
                            (recur (next seq-exprs)
                                   deps
                                   (into new-seq-exprs [:let new-bindings]))))

                        ;; normal binding
                        (let [index-sym (gensym "index-")
                              new-val `(map-indexed vector ~val)
                              binding [[index-sym bind] new-val]

                              val# (gensym "val#_")
                              deps (assoc deps val# [deps (delay [val
                                                                  `(list (quote ~'seq-nth) ~index-sym)])])
                              deps (into deps
                                         (for [[subbind subpath] (destructure-deps bind)]
                                           [subbind [deps (delay [val#
                                                                  (vec subpath)])]]))]
                          (recur (next seq-exprs)
                                 deps
                                 (into new-seq-exprs binding)))))
                    [deps new-seq-exprs]))]
            (with-meta
              `(~'for ~seq-exprs
                ~(path-replace body-expr deps))
              (meta form)))

          (fori for-kv for-with-last)
          (throw (ex-info (str (first form) " is no longer supported.")
                          {:form form}))

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
            
            (with-meta
              (apply list
                     (concat
                      [fnsym]
                      (when fn-name
                        [fn-name])
                      replaced-sigs))
              (meta form)))
          

          . form

          ;; case?

          quote form

          var form

          catch form

          reify*
          form

          ;; other fn call
          (path-replace-fn-call deps form)))

      (symbol? form)
      (if (contains? deps form)
        form
        (let [form-name (name form)]
          (if (.startsWith form-name "$")
            (let [k (symbol (subs form-name 1))]
              (if (contains? deps k)
                (calculate-path deps k)
                form))
            form)))

      (string? form)
      form

      (map? form)
      (into (empty form)
            (for [[k v] form]
              [(path-replace k deps)
               (path-replace v deps)]))
      
      
      (seqable? form) (let [empty-form (empty form)
                            empty-form (if #?(:clj (instance? clojure.lang.IObj empty-form)
                                              :cljs (satisfies? IMeta empty-form))
                                         (with-meta empty-form (meta form))
                                         empty-form)]
                        (into empty-form
                              (map #(path-replace % deps) form)))

      :default form)))








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
      new-form)))


(defn doall* [s] (dorun (tree-seq seqable? seq s)) s)

(def component-cache (cw/basic-cache-factory {::basis 1}))

(defn reset-component-cache!
  "For debugging purposes only. Ideally, should never be necessary."
  []
  (swap! component-cache
         (fn [old-cache]
           (let [old-basis (cache/lookup old-cache ::basis)]
             (cache/seed old-cache {::basis (inc old-basis)}))) ))

;; make it easier for syntax quote to use the correct
;; namespaced symbol
(def cache-lookup-or-miss cw/lookup-or-miss)
(def cache-has? cache/has?)
(def cache-evict cache/evict)
(def cache-miss cache/miss)

(def has-key-press-memo (memoize ui/has-key-press))
(def has-key-event-memo (memoize ui/has-key-event))
(def has-mouse-move-global-memo (memoize ui/has-mouse-move-global))

(defmacro defui
  "Define a component.

  The arguments for a component must take the form (defui my-component [ & {:keys [arg1 arg2 ...]}])

  "
  [ui-name & fdecl]
  (let [def-meta (if (string? (first fdecl))
                   {:doc (first fdecl)}
                   {})
        fdecl (if (string? (first fdecl))
                (next fdecl)
                fdecl)
        def-meta (if (map? (first fdecl))
                   (conj def-meta (first fdecl))
                   def-meta)
        fdecl (if (map? (first fdecl))
                (next fdecl)
                fdecl)
        _ (assert (vector? (first fdecl)) "only one arglist allowed for defui.")
        [args & body] fdecl

        _ (assert (= 1 (count args))
                  "defui arglist must have exactly one arg")

        arg-map-or-sym (first args)
        _ (assert (or (symbol? arg-map-or-sym)
                      (map? arg-map-or-sym)
                      "defui arglist must have exactly one arg and it must be either a symbol or map."
                      ))
        ;; [ampersand arg-map-or-sym] args

        ;; arg-syms (get arg-map-or-sym :keys)

        args-map-sym (if (symbol? arg-map-or-sym)
                       arg-map-or-sym
                       (get arg-map-or-sym :as (gensym "m-")))

        arg-keys (get arg-map-or-sym :keys [])
        arg-keys (if (some #(= 'extra %) arg-keys)
                   arg-keys
                   (conj arg-keys 'extra))

        arg-keys (if (some #(= 'context %) arg-keys)
                   arg-keys
                   (conj arg-keys 'context))
         
        defaults (:or arg-map-or-sym)

        arg-path-syms (for [arg-name arg-keys]
                        (gensym (str "arg-path-" (name arg-name) "-")))
        arg-path-bindings (mapcat
                           (fn [arg-sym path-sym]
                             (let [arg-key (keyword arg-sym)
                                   $arg-key (keyword (str "$" (name arg-sym)))]
                               [path-sym
                                ;; should be same as below with one less vector wrap
                                (let [fn-arg-path `(get ~args-map-sym ~$arg-key [~(list 'list '(quote keypath) (list 'quote arg-key))])]
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
                         [nil (with-meta path-sym
                                {::flatten? true})])]]))
        ui-arg-map
        (merge (when (map? arg-map-or-sym)
                 arg-map-or-sym)
               {:keys arg-keys
                :as args-map-sym}
               (when defaults
                 {:or defaults}))
        ui-name-meta (merge
                      {::special? true :arglists `([~(dissoc ui-arg-map :as)])}
                      def-meta)]
    ;; needed for cljs
    ;; and recursive calls
    (swap! special-fns
           assoc
           (symbol (name (ns-name *ns*)) (name ui-name))
           ui-name-meta)

    (let [component-name (symbol (clojure.string/capitalize (name ui-name)))
          component-map-constructor (symbol (str "map->" (name component-name)))
          component-arg-constructor (symbol (str "->" (name component-name)))
          render-fn-name (symbol (str (name (str ui-name "-render"))))
          ui-name-kw (keyword (name (ns-name *ns*))
                              (name ui-name))
          render-cached-fn-name (symbol (str ui-name "-render-cached!"))
          elem-sym (gensym "elem-")

          result
          `(do

             (declare ~ui-name)
             (defn ~render-fn-name {:no-doc true} [~ui-arg-map]
               #_(prn "rendering " ~(str ui-name))
               (let [~@arg-path-bindings]
                 ;; force evaluation so *ns* variable is set
                 ;; correctly. we need to know the *ns* for clojurescript
                 ;; so we can correctly replace calls to other ui components
                 ;; with their provenance info
                 ~@(binding [*env* &env]
                     (doall* (map #(path-replace % deps) body)))))

             (defn ~render-cached-fn-name  {:no-doc true} [elem#]
               (cache-lookup-or-miss component-cache
                                     elem#
                                     ~render-fn-name))

             (defrecord ~component-name []
               membrane.ui/IOrigin
               (~'-origin [this#]
                [0 0])

               membrane.ui/IBounds
               (~'-bounds [this#]
                (ui/bounds (~render-cached-fn-name this#)))

               membrane.ui/IHasMouseMoveGlobal
               (~'has-mouse-move-global [this#]
                (has-mouse-move-global-memo (~render-cached-fn-name this#)))

               membrane.ui/IHasKeyPress
               (~'has-key-press [this#]
                (has-key-press-memo (~render-cached-fn-name this#)))

               membrane.ui/IHasKeyEvent
               (~'has-key-event [this#]
                (has-key-event-memo (~render-cached-fn-name this#)))

               membrane.ui/IKeyPress
               (~'-key-press [this# info#]
                (let [rendered# (~render-cached-fn-name this#)]
                  (when (has-key-press-memo rendered#)
                    (ui/-key-press rendered# info#))))

               membrane.ui/IKeyEvent
               (~'-key-event [this# key# scancode# action# mods#]
                (let [rendered# (~render-cached-fn-name this#)]
                  (when (has-key-event-memo rendered#)
                    (ui/-key-event rendered# key# scancode# action# mods#))))

               membrane.ui/IMouseMoveGlobal
               (~'-mouse-move-global [this# pos#]
                (let [rendered# (~render-cached-fn-name this#)]
                  (when (has-mouse-move-global-memo rendered#)
                    (membrane.ui/-mouse-move-global rendered# pos#))))

               membrane.ui/IChildren
               (~'-children [this#]
                [(~render-cached-fn-name this#)]))

             (alter-meta! (var ~ui-name) (fn [old-meta#]
                                           (merge old-meta# (quote ~ui-name-meta))))

             (let [
                   ret#
                   (defn ~ui-name ~(dissoc ui-name-meta
                                           :arglists)
                     [~ui-arg-map]

                     (let [elem# (~component-map-constructor ~args-map-sym)]
                       elem#))]
               (reset-component-cache!)

               ;; needed for bootstrapped cljs
               (swap! special-fns
                      assoc
                      (quote ~(symbol (name (ns-name *ns*)) (name ui-name)))
                      (quote ~ui-name-meta))
               (alter-meta! (var ~ui-name) (fn [old-meta#]
                                             (merge old-meta# (quote ~ui-name-meta))))
               ret#)
              
             )]

      result)))


(defonce effects (atom {}))
(defmacro defeffect
  "Define an effect.

  `defeffect` is a macro that does 3 things:
1) It registers a global effect handler of `type`. `type` should be a keyword and since it is registered globally, should be namespaced
2) It will define a var in the current namespace of `effect-*type*` where *type* is the name of the type keyword. This can be useful if you want to be able to use your effect functions in isolation
3) It will implicitly add an additional argument as the first parameter named `dispatch`

The arglist for `dispatch!` is `[type & args]`. Calling `dispatch!` will invoke the effect of `type` with `args`.
The role of `dispatch!` is to allow effects to define themselves in terms of other effects. Effects should not be called directly because while the default for an application is to use all the globally defined effects, this can be overridden for testing, development, or otherwise.


  example:

  (defeffect ::increment-number [$num]
      (dispatch! :update $num inc))

  "
  [type args & body]
  (let [fn-name (symbol (str "effect-" (name type)))]
    `(let [var# (defn ~fn-name [~'dispatch! ~@args]
                  ~@body)]
       (swap! effects assoc ~type ~fn-name)
       var#)))


#?(:clj
   (do
     (defeffect :clipboard-copy [s]
       (ui/copy-to-clipboard s))
     (defeffect :clipboard-cut [s]
       (ui/copy-to-clipboard s)))
   )





(defui top-level-ui [{:keys [state
                             body
                             path-keys
                             val-key-fns
                             handler]
                      :as m}]
  (let [extra (::extra state)
        context (::context state)

        base
        (assoc path-keys
               :extra extra
               :$extra $extra
               :context context
               :$context $context)

        arg (into base
                  (map (fn [[kw kw-fn]]
                         [kw (kw-fn state context)]))
                  val-key-fns)

        main-view (@body arg)]
    (membrane.ui/on-bubble
     (fn [intents]
       (when (seq intents)
         (handler intents)))
     (membrane.ui/on-mouse-event
      (fn [pos button mouse-down? mods]
        (let [intents (membrane.ui/mouse-event main-view pos button mouse-down? mods)]
          (if (seq intents)
            (handler intents)
            (when mouse-down?
              (let [focus (:focus context)]
                (when focus
                  (handler [[:set $focus nil]])))
              nil))))
      main-view))))



(defn default-update [atm path f & args]
  ;; use transform* over transform for graalvm.
  ;; since the specs are dynamic, I don't think there's any benefit to the
  ;; macro anyway
  (spec/transform* (path->spec [ATOM path])
                   (fn [& spec-args]
                     (apply f (concat spec-args
                                      args)))
                   atm))

(defn default-set [atm path v]
  ;; use setval* over setval for graalvm.
  ;; since the specs are dynamic, I don't think there's any benefit to the
  ;; macro anyway
  (spec/setval* (path->spec [ATOM path]) v atm))

(defn default-get [atm path]
  (spec/select-one* (path->spec [ATOM path])
                    atm))

(defn default-delete [atm path]
  ;; use setval* over setval for graalvm.
  ;; since the specs are dynamic, I don't think there's any benefit to the
  ;; macro anyway
  (spec/setval* (path->spec [ATOM path]) spec/NONE atm))

(defn dispatch!*
  [atm dispatch! & args]
  (case (first args)
    :update
    (apply default-update atm (next args))

    :set
    (apply default-set atm (next args))

    :get
    (default-get atm (nth args 1))

    :delete
    (default-delete atm (nth args 1))
    
    (let [effects @effects]
      (let [type (first args)
            handler (get effects type)]
        (if handler
          (apply handler dispatch! (next args))
          (println "no handler for " type))))))

(defn default-handler [atm]
  (fn dispatch!
    ([effects]
     (run! #(apply dispatch! %) effects))
    ([effect-type & args]
     (apply dispatch!* atm dispatch! effect-type args))))

(defn ui-var->top-level-ui [ui-var]
  (let [arglist (-> ui-var
                    meta
                    :arglists
                    first)
        m (first arglist)
        arg-names (disj (set (:keys m))
                        'extra
                        'context)
        defaults (:or m)

        path-keys (into {}
                        (map (fn [nm]
                               (let [kw (keyword nm)
                                     $kw (keyword (str "$" (name nm)))
                                     contextual? (-> nm meta ::contextual)]
                                 [$kw
                                  (if contextual?
                                    ['(keypath ::context) (list 'keypath kw)]
                                    ;; else
                                    (into
                                     `[(~'keypath ~kw)]
                                     (when (contains? defaults nm)
                                       [(list 'nil->val (get defaults nm))])))])))
                        arg-names)

        val-key-fns
        (into []
              (map (fn [nm]
                     (let [kw (keyword nm)
                           $kw (keyword (str "$" (name kw)))
                           contextual? (-> nm meta ::contextual)]
                       [kw
                        (if contextual?
                          (fn [state context]
                            (get context kw
                                 ;; path-replace-fn-call-map-literal doesn't
                                 ;; currently check defaults for contextual.
                                 ;; revisit?
                                 #_(get defaults nm)
                                 ))
                          (fn [state context]
                            (get state kw
                                 (get defaults nm))))])))
              arg-names)]
    (top-level-ui {:$state []
                   :body ui-var
                   :path-keys path-keys
                   :val-key-fns val-key-fns})))

(defn make-app
  "`ui-var` The var for a component
  `initial-state` The initial state of the component to run or an atom that contains the initial state.
  `handler` The effect handler for your UI. The `handler` will be called with all effects returned by the event handlers of your ui.

  If `handler` is nil or an arity that doesn't specify `handler` is used, then a default handler using all of the globally defined effects from `defeffect` will be used. In addition to the globally defined effects the handler will provide 3 additional effects:

  `:update` similar to `update` except instead of a keypath, takes a more generic path.
  example: `[:update $ref inc]`

  `:set` sets the value given a $path
  example: `[:set $ref val]`

  `:delete` deletes value at $path
  example: `[:delete $ref]`

  return value: the state atom used by the ui."
  ([ui-var]
   (make-app ui-var {}))
  ([ui-var initial-state]
   (make-app ui-var initial-state nil))
  ([ui-var initial-state handler]
   (let [state-atom (if (instance? #?(:clj clojure.lang.Atom
                                      :cljs cljs.core.Atom)
                                   initial-state)
                      initial-state
                      (atom initial-state))
         handler (if handler
                   handler
                   (default-handler state-atom))
         top-ui (ui-var->top-level-ui ui-var)
         top-ui (assoc top-ui :handler handler)
         top-level (fn []
                     (assoc top-ui
                            :state @state-atom
                            ::basis (::basis @component-cache)))]
     top-level)))


