(ns membrane.defui-test
  (:require [clojure.test :refer :all]
            [com.rpl.specter :as spec
             :refer [ATOM ALL FIRST LAST MAP-VALS META]]
            [clojure.string :as str]
            ;; [clojure.spec.gen.alpha :as gen]
            [membrane.ui :as ui]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.alpha.spec :as s]
            [clojure.test.check :as tc]
            [membrane.component :refer :all
             :as component]))


(s/def ::literal boolean?)

(s/def ::prop simple-keyword?)

(s/def ::extra map?)
(s/def ::context map?)
(s/def ::defui-call-arg
  (s/merge
   (s/keys :req [::literal])
   (s/map-of
    (s/or :literal #{::literal}
          :prop ::prop)
    any?)
   (s/keys :opt-un [::extra ::context] )))

(defui child [{:keys [a b
                      ^:membrane.component/contextual
                      c]
               :or {a 42}
               :as m}]
  (ui/on
   :mouse-down
   (fn [_]
     [[:data a b c m]])
   (ui/spacer 10)))

(defui parent [{:keys [m]}]
  (child m))

(comment

  (-> (parent {:m {}
               :context {:focus 42}
               :$context [:foo]})
      (ui/mouse-down [0 0]))

  (-> (parent {:m {:b 42}
               :context {:focus 42}
               :$context [:foo]})
      (ui/mouse-down [0 0])
      )
)


(defui seq-nth1 [{:keys [obj]}]
  (vec
   (for [[k v] obj]
     (ui/on
      :key-press
      (fn [s]
        [[:set $v s]])
      ))))

;; setting keys doesn't make sense
#_(defui seq-nth2 [{:keys [obj]}]
  (vec
   (for [[k v] obj]
     (ui/on
      :key-press
      (fn [s]
        [[:set $k s]])
      ))))

(defui seq-nth3 [{:keys [obj]}]
  (let [[[k1 v1] [k2 v2] & _more] obj]
    (ui/on
     :key-press
     (fn [s]
       [[:set $k1 s]
        [:set $v1 s]
        [:set $k2 s]
        [:set $v2 s]]))))

(defui seq-nth4 [{:keys [obj]}]
  (vec
   (for [[k v] obj]
     (ui/on
      :key-press
      (fn [s]
        [[:set $v s]])
      ))))

(deftest seq-nth-test
  (testing "seq-nth setting vals"
    (let [arg {:obj {:a 1 :b 2}}
          intents (ui/key-press
                   (seq-nth1 arg)
                   "s")]
      (is
       (= #{1 2}
          (into #{}
                (map (fn [[_set path v]]
                       (spec/select-one (path->spec path)
                                        arg)))
                intents)))

      (is
       (every? (fn [[_set path v]]
                 (= :new-val
                    (spec/select-one (path->spec path)
                                     (spec/setval (path->spec path)
                                                  :new-val
                                                  arg))))
               intents))

      (is
       (every? 
        (fn [[_set path v]]
          (=
           (inc (spec/select-one (path->spec path)
                                 arg))
           (spec/select-one
            (path->spec path)
            (spec/transform (path->spec path)
                            inc
                            arg))))
        intents))))


  (let [arg {:obj {:a 1 :b 2 :c 3}}
        intents (ui/key-press
                (seq-nth3 arg)
    "s")]
    (is (= #{:a 1 :b 2}
           (into #{}
                 (map (fn [[_set path v]]
                        (spec/select-one (path->spec path)
                                         arg)))
                 intents)))))


(defui when-let-ui1 [{:keys [obj]}]
  (when-let [{:keys [a]} obj]
    (ui/on
     :key-press
     (fn [s]
       [[:set $a s]]))))

