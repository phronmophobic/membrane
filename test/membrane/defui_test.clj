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
