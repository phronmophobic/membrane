;; Copyright (C) Adrian Smith - All Rights Reserved
;; Unauthorized copying of this file, via any medium is strictly prohibited
;; Proprietary and confidential
;; Written by Adrian Smith adrian@phronemophobic.com, June 2019
(ns membrane.macroexpand
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require clojure.walk
            [cljs.core.async :refer [put! chan <! timeout dropping-buffer]
             :as async]
            [cljs.js :as cljs]))


(defn mexpand [form]
  (let [ch (async/promise-chan)]
    (cljs/eval (cljs/empty-state) (list 'macroexpand
                                        `(quote ~form))
               {:eval cljs/js-eval
                :source-map true}
               (fn [result]
                 (put! ch result)))
    ch))


(defn identity-async [x]
  (go x))

(defn map1-async [f coll]
  (go
    (let [chs (map f coll)]
      (loop [[ch & more] chs
             ret []]
        (if ch
          (recur more (conj ret (<! ch)))
          ret)))))


(defn prewalk-async-helper
  "Traverses form, an arbitrary data structure.  inner and outer are
  functions.  Applies inner to each element of form, building up a
  data structure of the same type, then applies outer to the result.
  Recognizes all Clojure data structures. Consumes seqs as with doall."

  {:added "1.1"}
  [f form]
  (cond
    (list? form)   (go (apply list (<! (map1-async f form))))
    (seq? form)    (go (<! (map1-async f form)))
    ;; (record? form) (outer (reduce (fn [r x] (conj r (f x))) form form))
    (record? form)
    (go
      (let [xs (<! (map1-async f form))]
        (reduce (fn [r x] (conj r x)) form xs)))
    (coll? form)   (go (into (empty form) (<! (map1-async f form))))
    :else          (go form)))

(defn prewalk-async
  "Traverses form, an arbitrary data structure.  inner and outer are
  functions.  Applies inner to each element of form, building up a
  data structure of the same type, then applies outer to the result.
  Recognizes all Clojure data structures. Consumes seqs as with doall."

  {:added "1.1"}
  [f form]
  (go
    (<!
     (prewalk-async-helper f (<! (f form))))))


;; (defn prewalk-async
;;   "Like postwalk, but does pre-order traversal."
;;   {:added "1.1"}
;;   [f form]
;;   (walk-async (partial prewalk-async f) identity (f form)))


(defn macroexpand-all
  "Recursively performs all possible macroexpansions in form."
  [form]
  (prewalk-async (fn [x] (if (seq? x) (mexpand x) (identity-async x))) form))
