(ns fun-features
  (:require [membrane.java2d :as java2d]
            [membrane.example.todo :refer [todo-app]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [membrane.ui :as ui
             :refer [horizontal-layout
                     vertical-layout
                     button
                     label
                     spacer
                     on]]))


;; graphical elements are values
;; no need to attach elements to the dom to get layout info
(ui/bounds (vertical-layout
            (ui/label "hello")
            (ui/checkbox true)))
;; [33.181640625 28.48828125]


;; events are pure functions that return effects which are also values
(let [mpos [15 15]]
  (ui/mouse-down
   (ui/translate 10 10
                 (on :mouse-down (fn [[mx my]]
                                   ;;return a sequence of effects
                                   [[:say-hello]])
                     (ui/label "Hello")))
   mpos))
;; ([:say-hello])


;; horizontal and vertical centering!
(java2d/run #(let [rect (ui/with-style :membrane.ui/style-stroke
                          (ui/rectangle 200 200))]
               [rect
                (ui/center (ui/label "hello") (ui/bounds rect))]) )


;; save graphical elem as an image
(let [todos [{:complete? false
              :description "first"}
             {:complete? false
              :description "second"}
             {:complete? true
              :description "third"}]]
  (java2d/save-image "todoapp.png"
                     (todo-app {:todos todos :selected-filter :all})))


(s/def :todo/complete? boolean?)
(s/def :todo/description (s/and string?
                                #(< (count %) 20)))
(s/def :todo/todo (s/keys :req-un [:todo/complete?
                                   :todo/description]))
(s/def ::todos (s/and
                (s/coll-of :todo/todo
                           :into [])
                #(< (count %) 10)))

;; use spec to generate images of variations of your app
(doseq [[i todo-list] (map-indexed vector (gen/sample (s/gen ::todos)))]
  (java2d/save-image (str "todo" i ".png")
                     (ui/vertical-layout
                      (ui/label (with-out-str
                                  (clojure.pprint/pprint todo-list)))
                      (ui/with-style :membrane.ui/style-stroke
                        (ui/path [0 0] [400 0]))
                      (todo-app {:todos todo-list :selected-filter :all}))))
