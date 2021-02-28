(ns membrane.autoui
  #?(:cljs
     (:require-macros [membrane.component
                       :refer [defui defeffect]]
                      [membrane.autoui
                       :refer [defgen]]))
  (:require #?(:clj [membrane.skia :as skia])
            #?(:cljs membrane.webgl)
            ;; #?(:cljs membrane.vdom)
            [membrane.ui :as ui
             :refer [vertical-layout
                     translate
                     horizontal-layout
                     label
                     with-color
                     bounds
                     spacer
                     on]]

            [com.rpl.specter :as spec]
            #?(:cljs [cljs.reader :refer [read-string]])
            #?(:cljs [membrane.eval])
            #?(:cljs [cljs.core.async :refer [put! chan <! timeout dropping-buffer promise-chan]
                      :as async])
            #?(:cljs [cognitect.transit :as transit])
            #?(:cljs [cljs.js :as cljs])

            [membrane.component :as component
             :refer [#?(:clj defui)
                     #?(:clj defeffect)]]
            #?(:clj [clojure.data.json :as json])
            [membrane.basic-components :as basic
             :refer [button
                     textarea]]
            [clojure.spec.alpha :as s]
            [spec-provider.provider :as sp]
            [clojure.spec.gen.alpha :as gen])
  #?(:clj (:gen-class)))

;; layouts
;; https://docs.microsoft.com/en-us/xamarin/xamarin-forms/user-interface/controls/layouts
;; - grid
;; - stack?
;; https://www.crazyegg.com/blog/guides/great-form-ui-and-ux/

;; extra elements
;; - multi select
;; - radio button
;; - collapsable

;; Show all selection options if under 6

(defn imeta? [obj]
  #?(:clj (instance? clojure.lang.IObj obj)
     :cljs (satisfies? IMeta obj)))

(defn maybe-with-meta [obj m]
  (if (imeta? obj)
    (with-meta obj m)
    obj))

(s/def :blades.user/info (s/keys :req [:blades.user/name
                                       :blades.user/look
                                       :blades.user/heritage
                                       :blades.user/background
                                       :blades.user/vice]))

(s/def :blades.user/name string?)
(s/def :blades.user/look (s/coll-of string? :kind vector?))

(s/def :blades.user/heritage (s/keys :req [:blades.heritage/description
                                           :blades.heritage/place]))
(s/def :blades.heritage/description string?)
(s/def :blades.heritage/place string?)

(s/def :blades.user/background (s/keys :req [:blades.background/description
                                             :blades/place]))
(s/def :blades.background/description string?)
(s/def :blades/place string?)

(s/def :blades.user/vice (s/keys :req [:blades.vice/description
                                       :blades.vice/category
                                       :blades/person]))
(s/def :blades.vice/description string?)
(s/def :blades.vice/category string?)
(s/def :blades/person string?)





#_(def blades-json
  {
   :info {:name "Drav Farros"
                      :look ["Male"
                                         "Long Cloak"
                                         "Tall Boots"
                                         "Tricorn Hat"
                                         "Collard Shirt"
                                         "Waistcoat"]
                      :heritage {:description "Lowly crafters who couldn't pay the bills"
                                             :place "Akoros"}
                      :background {:description "Grew up on the streets since there was no food at home"
                                               :place "Underworld"}
                      :vice {:description "A thug with dulusions of grandeur, pleasure are suitably \"high\" class. Not that I can tell the difference."
                                         :person "Cyrene"
                                         :category "Pleasure"}}
   :status
   {:stress 4
    :traumas ["Cold"
              "Haunted"]
    :harms ["welt on the back of the head"
            "holding a sword"]
    :armor {:armor false
            :heavy false
            :special true}}

   ;; :abilities
   ;; []

   :attributes
   {:insight {:level 1
              :hunt 0
              :study 1
              :survey 1
              :tinker 0}
    :prowess {:level 1
              :finesse 1
              :prowl 1
              :skirmish 3
              :wreck 2}
    :resolve {:level 4
              :attrune 1
              :command 3
              :consort 0
              :sway 1}}})

(def blades-json
  {
   :blades.user/info {:blades.user/name "Drav Farros"
                      :blades.user/look ["Male"
                                         "Long Cloak"
                                         "Tall Boots"
                                         "Tricorn Hat"
                                         "Collard Shirt"
                                         "Waistcoat"]
                      :blades.user/heritage {:blades.heritage/description "Lowly crafters who couldn't pay the bills"
                                             :blades.heritage/place "Akoros"}
                      :blades.user/background {:blades.background/description "Grew up on the streets since there was no food at home"
                                               :blades/place "Underworld"}
                      :blades.user/vice {:blades.vice/description "A thug with dulusions of grandeur, pleasure are suitably \"high\" class. Not that I can tell the difference."
                                         :blades/person "Cyrene"
                                         :blades.vice/category "Pleasure"}}
   :blades/status
   {:blades.status/stress 4
    :blades.status/traumas ["Cold"
              "Haunted"]
    :blades.status/harms ["welt on the back of the head"
                          "holding a sword"]
    :blades.status/armor {:blades.armor/armor false
                          :blades.armor/heavy false
                          :blades.armor/special true}}

   ;; :abilities
   ;; []

   :attributes
   {:insight {:level 1
              :hunt 0
              :study 1
              :survey 1
              :tinker 0}
    :prowess {:level 1
              :finesse 1
              :prowl 1
              :skirmish 3
              :wreck 2}
    :resolve {:level 4
              :attrune 1
              :command 3
              :consort 0
              :sway 1}}})

(defn inc-context-depth [ctx]
  (-> ctx
      (update-in [:depth] inc)
      (dissoc :spec)))

(defn list-editor [])

(def gen-editors (atom {}))

(defprotocol IGenEditor
  :extend-via-metadata true
  (gen-editor [this sym]))

(defprotocol ISubGens
  (subgens [this]))

(defprotocol IEditableProps
  (editable-props [this]))

(defprotocol IGenInspector
  (gen-inspector [this]))


(extend-type #?(:clj Object
                :cljs default)
  ISubGens
  (subgens [this]
    nil))

(extend-type #?(:clj Object
                :cljs default)
  IEditableProps
  (editable-props [this]
    nil))

(extend-type #?(:clj Object
                :cljs default)
  IGenInspector
  (gen-inspector [this]
    nil))

(extend-type nil
  IGenEditor
  (gen-editor [this sym]
    (ui/label "got null editor")))

(extend-type nil
  ISubGens
  (subgens [this]
    (prn "calling subgens with nil")
    nil))

;; (extend-type nil
;;   IGenProps
;;   (genprops [this]
;;     (prn "calling genprops with nil")
;;     nil))


(extend-type nil
  IGenInspector
  (gen-inspector [this]
    nil))


(defprotocol IGenPred
  (gen-pred? [this obj]))

(defmulti can-gen-from-spec? (fn [new-gen-type specs spec]
                               new-gen-type))

