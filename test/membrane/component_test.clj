(ns membrane.component-test
  (:require [clojure.test :refer :all]
            [clojure.core.specs.alpha :as sa]
            [com.rpl.specter :as spec
             :refer [ATOM ALL FIRST LAST MAP-VALS META]]
            [clojure.string :as str]
            ;; [clojure.spec.gen.alpha :as gen]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.alpha.spec :as s]
            [clojure.test.check :as tc]
            [membrane.component :refer :all
             :as component]))


(alter-var-root #'s/*recursion-limit* (constantly 2) )

(s/def ::binding-sym
  (s/with-gen
    (s/and simple-symbol? #(not= '& %))
    (fn []
      (gen/fmap
       (fn [n]
         (symbol (str "__" n)))
       gen/nat))))

(s/def ::binding-name
  (s/with-gen
    (s/and simple-symbol?
           #(not= '& %)
           #(not (str/includes? (name %) ".")))
    (fn []
      (gen/fmap
       (fn [n]
         (gensym (str "binding_")))
       gen/nat))))

(s/def ::ampersand
  (s/with-gen #(= % '&)
    (constantly
     (gen/return '&))))


(s/def ::seq-binding-form
  (s/catv :forms (s/* ::binding-form)
          :rest-forms (s/? (s/cat :ampersand ::ampersand
                                  ;; should be ::binding-form
                                  ;; but we're ignoring maps in rest params
                                  :form ::rest-binding-form))
          :as-form (s/? (s/cat :as #{:as} :as-sym ::binding-name))))

(s/def ::rest-binding-form
  (s/or
   :local-symbol ::binding-name
   :seq-destructure ::seq-binding-form
   ;; technically allowed, but we're not supporing for now
   ;; :map-destructure ::map-binding-form
   ))


(s/def ::keys (s/coll-of ::binding-name :kind vector?))
(s/def ::syms (s/coll-of ::binding-name :kind vector?))
(s/def ::strs (s/coll-of ::binding-name :kind vector?))
(s/def ::or (s/map-of ::binding-sym #{:or-default}))
(s/def ::as ::binding-name)

(s/def ::map-special-binding
  (s/keys :opt-un [::as ::or ::keys ::syms ::strs]))

(s/def ::map-binding (s/tuple ::binding-form keyword?))

#_(s/def ::ns-keys
    (s/tuple
     (s/and qualified-keyword? #(-> % name #{"keys" "syms"}))
     (s/coll-of simple-symbol? :kind vector?)))

(s/def ::map-bindings
  (s/every
   (s/or :map-binding ::map-binding
         ;; :qualified-keys-or-syms ::ns-keys
         :special-binding (s/tuple #{:as :or :keys :syms :strs} any?)
         )
   :kind map?))

(s/def ::map-binding-form (s/merge ::map-bindings ::map-special-binding))

(s/def ::binding-form
  (s/or
   :local-symbol ::binding-name
   :seq-destructure ::seq-binding-form
   :map-destructure ::map-binding-form))

(defn find-bindings [obj]
  (->> (tree-seq (fn [obj]
                   (or (vector? obj)
                       (map? obj)
                       (map-entry? obj)))
                 seq
                 obj)
       (filter (fn [obj]
                 (and (symbol? obj)
                      (str/starts-with? (name obj) "binding_"))))
       (into [] (distinct) )))

;; Used in code generated in tests
(def root nil)


;; > (let [[ & [ & [& [& [& [& hi]]]]]] [42]]
;;     hi)
;; (42)

;; > (let [[& {:as m}] [:a 42]]
;;     m)
;; {:a 42}


(declare binding->data)
(defn- binding-vector->data [bind]
  (let [[nth-binds tail] (split-with (complement '#{:as &}) bind)
        [rest-bind tail] (if (= '& (first tail))
                           [(second tail)
                            (nthrest tail 2)]
                           [nil tail])
        as-bind (when (= ':as (first tail))
                  (second tail))]
    ;; straight concats throws errors
    ;; see https://ask.clojure.org/index.php/1912/quote-of-an-empty-lazyseq-produces-an-error-when-evaled
    (apply
     list
     (concat
      ;; nth binds
      (eduction
       (map (fn [bind]
              (binding->data bind)))
      
       nth-binds)

      ;; rest-bind
      (when rest-bind
        (if (map? rest-bind)
          (into []
                cat
                (binding->data rest-bind))
          (binding->data rest-bind)))

      ;; as-bind
      ;; nothing to do
      ))))

(defn- binding-map->data [bind]
  (let [ors (:or bind)]
    (into
     {}
     (comp (map (fn [[bind k]]
                  (case bind
                    :as nil

                    :keys
                    (eduction
                     (map (fn [bind]
                            (let [k (-> bind name keyword)]
                              [k nil])))
                     k)

                    :strs
                    (eduction
                     (map (fn [bind]
                            (let [k (name bind)]
                              [k nil])))
                     k)

                    :syms
                    (eduction
                     (map (fn [bind]
                            (let [sym (-> bind name symbol)]
                              [sym nil])))
                     k)

                    :or nil

                    ;; normal binding
                    [[k (binding->data bind)]])))
           cat)
     bind)))

(defn- binding->data [bind]
  (cond
    (symbol? bind)
    nil

    (vector? bind)
    (binding-vector->data bind)

    (map? bind)
    (binding-map->data bind)

    :else (throw (ex-info "Unrecognized binding form"
                          {:form bind}))))

(def codes (atom []))
(defn check-compiles [binding-form]
  (let [code `(let [~binding-form (quote ~(binding->data binding-form))])]
    (try
      (nil? (eval code))
      (catch Exception e
        (swap! codes conj code)
        (= "Method code too large!"
           (-> e ex-cause ex-message))))))

(def compiles-prop
  (prop/for-all [binding-form (s/gen ::binding-form)]
                (check-compiles binding-form)))

(defspec compiles-binding-data-form
  20
  compiles-prop)


(defn check-updates [binding-form]
  (let [bindings (find-bindings binding-form)
        bdata (binding->data binding-form)]
    (try
      (every? (fn [bind]
                (try
                  (let [test-value [:a 42]
                        path-code (path-replace
                                   `(let [~binding-form root]
                                      ~(symbol (str "$" (name bind))))
                                   {`root [nil
                                           (delay [nil
                                                   []])]})
                        path (eval path-code)
                      
                        updated-data (spec/setval (path->spec path)
                                                  test-value
                                                  bdata)
                        ;; _ (prn binding-form bind path bdata updated-data)
                      
                        value-code
                        `(let [~binding-form (quote ~updated-data)]
                           ~bind)
                        ;; _  (prn value-code)
                        extracted-value
                        (try
                          (eval value-code)
                          (catch Exception e
                            ;; ignore exceptions here for now.
                            ;; they're mostly due to replacing seqs/maps
                            ;; with the test-value and throwing an exception
                            ;; during destructuring
                            test-value))]
                    (= test-value extracted-value))
                  ))
              bindings)
      (catch Exception e
        (= "Method code too large!"
           (-> e ex-cause ex-message))))))



(def updates-prop
  (prop/for-all [binding-form (s/gen ::binding-form)]
                (check-updates binding-form)))

(defspec updates-prop-test
  20
  updates-prop)


(defn check-extracts [binding-form]
  (let [bindings (find-bindings binding-form)
        bdata (binding->data binding-form)]
    (try
      (every? (fn [bind]
                (try
                  (let [
                        path-code (path-replace
                                   `(let [~binding-form root]
                                      ~(symbol (str "$" (name bind))))
                                   {`root [nil
                                           (delay [nil
                                                   []])]})
                        path (eval path-code)

                        test-value (spec/select-one (path->spec path)
                                                    bdata)
                        
                        
                        value-code
                        `(let [~binding-form (quote ~bdata)]
                           ~bind)
                        ;; _  (prn value-code)
                        extracted-value (eval value-code)

                        ;; _ (prn binding-form bind path bdata test-value extracted-value)
                        ]
                    (= (seq test-value) (seq extracted-value)))
                  ))
              bindings)
      (catch Exception e
        (= "Method code too large!"
           (-> e ex-cause ex-message))))))



(def extracts-prop
  (prop/for-all [binding-form (s/gen ::binding-form)]
                (check-extracts binding-form)))

(defspec extracts-prop-test
  20
  extracts-prop)


(defonce reports (atom []))


(comment
  (future
    (try
      (binding [s/*recursion-limit* 2]
        (def results (tc/quick-check 50 updates-prop
                                     :reporter-fn (fn [m]
                                                    (swap! reports conj m))
                                   
                                     )))
      (def bad-result (-> results :shrunk :smallest))
      (catch Exception e
        (def bad-e e))))

  (future
    (try
      (binding [s/*recursion-limit* 1]
        (def results (tc/quick-check 20 extracts-prop
                                     #_#_:reporter-fn (fn [m]
                                                    (swap! reports conj m))
                                   
                                     )))
      (def bad-result (-> results :shrunk :smallest))
      (catch Exception e
        (def bad-e e))))
  ,)

