(ns membrane.example.file-selector
    (:require [membrane.skia :as skia]
              [membrane.ui :as ui
               :refer [vertical-layout
                       translate
                       horizontal-layout
                       button
                       label
                       with-color
                       bounds
                       spacer
                       on]]
              [membrane.component :as component
               :refer [defui run-ui run-ui-sync defeffect]]
              [membrane.basic-components :as basic]))

(defui item-row [ & {:keys [item-name selected?]}]
  (on
   :mouse-down
   (fn [_]
     [[:update $selected? not]])
   ;; put the items side by side
   (horizontal-layout
    (translate 5 5
               ;; checkbox in `membrane.ui` is non interactive.
               (ui/checkbox selected?))
    (spacer 5 0)
    (ui/label item-name))))

(comment
 ;; It's a very common workflow to work on sub components one piece at a time.
  (component/run-ui #'item-row {:item-name "my item" :selected? false}))

(defui item-selector
  "`item-names` a vector of choices to select from
`selected` a set of selected items
`str-filter` filter out item names that don't contain a case insensitive match for `str-filter` as a substring
"
  [& {:keys [item-names selected str-filter]
      :or {str-filter ""
           selected #{}}}]
  (let [filtered-items (filter #(clojure.string/includes? (clojure.string/lower-case %) str-filter) item-names)]
    (apply
     vertical-layout
     (basic/textarea :text str-filter)
     (for [iname filtered-items]
       ;; override the default behaviour of updating the `selected?` value directly
       ;; instead, we'll keep the list of selected items in a set
       (on :update
           (fn [& args]
             [[:update $selected (fn [selected]
                                   (if (contains? selected iname)
                                     (disj selected iname)
                                     (conj selected iname)))]])
           (item-row :item-name iname :selected? (get selected iname)))))))

(comment
  (run-ui #'item-selector {:item-names
                           (->> (clojure.java.io/file ".")
                                            (.listFiles)
                                            (map #(.getName %)))}))

(defn file-selector [path]
  (:selected
   @(component/run-ui-sync #'item-selector {:item-names (->> (clojure.java.io/file path)
                                                           (.listFiles)
                                                           (map #(.getName %))
                                                           sort)})))

(defn -main
  ([]
   (-main "."))
  ([path]
   (doseq [fname (file-selector path)]
     (println fname))))