(declare best-gen)
(defmulti re-gen (fn [new-gen-type specs spec context]
                   new-gen-type))


(defmacro defgen [name params & body]
  `(let [ret#
         (defrecord ~name ~params
           ~@body)]
     (swap! gen-editors assoc (quote ~name) ret#)
     ret#))

;; (defmacro def-gen
;;   ([name pred body]
;;    `(def-gen ~name ~pred nil ~body))
;;   ([name pred children body]
;;    `(let [gen# (with-meta
;;                  {:pred ~pred
;;                   :children ~children
;;                   :generate (fn [~'sym ~'obj ~'children ~'context]
;;                               ~body)}
;;                  {(quote ~`gen-editor)
;;                   (fn [this# sym# obj# children# context#]
;;                     ((:generate this#) sym# obj# children# context#))})]
;;       (swap! gen-editors assoc (quote ~name) gen#)
;;       (def ~name gen#))))


(defgen OrGen [opts context]
  ;; :preds in opts takes in a [pred? gen]
  ;; pred? should be a fully qualified symbol pointing to a single arity predicate function

  IGenPred
  (gen-pred? [this obj]
    (every? #(% obj) (map first (:preds opts))))
  IGenEditor
  (gen-editor [this sym]
    `(cond
       ~@(apply concat
                (for [[pred? gen] (:preds opts)]
                  (do
                    (assert (symbol? pred?) "pred? must be a symbol")
                    [`(~pred? ~sym)
                     (gen-editor gen sym)]))))))


(defmethod re-gen OrGen [_ specs spec context]
  (let [key-preds (rest spec)]
    (->OrGen {:preds (for [[k pred] (partition 2 key-preds)]
                       [[pred (best-gen specs pred context)]])}
             context)))

(defmethod can-gen-from-spec? OrGen [this specs spec]
  (and (seq? spec)
       (#{'clojure.spec.alpha/or
          'cljs.spec.alpha/or} (first spec))))

(defgen SimpleTextAreaGen [opts context]
  
  IGenPred
  (gen-pred? [this obj]
    (string? obj))
  IGenEditor
  (gen-editor [this sym]
    `(basic/textarea {:text ~sym})))

(defmethod re-gen SimpleTextAreaGen [_ specs spec context]
  (->SimpleTextAreaGen {} context))

(defmethod can-gen-from-spec? SimpleTextAreaGen [this specs spec]
  (#{'clojure.core/string?
     'cljs.core/string?} spec ))


(defui number-counter-inspector [{:keys [gen]}]
  (vertical-layout
   (let [min (get-in gen [:opts :min])
         min-checked? (get extra :min-checked?)
         last-min (get extra :last-min)]
     (horizontal-layout
      (ui/label "min: ")
      (on
       :membrane.basic-components/toggle
       (fn [$min-checked?]
         (conj
          (if min-checked?
            [[:set $min nil]
             [:set $last-min min]]
            [[:set $min (or last-min 0)]])
          [:membrane.basic-components/toggle $min-checked?]))
       (basic/checkbox {:checked? min-checked?}))
      (when min
        (basic/counter {:num min}))))
   (let [max (get-in gen [:opts :max])
         max-checked? (get extra :max-checked?)
         last-max (get extra :last-max)]
     (horizontal-layout
      (ui/label "max: ")
      (on
       :membrane.basic-components/toggle
       (fn [$max-checked?]
         (conj
          (if max-checked?
            [[:set $max nil]
             [:set $last-max max]]
            [[:set $max (or last-max 0)]])
          [:membrane.basic-components/toggle $max-checked?]))
       (basic/checkbox {:checked? max-checked?}))
      (when max
        (basic/counter {:num max}))))))

(defgen NumberCounterGen [opts context]

  IGenInspector
  (gen-inspector [this]
    #'number-counter-inspector)
  
  IGenPred
  (gen-pred? [this obj]
    (number? obj))
  IGenEditor
  (gen-editor [this sym]
    `(if (number? ~sym)
       (basic/counter {:num ~sym})
       (ui/label (str ~sym " is NaN!"))))
  )

(defmethod re-gen NumberCounterGen [_ specs spec context]
  (->NumberCounterGen {} context))

(defmethod can-gen-from-spec? NumberCounterGen [this specs spec]
  (#{'clojure.core/integer?
     'cljs.core/integer?} spec ))


(defui number-slider-inspector [{:keys [gen]}]
  (vertical-layout
   (let [min (get-in gen [:opts :min] 0)]
     (horizontal-layout
      (ui/label "min: ")
      (basic/counter {:num min})))
   (let [max (get-in gen [:opts :max] 100)]
     (horizontal-layout
      (ui/label "max: ")
      (basic/counter {:num max})))
   (let [max-width (get-in gen [:opts :max-width] 100)]
     (horizontal-layout
      (ui/label "max-width: ")
      (basic/counter {:num max-width})))
   (let [integer? (get-in gen [:opts :integer?] true)]
     (horizontal-layout
      (ui/label "integer? ")
      (basic/checkbox {:checked? integer?})))))

(defgen NumberSliderGen [opts context]

  IGenInspector
  (gen-inspector [this]
    #'number-slider-inspector)
  
  IGenPred
  (gen-pred? [this obj]
    (number? obj))
  IGenEditor
  (gen-editor [this sym]
    `(if (number? ~sym)
       (basic/number-slider {:num ~sym
                             :min ~(get opts :min 0)
                             :max ~(get opts :max 100)
                             :integer? ~(get opts :integer? true)
                             :max-width ~(get opts :max-width 100)})
       (ui/label (str ~sym " is not a NaN!"))))
  )

(defmethod re-gen NumberSliderGen [_ specs spec context]
  (->NumberSliderGen {} context))

(defmethod can-gen-from-spec? NumberSliderGen [this specs spec]
  (#{'clojure.core/integer?
     'cljs.core/integer?
     'clojure.core/number?
     'cljs.core/number?
     'clojure.core/float?
     'cljs.core/float?
     'clojure.core/double?
     'cljs.core/double?}
   spec))


(defgen CheckboxGen [opts context]

  IGenPred
  (gen-pred? [this obj]
    (boolean? obj))
  IGenEditor
  (gen-editor [this sym]
    `(basic/checkbox {:checked? ~sym})))


(defmethod re-gen CheckboxGen [_ specs spec context]
  (->CheckboxGen {} context))

(defmethod can-gen-from-spec? CheckboxGen [this specs spec]
  (#{'clojure.core/boolean?
     'cljs.core/boolean?} spec))


(defgen TitleGen [opts context]
  IGenPred
  (gen-pred? [this obj]
    (or (string? obj) (keyword? obj)))
  IGenEditor
  (gen-editor [this sym]
    `(ui/label (let [s# (str (if (keyword? ~sym)
                               (name ~sym)
                               ~sym))]
                 (clojure.string/capitalize s#))
               (ui/font nil ~(max 16 (- 22 (* 4 (get context :depth 0)))))))
  )

(defmethod re-gen TitleGen [_ specs spec context]
  (->TitleGen {} context))

(defmethod can-gen-from-spec? TitleGen [this specs spec]
  (#{'clojure.core/string?
     'cljs.core/string?
     'clojure.core/keyword?
     'cljs.core/keyword?} spec))


(defgen LabelGen [opts context]
  ;; IEditableProps
  ;; (editable-props [this]
  ;;   {:font-size (number-inspector :max 40
  ;;                                 :min 12)})

  ;; IGenProps
  ;; (genprops [this]
  ;;   [:font-size])
  IGenPred
  (gen-pred? [this obj]
    (or (string? obj) (keyword? obj)))
  IGenEditor
  (gen-editor [this sym]
    `(ui/label ~sym
               (ui/font nil ~(:font-size opts))))
  )

(defmethod re-gen LabelGen [_ specs spec context]
  (->LabelGen {} context))

(defmethod can-gen-from-spec? LabelGen [this specs spec]
  true)


#_(def-gen static-vector
  vector?
  {:x identity}
  (let [x-sym (gensym "x-")]
    `(apply
      vertical-layout
      (for [~x-sym ~sym]
        ~(ui-code x-sym (first obj) (inc-context-depth context))))))

#_(def-gen static-horizontal-vector
  vector?
  {:x identity}
  (let [x-sym (gensym "x-")
        ]
    `(apply
      horizontal-layout
      (for [~x-sym ~sym]
        ~(ui-code x-sym (first obj) (inc-context-depth context))))))

(defn delete-X []
  (ui/with-style :membrane.ui/style-stroke
    (ui/with-color
      [1 0 0]
      (ui/with-stroke-width
        3
        [(ui/path [0 0]
                  [10 10])
         (ui/path [10 0]
                  [0 10])]))))

(defn ->$sym [sym]
  (symbol (str "$" (name sym)))
  )

#_(def-gen dynamic-vector
  vector?
  (let [x-sym (gensym "x-")]
    `(apply
      vertical-layout
      (basic/button :text "New"
                    :on-click
                    (fn []
                      [[:update ~(->$sym sym) conj (first ~sym)]]))
      (for [~x-sym ~sym]
        (horizontal-layout
         (on
          :mouse-down
          (fn [_#]
            [[:delete ~(->$sym x-sym)]])
          (delete-X))
         ~(ui-code x-sym (first obj) (inc-context-depth context)))))))

#_(def-gen checkbox
  boolean?
  `(basic/checkbox :checked? ~sym))

(defn stack-img [outer-layout inner-layout inner-elem outer-elem]
  (let [padding 1
        rect-size 4]
    (apply outer-layout
           (interpose
            (spacer 0 padding)
            (for [i (range 3)]
              (inner-layout
               (inner-elem rect-size)
               (spacer padding 0)
               (outer-elem rect-size)))))))


(defn kv-vertical-img []
  (stack-img vertical-layout
             horizontal-layout
             #(ui/with-style :membrane.ui/style-stroke
                (ui/rectangle % %))
             #(ui/with-style :membrane.ui/style-stroke-and-fill
                (ui/rectangle % %))))

(defn vk-vertical-img []
  (stack-img vertical-layout
             horizontal-layout
             #(ui/with-style :membrane.ui/style-stroke-and-fill
                (ui/rectangle % %))
             #(ui/with-style :membrane.ui/style-stroke
                (ui/rectangle % %))
             ))

(defn kv-horizontal-img []
  (stack-img horizontal-layout
             vertical-layout
             #(ui/with-style :membrane.ui/style-stroke
                (ui/rectangle % %))
             #(ui/with-style :membrane.ui/style-stroke-and-fill
                (ui/rectangle % %))))

(defn vk-horizontal-img []
  (stack-img horizontal-layout
             vertical-layout
             #(ui/with-style :membrane.ui/style-stroke-and-fill
                (ui/rectangle % %))
             #(ui/with-style :membrane.ui/style-stroke
                (ui/rectangle % %))
             ))

(defn kv-all-horizontal-img []
  (stack-img horizontal-layout
             horizontal-layout
             #(ui/with-style :membrane.ui/style-stroke
                (ui/rectangle % %))
             #(ui/with-style :membrane.ui/style-stroke-and-fill
                (ui/rectangle % %))))