(deftest when-let-test
  (let [arg {:obj {:a 1}}
        intents (ui/key-press
                 (when-let-ui1 arg)
                 "s")
        [set_ path v :as intent] (first intents)]
    (is
     (= 1
        (spec/select-one (path->spec path)
                         arg)))

    (is
     (= {:obj {:a 2}}
        (spec/setval (path->spec path)
                     2
                     arg)))

    (is
     (= {:obj {:a 2}}
        (spec/transform (path->spec path)
                        inc
                        arg))))

  (let [arg {:obj nil}
        intents (ui/key-press
                 (when-let-ui1 arg)
                 "s")]
    (is (empty? intents))))

(defui if-let-ui1 [{:keys [obj]}]
  (if-let [{:keys [a]} obj]
    (ui/on
     :key-press
     (fn [s]
       [[:set $a s]]))))

(defui if-let-ui2 [{:keys [obj not-obj]}]
  (if-let [{:keys [a]} obj]
    (ui/on
     :key-press
     (fn [s]
       [[:set $a s]]))
    (ui/on
     :key-press
     (fn [s]
       [[:set $not-obj :obj]]))))

(deftest if-let-test
  (let [arg {:obj {:a 1}}
        intents (ui/key-press
                 (if-let-ui1 arg)
                 "s")
        [set_ path v :as intent] (first intents)]
    (is
     (= 1
        (spec/select-one (path->spec path)
                         arg)))

    (is
     (= {:obj {:a 2}}
        (spec/setval (path->spec path)
                     2
                     arg)))

    (is
     (= {:obj {:a 2}}
        (spec/transform (path->spec path)
                        inc
                        arg))))

  (let [arg {:obj nil}
        intents (ui/key-press
                 (if-let-ui1 arg)
                 "s")]
    (is (empty? intents)))

  (let [arg {:obj {:a 1}}
        intents (ui/key-press
                 (if-let-ui2 arg)
                 "s")
        [set_ path v :as intent] (first intents)]
    (is
     (= 1
        (spec/select-one (path->spec path)
                         arg)))

    (is
     (= {:obj {:a 2}}
        (spec/setval (path->spec path)
                     2
                     arg)))

    (is
     (= {:obj {:a 2}}
        (spec/transform (path->spec path)
                        inc
                        arg))))

  (let [arg {:not-obj 1}
        intents (ui/key-press
                 (if-let-ui2 arg)
                 "s")
        [set_ path v :as intent] (first intents)]
    (is
     (= 1
        (spec/select-one (path->spec path)
                         arg)))

    (is
     (= {:not-obj 2}
        (spec/setval (path->spec path)
                     2
                     arg)))

    (is
     (= {:not-obj 2}
        (spec/transform (path->spec path)
                        inc
                        arg)))))

(defprotocol ITestExtract
  :extend-via-metadata true
  (-test-extract [elem m]))

(extend-protocol ITestExtract
  nil
  (-test-extract [this m] nil)

  Object
  (-test-extract [this m]
    (mapcat #(-test-extract % m) (ui/children this))))


(defn extract [elem m]
  (-test-extract elem m))

(defn on-extract [f elem]
  (vary-meta elem
             assoc `-test-extract (fn [_ m]
                                    (f m))))


(do
  (defui non-literal-target [{:keys [a b
                                     has-default
                                     ^::component/contextual
                                     is-context]
                              :or {has-default 42}
                              :as arg}]
    (on-extract
     (fn [m]
       (case (:sym m)
         a [a $a]
         b [b $b]
         has-default [has-default $has-default]
         is-context [is-context $is-context]
         all [[a $a]
              [b $b]
              [has-default $has-default]
              [is-context $is-context]]
         arg [arg]
         nil))
     []))

  (defui non-literal-origin [{:keys [m]}]
    (let [nle (get extra :foo)]
     (non-literal-target (assoc m
                                :extra nle
                                ;; :$extra $nle
                                ))))
  (comment
    (clojure.pprint/pprint
     (extract (non-literal-origin {:m {:a 12
                                       :has-default 4}
                                   :context {:is-context 13}})
              '{:sym arg})))
  ,
  )


