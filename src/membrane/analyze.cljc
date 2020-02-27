(ns membrane.analyze
  #?(:clj
     (:require clojure.walk))
  #?@
  (:cljs
   [(:require-macros [cljs.core.async.macros :refer [go]])
    
    (:require clojure.walk
              [membrane.macroexpand :refer [macroexpand-all]] 
              [cljs.core.async :refer [put! chan <! timeout dropping-buffer]
               :as async]
              [cljs.js :as cljs])]))
;; topo sort
;; https://gist.github.com/alandipert/1263783
;; clj-graph
;; https://github.com/bonjure/clj-graph/blob/master/src/clj_graph.clj

(defn ignore-loop-and-let-bindings [form]
  ;; assumes already macroexpanded
  (clojure.walk/postwalk
   (fn [form]
     (if (and (seq? form)
              (#{'loop* 'let*} (first form)))
       (let [[letsym bindings & body] form]
         `(~letsym ~(->> bindings (drop 1) (take-nth 2)) ~@body))
       form))
   form))

(defn ignore-new-bindings [form]
  (clojure.walk/postwalk
   (fn [form]
     (if (and (seq? form)
              (= 'new (first form)))
       (let [[newsym constructor-sym body] form]
         body)
       form))
   form))

(defn ignore-fns [form]
  (clojure.walk/prewalk
   (fn [form]
     (if (and (seq? form)
              (= (first form) 'fn*))
       nil
       form))
   form))

(defn unbound-syms [form]
  (cond
   (= '() form) nil

   (seq? form)
   (case (first form)
     ;; broken for
     ;; (let [b a a a ]1)
     (let* loop* clojure.core/let)
     (let [[letsym bindings & body] form
           newbindings (apply hash-set (take-nth 2 bindings))
           unbound (concat
                    (unbound-syms (->> bindings (drop 1) (take-nth 2)))
                    (unbound-syms body))
           unbound (remove newbindings unbound)]
       unbound)

     ;; this doesn't cover all of the binding forms
     fn*
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
           unbound-body-syms (apply concat
                                    (for [[bindings & body] sigs
                                          :let [bindings (into #{} bindings)]]
                                      (remove bindings (unbound-syms body))))
           unbound (remove #{fn-name} unbound-body-syms)
           ]
       unbound) 

     new
     (let [[newsym classname & args] form]
       (unbound-syms args))

     . (concat
        (unbound-syms (second form))
        (unbound-syms (if (list? (nth form 2))
                        (rest (nth form 2))
                        (drop 3 form))))


     quote nil

     var nil

     catch (let [[catch-sym catch-class new-binding & body] form]
             (remove #{new-binding} (unbound-syms body)))

     reify*
     (let [[_ interfaces & methods] form]
       (apply
        concat
        (for [[method-name bindings & body] methods]
          (remove (set bindings)
                  (unbound-syms body)))))

     (mapcat unbound-syms form))

   (symbol? form) (list form)

   (seqable? form) (mapcat unbound-syms form)

   :default nil))



(defn cell-deps
  ([expr]
   (#?(:clj identity :cljs go)
     (->> #?(:cljs (<! (macroexpand-all expr))
             :clj (clojure.walk/macroexpand-all expr))
          unbound-syms
          (remove namespace)
          (remove special-symbol?)
          (distinct))))
  ([cname expr]
   (#?(:clj identity :cljs go)
     (->> #?(:cljs (<! (macroexpand-all expr))
             :clj (clojure.walk/macroexpand-all expr))
          unbound-syms
          (remove namespace)
          (remove special-symbol?)
          (remove #{cname})
          (distinct)))))