(defn vk-all-horizontal-img []
  (stack-img horizontal-layout
             horizontal-layout
             #(ui/with-style :membrane.ui/style-stroke-and-fill
                (ui/rectangle % %))
             #(ui/with-style :membrane.ui/style-stroke
                (ui/rectangle % %))
             ))

(defn kv-all-vertical-img []
  (stack-img vertical-layout
             vertical-layout
             #(ui/with-style :membrane.ui/style-stroke
                (ui/rectangle % %))
             #(ui/with-style :membrane.ui/style-stroke-and-fill
                (ui/rectangle % %))))

(defn vk-all-vertical-img []
  (stack-img vertical-layout
             vertical-layout
             #(ui/with-style :membrane.ui/style-stroke-and-fill
                (ui/rectangle % %))
             #(ui/with-style :membrane.ui/style-stroke
                (ui/rectangle % %))
             ))

(defn test-view []
  (apply vertical-layout
         (interpose (spacer 0 10)
                    [(kv-vertical-img)
                     (vk-vertical-img)
                     (kv-horizontal-img)
                     (vk-horizontal-img)
                     (kv-all-horizontal-img)
                     (vk-all-horizontal-img)
                     (kv-all-vertical-img)
                     (vk-all-vertical-img)])))

(defui option-toggle [{:keys [flag true-text false-text]}]
  (horizontal-layout
   (ui/button true-text
              (fn []
                (when-not flag
                  [[:set $flag true]]))
              flag)
   (ui/button false-text
              (fn []
                (when flag
                  [[:set $flag false]]))
              (not flag))))

(defui static-map-gen-inspector [{:keys [gen]}]
  (let [horizontal-rows? (get-in gen [:opts :horizontal-rows?])
        horizontal-cols? (get-in gen [:opts :horizontal-cols?])
        kv-order? (get-in gen [:opts :kv-order?] true)]
    
    (vertical-layout

     (let [k-elem #(ui/with-style :membrane.ui/style-stroke
                     (ui/rectangle % %))
           v-elem #(ui/with-style :membrane.ui/style-stroke-and-fill
                     (ui/rectangle % %))
           
           [inner-elem outer-elem]
           (if kv-order?
             [k-elem v-elem]
             [v-elem k-elem])]

       [(spacer 45 45)
        (ui/padding 5 5
                    (stack-img (if horizontal-rows?
                                 horizontal-layout
                                 vertical-layout)
                               (if horizontal-cols?
                                 horizontal-layout
                                 vertical-layout)
                               inner-elem
                               outer-elem))])

     (ui/label "key value layout order")
     (option-toggle {:flag kv-order?
                     :true-text "kv"
                     :false-text "vk"})
     (ui/label "row direction")
     (option-toggle {:flag horizontal-rows?
                     :true-text "horizontal"
                     :false-text "vertical"})
     (ui/label "column direction")
     (option-toggle {:flag horizontal-cols?
                     :true-text "horizontal"
                     :false-text "vertical"})
     
     
     
     
     
     )
    #_(horizontal-layout
       
     
       (ui/label "horizontal?")
       (ui/translate 5 5
                     (basic/checkbox :checked? horizontal?)))))

(defgen StaticMapGen [opts context]

  IGenInspector
  (gen-inspector [this]
    #'static-map-gen-inspector)

  IEditableProps
  (editable-props [this]
    {:k gen-inspector
     :v gen-inspector})

  ISubGens
  (subgens [this]
    [[:opts :k]
     [:opts :v]])
  
  
  IGenPred
  (gen-pred? [this obj]
    (map? obj))
  IGenEditor
  (gen-editor [this sym]
    (let [k-sym (gensym "k-")
          v-sym (gensym "v-")
          klabel-sym (gensym "klabel-")
          table-sym (gensym "table-")]
      `(ui/translate 10 10
                     (let [~table-sym (for [[~k-sym ~v-sym] ~sym]
                                        ~(let [k-elem
                                               `(let [~klabel-sym (maybe-with-meta
                                                                   ~(gen-editor (:k opts) k-sym)
                                                                   ~{:relative-identity '(quote [(keypath :opts)
                                                                                                 (keypath :k)])})]
                                                  (vertical-layout
                                                   ~klabel-sym
                                                   ~(when (<= (:depth context) 1)
                                                      `(let [lbl-width# (ui/width ~klabel-sym)]
                                                         (ui/with-style :membrane.ui/style-stroke
                                                           (ui/with-color [0 0 0]
                                                             (ui/path [0 0] [lbl-width# 0])))))))
                                               v-elem
                                               `(let [~v-sym (get ~sym ~k-sym)]
                                                  (maybe-with-meta
                                                   ~(gen-editor (:v opts) v-sym)
                                                   ~{:relative-identity '(quote [(keypath :opts)
                                                                                 (keypath :v)])}))]
                                           (if (get opts :kv-order? true)
                                             [k-elem v-elem]
                                             [v-elem k-elem])))
                           ]
                       ~(if (= (:horizontal-rows? opts)
                               (:horizontal-cols? opts))
                          (let [layout (if (:horizontal-rows? opts)
                                         `horizontal-layout
                                         `vertical-layout)] 
                            `(apply ~layout
                                    (apply concat ~table-sym)))
                          `(ui/table-layout ~(if (:horizontal-rows? opts)
                                               table-sym
                                               `[(map first ~table-sym)
                                                 (map second ~table-sym)]))
                          )
                       )
                     #_(apply
                        ~(if (:horizontal-rows? opts)
                           `horizontal-layout
                           `vertical-layout)
                        (for [[~k-sym ~v-sym] ~sym]
                        
                          (~(if (:horizontal-cols? opts)
                              `horizontal-layout
                              `vertical-layout)
                           ~@(let [k-elem
                                   `(let [~klabel-sym (maybe-with-meta
                                                       ~(gen-editor (:k opts) k-sym)
                                                       ~{:relative-identity '(quote [(keypath :opts)
                                                                                     (keypath :k)])})]
                                      (vertical-layout
                                       ~klabel-sym
                                       ~(when (<= (:depth context) 1)
                                          `(let [lbl-width# (ui/width ~klabel-sym)]
                                             (ui/with-style :membrane.ui/style-stroke
                                               (ui/with-color [0 0 0]
                                                 (ui/path [0 0] [lbl-width# 0])))))))
                                   v-elem
                                   `(let [~v-sym (get ~sym ~k-sym)]
                                      (maybe-with-meta
                                       ~(gen-editor (:v opts) v-sym)
                                       ~{:relative-identity '(quote [(keypath :opts)
                                                                     (keypath :v)])}))]
                               (if (get opts :kv-order? true)
                                 [k-elem v-elem]
                                 [v-elem k-elem]))))
                        )))))


(defmethod re-gen StaticMapGen [_ specs spec context]
  (let [subcontext (inc-context-depth context)]
    (let [spec-fn (first spec)]
      (case spec-fn 

        (clojure.spec.alpha/map-of
         cljs.spec.alpha/map-of)
        (let [[kpred vpred & opts] (rest spec)
              k-spec (if (keyword? kpred)
                       (get specs kpred)
                       kpred)
              k-gen (if (can-gen-from-spec? TitleGen specs k-spec)
                      (->TitleGen {} (assoc subcontext :spec k-spec))
                      (best-gen specs k-spec subcontext))

              v-spec (if (keyword? vpred)
                       (get specs vpred)
                       vpred)]
          (->StaticMapGen {:k k-gen
                           :v (best-gen specs v-spec subcontext)}
                          context))

        (clojure.spec.alpha/keys
         cljs.spec.alpha/keys)
        (let [[& {:keys [req req-un opt opt-un gen]}] (rest spec)
              key-specs (concat req req-un opt opt-un)
              first-key-spec (get specs (first key-specs))]
          (->StaticMapGen {:k (->TitleGen {} (dissoc subcontext :spec))
                           :v (best-gen specs first-key-spec subcontext)}
                          context))
        
        )))
  )

(defmethod can-gen-from-spec? StaticMapGen [this specs spec]
  (and (seq? spec)
       (let [spec-fn (first spec)]
         (or (#{'clojure.spec.alpha/map-of
                'cljs.spec.alpha/map-of}
              spec-fn )
             (and (#{'clojure.spec.alpha/keys
                     'cljs.spec.alpha/keys} spec-fn )
                  ;; all key specs should return the same gen type
                  ;; this is so ugly. forgive me
                  (let [[& {:keys [req req-un opt opt-un gen]}] (rest spec)
                        key-specs (concat req req-un opt opt-un)
                        context {:depth 0}
                        example-gen-type (type (best-gen specs (get specs (first key-specs)) context))]
                    (every? #(= example-gen-type
                                (type
                                 (best-gen specs
                                           (get specs %) context)))
                            (rest key-specs))))))))


(defgen StaticSeqGen [opts context]

  ISubGens
  (subgens [this]
    [[:opts :vals]])
  
  IGenPred
  (gen-pred? [this obj]
    (and (seqable? obj)
         (not (or (map? obj)
                  (string? obj)))))
  IGenEditor
  (gen-editor [this sym]
    (let [v-sym (gensym "v-")]
      `(ui/translate 10 10
                     (apply
                      ~(if (:horizontal? opts)
                         `horizontal-layout
                         `vertical-layout)
                      (for [~v-sym ~sym]
                        (maybe-with-meta
                         ~(gen-editor (:vals opts) v-sym)
                         ~{:relative-identity '(quote [(keypath :opts)
                                                       (keypath :vals)])})))))))

(defmethod re-gen StaticSeqGen [_ specs spec context]
  (assert (can-gen-from-spec? StaticSeqGen specs spec))
  (->StaticSeqGen {:vals (best-gen specs (second spec) (inc-context-depth context))}
                  context))

(defmethod can-gen-from-spec? StaticSeqGen [this specs spec]
  (and (seq? spec)
       (#{'clojure.spec.alpha/coll-of
          'cljs.spec.alpha/coll-of} (first spec))))

(defui keymap-gen-inspector [{:keys [gen]}]
  (let [bordered? (get-in gen [:opts :bordered?] true)]
    (horizontal-layout
     (ui/label "bordered?")
     (ui/translate 5 5
                   (basic/checkbox {:checked? bordered?})))))

(defgen KeyMapGen [opts context]

  IGenInspector
  (gen-inspector [this]
    #'keymap-gen-inspector)

  ISubGens
  (subgens [this]
    (conj (map #(vector :opts :val-gens %) (keys (:val-gens opts)))
          [:opts :k]))
  IGenPred
  (gen-pred? [this obj]
    (map? obj))
  IGenEditor
  (gen-editor [this sym]
    (let [body `(vertical-layout
                ~@(apply concat
                         (for [[k subgen] (:val-gens opts)]
                           (let [v-sym (gensym "v-")]
                             `[(maybe-with-meta
                                ~(gen-editor (:k opts) k)
                                ~{:relative-identity '(quote [(keypath :opts)
                                                              (keypath :k)])})
                               (let [~v-sym (get ~sym ~k)]
                                 (maybe-with-meta
                                  ~(gen-editor subgen v-sym)
                                  ~{:relative-identity [(quote '(keypath :opts))
                                                        (quote '(keypath :val-gens))
                                                        (list 'list
                                                              (list 'quote 'keypath)
                                                              k)]}))]))))
          body (if (get opts :bordered? true)
                 `(ui/bordered [5 5] ~body)
                 body)]
      body)))


(defmethod re-gen KeyMapGen [_ specs spec context]
  (assert (can-gen-from-spec? KeyMapGen specs spec))
  (let [subcontext (inc-context-depth context)
        [& {:keys [req req-un opt opt-un gen]}] (rest spec)]
    (->KeyMapGen {:k (->TitleGen {} (dissoc subcontext :spec))
                  :val-gens (into {}
                                  (concat
                                   (for [k (concat req opt)]
                                     [k (best-gen specs (get specs k) subcontext)])
                                   (for [k (concat req-un opt-un)]
                                     [(keyword (name k)) (best-gen specs (get specs k) subcontext)])))
                  :bordered? (if-let [depth (:depth context)]
                               (pos? depth)
                               true)}
                 context)))

(defmethod can-gen-from-spec? KeyMapGen [this specs spec]
  (and (seq? spec)
       (#{'clojure.spec.alpha/keys
          'cljs.spec.alpha/keys} (first spec))))


(defui cat-gen-inspector [{:keys [gen]}]
  (let [bordered? (get-in gen [:opts :bordered?] true)]
    (horizontal-layout
     (ui/label "bordered?")
     (ui/translate 5 5
                   (basic/checkbox {:checked? bordered?})))))

(defgen CatGen [opts context]

  IGenInspector
  (gen-inspector [this]
    #'cat-gen-inspector)

  ISubGens
  (subgens [this]
    (map #(vector :opts :val-gens %) (range (count (:val-gens opts)))))
  IGenPred
  (gen-pred? [this obj]
    (and (seqable? obj)
         (not (or (map? obj)
                  (string? obj)))))
  IGenEditor
  (gen-editor [this sym]
    (let [body `(vertical-layout
                ~@(apply concat
                         (for [[i subgen] (map-indexed vector (:val-gens opts))]
                           (let [v-sym (gensym "v-")]
                             `[(let [~v-sym (nth ~sym ~i)]
                                 (maybe-with-meta
                                  ~(gen-editor subgen v-sym)
                                  ~{:relative-identity [(quote '(keypath :opts))
                                                        (quote '(keypath :val-gens))
                                                        (list 'list
                                                              (list 'quote 'nth)
                                                              i)]}))]))))
          body (if (get opts :bordered? true)
                 `(ui/bordered [5 5] ~body)
                 body)]
      body)))


(defmethod re-gen CatGen [_ specs spec context]
  (assert (can-gen-from-spec? CatGen specs spec))
  (let [subcontext (inc-context-depth context)
        val-specs (take-nth 2 (drop 2 spec))]
    (->CatGen {:val-gens (vec (for [val-spec val-specs]
                                (best-gen specs (get specs val-spec val-spec) subcontext)))
               :bordered? (if-let [depth (:depth context)]
                            (pos? depth)
                            true)}
                 context)))

(defmethod can-gen-from-spec? CatGen [this specs spec]
  (and (seq? spec)
       (= 'clojure.spec.alpha/cat (first spec))))


(defn first-matching-gen [obj]
  (some #(when ((:pred %) obj)
           %)
        (vals @gen-editors))
  )

#_(defn ui-code
  ([sym obj context]
   (let [generator (first-matching-gen obj)
         children (into
                   {}
                   (for [[k v] (:children generator)]
                     [k (fn [sym context]
                          (ui-code sym (first (v obj)) (inc-context-depth context)))]))]
     (assert generator (str "No generator for " (pr-str obj)) )
     (gen-editor generator sym obj children context))))

(defn auto-component [ui-name ge]
  (let [arg-sym 'obj]
    `(defui ~ui-name [{:keys [~arg-sym]}]
       (basic/scrollview {:bounds [800 800]
                          :body ~(gen-editor ge arg-sym)}))))

(defn def-auto-component [ui-name ge]
  (let [code (auto-component ui-name ge)]
     (eval code)))

#_(def-auto-component 'testblades (best-gen blades-json))

#_(def-auto-component map-example {:a 1
                                 :b 2
                                 :c 3})



(def testgen
  (let [context {:depth 0}
        subcontext (inc-context-depth context)]
    (->StaticMapGen {:k (->LabelGen {} subcontext)
                     :v (->NumberCounterGen {} subcontext) #_ (->LabelGen {} subcontext)}
                    context)))

;; todo
;; - have a way to set data
;; - have a way to generate the gen-tree
;; - have a way to swap out alternatives



(do
  

  (defn infer-specs
    ([obj]
     (infer-specs obj :membrane.autoui/infer-specs))
    ([obj spec-name]
     (let [inferred (sp/infer-specs [obj] spec-name)
           specs (into {}
                       (for [[def k spec] inferred]
                         [k spec]))]
       specs)))

  (defn best-gen
    ([obj]
     (let [spec-name :membrane.autoui/infer-specs
           specs (infer-specs obj spec-name)]
       (best-gen specs (get specs spec-name) {:depth 0
                                              :specs specs})))
    ([specs spec context]
     (let [gen (cond

                 (#{'clojure.core/string?
                    'cljs.core/string?} spec )
                 (->SimpleTextAreaGen {} context)
                 
                 (#{'clojure.core/boolean?
                    'cljs.core/boolean?} spec )
                 (->CheckboxGen {} context)

                 (#{'clojure.core/keyword?
                    'cljs.core/keyword?} spec )
                 (->TitleGen {} context)

                 (#{'clojure.core/simple-symbol?} spec )
                 (->TitleGen {} context)

                 (#{'clojure.core/integer?
                    'cljs.core/integer?
                    } spec )
                 (->NumberCounterGen {} context)

                 ('#{clojure.core/double?
                     cljs.core/double?
                     clojure.core/float?
                     cljs.core/float?} spec)
                 (->NumberSliderGen {} context)
                 

                 
                 (seq? spec)
                 (case (first spec)
                   (clojure.spec.alpha/coll-of
                    cljs.spec.alpha/coll-of)
                   (let [[pred & {:keys [into
                                         kind
                                         count
                                         max-count
                                         min-count
                                         distinct
                                         gen-max
                                         gen]}]
                         (rest spec)

                         subcontext (inc-context-depth context)
                         ]
                     (->StaticSeqGen {:vals (best-gen specs pred subcontext)}
                                     context)
                     )

                   clojure.spec.alpha/cat
                   (let []
                     (re-gen CatGen specs spec context))

                   clojure.spec.alpha/?
                   (let [pred (second spec)
                         subcontext (inc-context-depth context)]
                     (best-gen specs pred subcontext))

                   clojure.spec.alpha/*
                   (let [pred (second spec)
                         subcontext (inc-context-depth context)]
                     (->StaticSeqGen {:vals (best-gen specs pred subcontext)}
                                     context))

                   (clojure.spec.alpha/keys
                    cljs.spec.alpha/keys)
                   (re-gen KeyMapGen specs spec context)

                   (clojure.spec.alpha/or
                    cljs.spec.alpha/or)
                   (let [key-preds (rest spec)]
                     (->OrGen {:preds (for [[k pred] (partition 2 key-preds)]
                                        [[pred (best-gen specs pred context)]])}
                              context))
                   

                   ;; else
                   (str "unknown spec: " (first spec))
                   )

                 :else
                 (str "unknown spec: " spec)


                 )]
       (if (satisfies? IGenEditor gen)
         (assoc-in gen [:context :spec] spec)
         gen)))
    )

  

  

  #_(prn
   (let [obj blades-json]
     (best-gen obj))))


(defn gen-options [specs spec]
  (->> @gen-editors
       vals
       (filter #(can-gen-from-spec? % specs spec))))


(def testgen2
  (let [ge testgen]
    (->KeyMapGen
     {:k (:k (:opts ge))
      :val-gens {:a (->NumberCounterGen {} (:context (:v (:opts ge))))
                 :b (->LabelGen {} (:context (:v (:opts ge))))
                 :c (->TitleGen {} (:context (:v (:opts ge))))}}
     (:context ge))))



(defui foldable-section [{:keys [title body visible?]
                          :or {visible? true}}]
  (vertical-layout
   (on
    :mouse-down
    (fn [_]
      [[:update $visible? not]])
    [#_(ui/filled-rectangle [0.9 0.9 0.9] 200 20)
     (horizontal-layout
      (let [size 5]
        (ui/with-style :membrane.ui/style-stroke-and-fill
          (if visible?
            (ui/translate 5 8
                          (ui/path [0 0]
                                   [size size]
                                   [(* 2 size) 0]))
            (ui/translate 5 5
                          (ui/path [0 0]
                                   [size size]
                                   [0 (* 2 size)])))))
      (spacer 5 0)
      (ui/label title))
     ])
   (when visible?
     (ui/translate 10 0 body))))


(defn pr-label [obj]
    (let [s (pr-str obj)]
      (ui/label (subs s 0 (min (count s)
                               37)))))

(defn get-gen-name [ge]
  #?(:clj (.getSimpleName ge)
     :cljs (pr-str ge)))


;; forward declare
(defui gen-editor-inspector [{:keys [ge]}])
(defui gen-editor-inspector [{:keys [ge]}]
  (let [sub (subgens ge)]
    (foldable-section
     {:title (if-let [spec (-> ge :context :spec)]
               (pr-str spec)
               (get-gen-name (type ge)))
      :visible? (get extra [:foldable-visible $ge] true)
      :body
      (apply
       vertical-layout
       (when-let [spec (-> ge :context :spec)]
         (when-let [specs (-> ge :context :specs)]
           (vertical-layout
            (pr-label spec)
            (let [options (gen-options specs spec)]
              (apply horizontal-layout
                     (for [option options]
                       (basic/button {:text (get-gen-name option)
                                      :hover? (get extra [:switch :hover? option])
                                      :on-click
                                      (fn []
                                        [[:set $ge (re-gen option specs spec (:context ge))]])})))))))
       (when-let [inspector (gen-inspector ge)]
         (let [inspector-extra (get extra [$ge :extra])]
           (inspector {:gen ge :$gen $ge
                       :extra inspector-extra
                       :$extra $inspector-extra
                       :context context
                       :$context $context})))
       (for [k sub]
         (let [v (get-in ge k)]
           (gen-editor-inspector {:ge v}))))})))

(defn find-under
  ([pos elem delta]
   (find-under pos elem (Math/pow delta 2) nil []))
  ([pos elem d2 elem-index ident]
   (let [[x y] pos
         [ox oy] (ui/origin elem)
         [width height] (ui/bounds elem)
         local-x (int (- x ox))
         local-y (int (- y oy))
         new-pos [local-x local-y]
         elem-meta (meta elem)]
     (if-let [m elem-meta]
       (if-let [identity (:identity m)]
         (let [child-results (mapcat #(find-under new-pos % d2 elem-index [identity]) (ui/children elem))]
           (if (and (< (Math/pow local-x 2)
                       d2)
                    (< (Math/pow local-y 2)
                       d2))
             (conj child-results
                   {:elem elem
                    :pos new-pos
                    :identity identity})
             child-results))
         (if-let [relative-identity (:relative-identity m)]
           (let [identity (into ident relative-identity)
                 child-results (mapcat #(find-under new-pos % d2 elem-index identity) (ui/children elem))]
             (if (and (< (Math/pow local-x 2)
                         d2)
                      (< (Math/pow local-y 2)
                         d2))
               (conj child-results
                     {:elem elem
                      :pos new-pos
                      :identity identity})
               child-results))
           (mapcat #(find-under new-pos % d2 elem-index ident) (ui/children elem))))
       (mapcat #(find-under new-pos % d2 elem-index ident) (ui/children elem))))))


(defn draw-circles
  ([elem]
   (draw-circles elem [0 0]))
  ([elem pos]
   (let [[x y] pos
         [ox oy] (ui/origin elem)
         [width height] (ui/bounds elem)
         local-x (int (+ x ox))
         local-y (int (+ y oy))
         new-pos [local-x local-y]
         elem-meta (meta elem)]
     (if-let [m elem-meta]
       (if-let [identity (:identity m)]
         (let [child-results (mapcat #(draw-circles % new-pos) (ui/children elem))]
           (conj child-results
                 new-pos))
         (if-let [relative-identity (:relative-identity m)]
           (let [child-results (mapcat #(draw-circles % new-pos) (ui/children elem))]
             (conj child-results
                   new-pos))
           (mapcat #(draw-circles % new-pos) (ui/children elem))))
       (mapcat #(draw-circles % new-pos) (ui/children elem))))))





(def ^:dynamic *obj* nil)
(def ^:dynamic *$ge* nil)


(declare background-eval)
(defui eval-form [{:keys [form eval-context cache eval-key]}]
  (let [cache (or cache {})
        result (background-eval form eval-context cache $cache eval-key)]
    ;; (prn "result:" result)
    result))

(defui gen-editor-editor [{:keys [ge obj selected-ge-path ge-options]}]
  (horizontal-layout
   (basic/scrollview
    {:scroll-bounds [800 800]
     :body
     (let [body
           (ui/no-events
            (ui/try-draw
             (let [drawable
                   #?(:clj
                      (binding [*obj* obj
                                *$ge* []]
                        ;; (clojure.pprint/pprint (gen-editor ge 'obj))
                        (eval `(let [~'obj *obj*]
                                 (maybe-with-meta
                                  ~(gen-editor ge 'obj)
                                  {:identity *$ge*}))))
                      :cljs
                      (eval-form :form `(maybe-with-meta
                                         ~(gen-editor ge 'obj)
                                         {:identity ~'ident})
                                 :eval-context {'ident *$ge*
                                                'obj obj}
                                 :eval-key ge)
                      )
                   ]
               drawable)
             (fn [draw e]
               (println e)
               (draw (ui/label "whoops!")))))
           circles (delay
                     (mapv (fn [[x y]]
                             (ui/translate (- x 5) (- y 5)
                                           (ui/filled-rectangle [1 0 0]
                                                                10 10)))
                           (draw-circles body)))]

       [
        (on
         :mouse-down
         (fn [pos]
           (let [results (seq (find-under pos body 5))]
             (if-let [results results]
               (if (= (count results) 1)
                 (let [ident (:identity (first results))]
                   [[:set $selected-ge-path ident]])
                 [[:set $ge-options
                   {:pos pos
                    :options (vec
                              (for [{:keys [elem pos elem-index identity]} results]
                                [identity (get-gen-name
                                           (type (spec/select-one (component/path->spec [identity])
                                                                  ge)))]))}]])
               [[:set $selected-ge-path nil]
                [:set $ge-options nil]])))
         body)
        ;; @circles
        (when ge-options
          (let [options-pos (:pos ge-options)
                options (:options ge-options)]
            (ui/translate
             (first options-pos)
             (second options-pos)
             (on ::basic/select
                 (fn [_ $ident]
                   [[:set $ge-options nil]
                    [:set $selected-ge-path $ident]])
                 (basic/dropdown-list {:options options})))))])})
   (let [[edit-ge $edit-ge] (if selected-ge-path
                              [(spec/select-one (component/path->spec [selected-ge-path])
                                                ge)
                               [$ge selected-ge-path]]
                              [ge $ge])]
     (basic/scrollview
        {:scroll-bounds [800 800]
         :body
         (gen-editor-inspector {:ge edit-ge
                                :$ge $edit-ge})}))))

(defui test-edit [{:keys [title?]}]
  (vertical-layout
   (horizontal-layout
    (ui/label "title?")
    (basic/checkbox {:checked? title?}))
   (let [testgen (if title?
                   (-> testgen
                       (assoc-in [:opts :k] (->TitleGen {} (:context (get-in testgen [:opts :k]))))
                       (assoc-in [:opts :v] (->TitleGen {} (:context (get-in testgen [:opts :v])))))
                   
                   testgen)]
     (eval`(let [~'m {:a 1 :b 2}]
             ~(gen-editor testgen 'm))))))

(comment (let [obj blades-json
               ge (best-gen obj)]
           (def editor-state (skia/run (component/make-app
                                        #'gen-editor-editor
                                        {:ge ge
                                         :obj obj})))))
(defn start-blades []
  (let [obj blades-json
        ge (best-gen obj)]
    (def editor-state (skia/run (component/make-app #'gen-editor-editor {:ge ge
                                                                         :obj obj})))))



(defn start-spec []
  (let [obj (list 10 "Asfa")
        ge (let [specs {} #_(into {}
                               (for [[k v] (s/registry)
                                     :let [form (try
                                                  (s/form v)
                                                  (catch Exception e
                                                    nil))]
                                     :when form]
                                 [k form]))]
                   (best-gen specs
                             `(s/cat :foo integer? :bar string?)
                             {:depth 0
                              :specs specs}))]
    (def editor-state (skia/run (component/make-app #'gen-editor-editor {:ge ge
                                                                         :obj obj})))))

#_(defui blades-scroll [{:keys [obj]}]
    (basic/scrollview
     :scroll-bounds [800 800]
     :body
     (testblades :obj obj)))

;; examples of using macros with eval
;; https://github.com/clojure/clojurescript/blob/master/src/test/self/self_host/test.cljs#L392

;; from @mfikes on clojurians regarding AOT and macros
;; You have to roll your own way of doing that. Planck does this https://blog.fikesfarm.com/posts/2016-02-03-planck-macros-aot.html
;; The high-level idea is that you can use a build-time self-hosted compiler to compile macros namespaces down to JavaScript.
;; Also Replete does this as well, all in the name of fast loading. So, you can look at Plank and Replete as inspiration on how to do this. There might be other examples out there as well.

#?(:cljs
   (do

     (def cljstate (cljs/empty-state))


     (def aot-caches
       [

        "js/compiled/out.autouitest/clojure/zip.cljs.cache.json"
        "js/compiled/out.autouitest/clojure/string.cljs.cache.json"
        "js/compiled/out.autouitest/clojure/walk.cljs.cache.json"
        "js/compiled/out.autouitest/clojure/set.cljs.cache.json"
        "js/compiled/out.autouitest/cognitect/transit.cljs.cache.json"
        "js/compiled/out.autouitest/spec_provider/util.cljc.cache.json"
        "js/compiled/out.autouitest/spec_provider/merge.cljc.cache.json"
        "js/compiled/out.autouitest/spec_provider/rewrite.cljc.cache.json"
        "js/compiled/out.autouitest/spec_provider/stats.cljc.cache.json"
        "js/compiled/out.autouitest/spec_provider/provider.cljc.cache.json"
        "js/compiled/out.autouitest/cljs/tools/reader/impl/commons.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/tools/reader/impl/utils.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/tools/reader/impl/errors.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/tools/reader/impl/inspect.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/tools/reader/edn.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/tools/reader/reader_types.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/tools/reader.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/env.cljc.cache.json"
        "js/compiled/out.autouitest/cljs/core/async.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/core/async/impl/channels.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/core/async/impl/dispatch.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/core/async/impl/timers.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/core/async/impl/buffers.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/core/async/impl/protocols.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/core/async/impl/ioc_helpers.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/compiler.cljc.cache.json"
        "js/compiled/out.autouitest/cljs/analyzer.cljc.cache.json"
        "js/compiled/out.autouitest/cljs/tagged_literals.cljc.cache.json"
        "js/compiled/out.autouitest/cljs/spec/test/alpha.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/spec/gen/alpha.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/spec/alpha.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/reader.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/source_map.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/js.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/pprint.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/source_map/base64.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/source_map/base64_vlq.cljs.cache.json"
        "js/compiled/out.autouitest/cljs/core$macros.cljc.cache.json"
        "js/compiled/out.autouitest/cljs/analyzer/api.cljc.cache.json"
        "js/compiled/out.autouitest/cljs/stacktrace.cljc.cache.json"
        "js/compiled/out.autouitest/membrane/audio.cljs.cache.json"
        "js/compiled/out.autouitest/membrane/jsonee.cljc.cache.json"
        "js/compiled/out.autouitest/membrane/webgl.cljs.cache.json"
        "js/compiled/out.autouitest/membrane/basic_components.cljc.cache.json"
        "js/compiled/out.autouitest/membrane/autoui.cljc.cache.json"
        "js/compiled/out.autouitest/membrane/builder.cljc.cache.json"
        "js/compiled/out.autouitest/membrane/ui.cljc.cache.json"
        "js/compiled/out.autouitest/membrane/component.cljc.cache.json"
        "js/compiled/out.autouitest/membrane/analyze.cljc.cache.json"
        "js/compiled/out.autouitest/membrane/example/counter.cljc.cache.json"
        "js/compiled/out.autouitest/membrane/example/todo.cljc.cache.json"
        "js/compiled/out.autouitest/membrane/macroexpand.cljs.cache.json"
        "js/compiled/out.autouitest/membrane/vdom.cljs.cache.json"
        "js/compiled/out.autouitest/membrane/eval.cljs.cache.json"
        "js/compiled/out.autouitest/membrane/webgltest.cljs.cache.json"
        "js/compiled/out.autouitest/com/rpl/specter.cljc.cache.json"
        "js/compiled/out.autouitest/com/rpl/specter/protocols.cljc.cache.json"
        "js/compiled/out.autouitest/com/rpl/specter/impl.cljc.cache.json"
        "js/compiled/out.autouitest/com/rpl/specter/navs.cljc.cache.json"
        "js/compiled/out.autouitest/vdom/core.cljs.cache.json"
        "js/compiled/out.autouitest/process/env.cljs.cache.json"


        ]
       )


     (defn load-cache [state cache-path]
       (let [ch (chan 1)]
         (membrane.eval/get-file cache-path
                                 (fn [source]
                                   (try
                                     (let [
                                           rdr   (transit/reader :json)
                                           cache (transit/read rdr source)
                                           ns (:name cache)]
                                       (cljs.js/load-analysis-cache! state ns cache)
                                       (prn "loaded " cache-path))
                                     (finally
                                       (async/close! ch)))))
         ch))


     (let [state cljstate]
       (async/go

         (let []
           (let [chs (doall (map (fn [path]
                                   (load-cache state path))
                                 aot-caches))]
             (doseq [ch chs]
               (<! ch)))

           (println "loaded all cached")

           (async/go

             #_(prn (<! (membrane.eval/eval-async state '(require-macros '[membrane.component :refer [defui]])
                                                  #_(:require [membrane.audio :as audio]))))

             (let [obj blades-json
                   ge (best-gen obj)]

               (def canvas (.getElementById js/document "canvas"))
               #_(defonce start-auto-app (membrane.component/run-ui  #'gen-editor-editor {:ge ge :obj obj} nil {:container canvas}))

               #_(defonce start-auto-app (membrane.component/run-ui #'gen-editor-editor {:ge ge :obj obj} nil {:container (js/document.getElementById "app")})))


             (def background-eval-context (atom {}))
             (def background-eval-context-key (atom 0))
             (defn background-eval [form eval-context cache $cache eval-key]
               (if (contains? cache eval-key)
                 (get cache eval-key)
                 (let [context-key (swap! background-eval-context-key inc)]
                   (spec/transform (component/path->spec $cache)
                                   (fn [m]
                                     (assoc m eval-key (ui/label "loading...")))
                                   start-auto-app)
                   (async/go
                     (swap! background-eval-context assoc context-key eval-context)
                     (let [bindings (for [[sym val] eval-context]
                                      [sym `(get-in @background-eval-context [~context-key (quote ~sym)])])
                           form-with-context `(let ~(vec (apply concat bindings))
                                                ~form)

                           result (<! (membrane.eval/eval-async cljstate form-with-context))]
                       ;; (prn result)
                       (if (:error result)
                         (do
                           (prn "error " (:error result))
                           (spec/transform (component/path->spec $cache)
                                           (fn [m]
                                             (assoc m eval-key (ui/label "error evaling....")))
                                           start-auto-app))
                         (spec/transform (component/path->spec $cache)
                                         (fn [m]
                                           (assoc m eval-key (:value result)))
                                         start-auto-app))
                       

                       (swap! background-eval-context dissoc context-key)))
                   (ui/label "loading...")))))))

       (set! (.-evaltest js/window
                         )
             (fn []
               (let [source '(membrane.component/defui my-foo [{:keys [ a b]}]) ]
                (cljs/eval state source membrane.eval/default-compiler-options
                            #(prn %)))))



       (set! (.-evaltually js/window)
             (fn [source]
               (cljs/eval-str state source nil membrane.eval/default-compiler-options
                              
                              #(prn %))
               
               ))

       (set! (.-evaltually_statement js/window)
             (fn [source]
               (cljs/eval-str state source nil (assoc membrane.eval/default-compiler-options
                                                      :context :statement)
                              #(prn %)))))))



(comment
  #?(:cljs
     (do
       (defn default-load-fn [{:keys [name macros path] :as m} cb]
         (prn "trying to load" m)
         (throw "whoops!"))

       (defn wrap-js-eval [resource]
         (try
           (cljs/js-eval resource)
           (catch js/Object e
             {::error e})))

       (def compiler-options
         {:source-map true
          ;; :context :statement
          :ns 'test.ns
          :verbose true
          :load default-load-fn
          :def-emits-var true
          :eval wrap-js-eval})

       (def state (cljs/empty-state))

       (defn eval-next [statements]
         (when-let [statement (first statements)]
           (prn "evaling " statement)
           (cljs/eval state
                      statement
                      compiler-options
                      #(do (prn "result: " %)
                           (eval-next (rest statements))))))
       (eval-next [
                   '(ns test.ns)
                   '(defmacro test-macro [& body]
                      `(vector 42 ~@body))
                   '(test-macro 123)
                   ;; console log:
                   ;; "evaling " (test-macro 123)
                   ;; "result: " [42]

                   ]))))





