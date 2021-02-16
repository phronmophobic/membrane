(ns membrane.fulcro
  (:require  [com.fulcrologic.fulcro.application :as app]
             [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
             [com.fulcrologic.fulcro.mutations :refer [defmutation] :as mut]
             [com.fulcrologic.fulcro.algorithms.lookup :as ah]
             [com.fulcrologic.fulcro.algorithms.indexing :as indexing]
             [com.fulcrologic.fulcro.dom :as dom]
             [com.fulcrologic.fulcro.inspect.inspect-client :as inspect]
             [com.fulcrologic.fulcro.algorithms.merge :as merge]
             [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
             [com.fulcrologic.fulcro.algorithms.tx-processing.synchronous-tx-processing :as stx]
             [membrane.ui :as ui]
             [membrane.basic-components :as basic]
             membrane.component
             [clojure.zip :as z]
             [membrane.skia :as skia])
  (:gen-class))

(defn uuid [] (.toString (java.util.UUID/randomUUID)))
(defn log [& msgs]
  (spit "lanterna.log" (str (clojure.string/join " " msgs) "\n") :append true))


(defn membrane-optimized-render!
  [app {:keys [force-root? hydrate?] :as options}]
  (binding [comp/*blindly-render* force-root?]
    (let [{:com.fulcrologic.fulcro.application/keys [runtime-atom state-atom]} app
          {:com.fulcrologic.fulcro.application/keys [root-factory root-class mount-node]} @runtime-atom
          r!               (if hydrate?
                             (or (ah/app-algorithm app :hydrate-root!) )
                             (or (ah/app-algorithm app :render-root!)))
          state-map        @state-atom
          query            (comp/get-query root-class state-map)
          data-tree        (if query
                             (fdn/db->tree query state-map state-map)
                             state-map)
          app-root (r! (root-factory data-tree) mount-node)]
      (swap! runtime-atom assoc :com.fulcrologic.fulcro.application/app-root app-root)
      app-root)))


(defn component->view [component]
  (let [opts (comp/component-options component)
        render (:render opts)]
    (render component)))

(defmutation request-focus
  "docstring"
  [{:keys [focus-id]}]
  (action [{:keys [state] :as env}]
          (swap! state assoc :focus focus-id)))

(defn dispatch! [app tx]
  (when (seq tx)
    ;; (prn "dispatch! "tx)
    ;; (log "dispatch" tx)
    (comp/transact! app tx))
  nil)

(defn fulcro-view [dispatch! root]
  (membrane.ui/on-scroll
   (fn [offset mpos]
     (let [steps (membrane.ui/scroll root offset mpos)]
       (dispatch! steps)))
   (membrane.ui/on-mouse-move-global
    (fn [pos]
      (let [steps (membrane.ui/mouse-move-global root pos)]
        (dispatch! steps)))
    (membrane.ui/on-mouse-move
     (fn [pos]
       (let [steps (membrane.ui/mouse-move root pos)]
         (dispatch! steps)))
     (membrane.ui/on-mouse-event
      (fn [pos button mouse-down? mods]
        (let [steps (membrane.ui/mouse-event root pos button mouse-down? mods)]
          (if (seq steps)
            (dispatch! steps)
            (when mouse-down?
              (dispatch! [(request-focus nil)])))))
      (membrane.ui/on-key-press
       (fn [s]
         (let [steps (membrane.ui/key-press root s)]
           (dispatch! steps)
           steps))
       (membrane.ui/on-key-event
        (fn [key scancode action mods]
          (let [steps (membrane.ui/key-event root key scancode action mods)]
            (dispatch! steps)
            steps))
        (membrane.ui/on-clipboard-cut
         (fn []
           (let [steps (membrane.ui/clipboard-cut root)]
             (dispatch! steps)
             steps))
         (membrane.ui/on-clipboard-copy
          (fn []
            (let [steps (membrane.ui/clipboard-copy root)]
              (dispatch! steps)
              steps))
          (membrane.ui/on-clipboard-paste
           (fn [s]
             (let [steps (membrane.ui/clipboard-paste root s)]
               (dispatch! steps)
               steps))
           root))))))))))


(defn make-root [Child]
  (clojure.core/let
      [child-factory (comp/factory Child)
       options__26421__auto__
       {
        :query
        (fn
          query*
          [this]
          [{::child (comp/get-query Child)}]),
        :render
        (fn
          render-Root
          [this]
          (com.fulcrologic.fulcro.components/wrapped-render
           this
           (clojure.core/fn
             []
             (clojure.core/let
                 [{::keys [child]}
                  (com.fulcrologic.fulcro.components/props this)]
               (component->view
                (child-factory child))))))}]
    (com.fulcrologic.fulcro.components/configure-component!
     "Root2"
     ::Root2
     options__26421__auto__)))

(defmutation set-child
  "docstring"
  [{:keys [child-ident]}]
  (action [{:keys [state] :as env}]
          (swap! state assoc ::child child-ident)
          nil))

(defn mount!
  ([root]
   (mount! root (atom nil)))
  ([root view-atom]
   (let [render-root! (fn [root _]
                        (reset! view-atom
                                (fulcro-view
                                 (partial dispatch! root)
                                 (component->view root))))
         app (stx/with-synchronous-transactions
               (app/fulcro-app
                {:optimized-render! membrane-optimized-render!
                 :render-root! render-root!}))
         root (make-root root)
         root-factory (comp/factory root)]
     (do
       (app/initialize-state! app root)

       (swap! (::app/runtime-atom app) assoc
              ;; ::mount-node dom-node
              ::app/root-factory root-factory
              ::app/root-class root)
       ;; (merge/merge-component! app root initial-state)
       ;; (let [child-ident (comp/ident root initial-state)]
       ;;   (comp/transact! app [(list `set-child {:child-ident child-ident})]))

       ;; (app/update-shared! app)
       ;; not doing anything right now
       ;; (indexing/index-root! app)

       ;; (app/render! app {:force-root? true})

       ;; (skia/run #(deref view-atom) {:draw nil})
       {:app app
        :view-atom view-atom}))))

(defn show!
  "Pop up a window of the component with the state"
  ([root initial-state]
   (let [{:keys [app view-atom]} (mount! root)]
     (merge/merge-component! app root initial-state)
     (let [child-ident (comp/ident root initial-state)]
       (comp/transact! app [(set-child {:child-ident child-ident})])

       (app/render! app {:force-root? true})

       (skia/run #(deref view-atom))
       app))))

(defn show-sync!
  ([root initial-state]
   (let [{:keys [app view-atom]} (mount! root)]
     (merge/merge-component! app root initial-state)
     (let [child-ident (comp/ident root initial-state)]
       (comp/transact! app [(set-child {:child-ident child-ident})])

       (app/render! app {:force-root? true})

       (skia/run-sync #(deref view-atom))
       app))))



;; (defn wrap-mouse-move-global [handler body]
;;   (ui/on-mouse-move-global
;;    (fn [pos]
;;      (handler (fn [pos] (ui/mouse-move-global body pos))
;;               pos))
;;    body))

(defn wrap-membrane-intents [body]
  (let [wrapper (fn [handler & args]
                  (let [intents (seq (apply handler args))]
                    (when intents
                      (map
                       (fn [intent]
                         (if (vector? intent)
                           (list `membrane-dispatch! {:intent intent})
                           intent))
                       intents))))]
    (ui/wrap-on :mouse-down wrapper
                :key-press wrapper
                :mouse-up wrapper
                :mouse-event wrapper
                :mouse-move wrapper
                :mouse-move-global wrapper
                body)))



(defmutation membrane-dispatch!
  "docstring"
  [{:keys [intent]}]
  (action [{:keys [state] :as env}]
          (let [dispatch! (membrane.component/default-handler state)]
            (try
              (apply dispatch! intent)
              (catch Exception e
                (prn "could not process " intent))))
          nil))



(defn fulcroize-membrane-component* [c prefix cname]
  (let [v (resolve c)

        fn-meta (meta v)

        arglists (:arglists fn-meta)
        first-arglist (first arglists)
        arg-map (first first-arglist)

        args (disj (set (:keys arg-map))
                   'context)
        defaults (:or arg-map)
        defaults (into {} (map (fn [[k v]]
                                 [(keyword prefix (name k))
                                  v])
                               defaults))

        focusable? (contains? args 'focus?)
        args (disj args 'focus?)

        props {(keyword prefix "keys") (conj (vec args) 'id)}
        props (if focusable?
                (assoc props :keys '[focus])
                props)

        ident (keyword prefix "id")
        initial-state (assoc defaults
                             ident `(uuid))


        query (mapv (fn [arg]
                     (keyword prefix (name arg)))
                   args)
        query (if focusable?
                (conj query [:focus '(quote _)])
                query)
        query (conj query (keyword prefix "id"))

        call-args (for [arg args]
                    [(keyword arg) arg])
        $call-args (for [arg args]
                     [(keyword (str "$" (name arg)))
                      [ident `(list '~'keypath ~'id) (keyword prefix (name arg))]])

        all-call-args (concat call-args
                              $call-args)
        all-call-args (if focusable?
                        (conj all-call-args
                              [:focus? (list '= 'focus [ident 'id])])
                        all-call-args)

        body `(~c ~(into {} all-call-args))
        body (if focusable?
               `(ui/on ::basic/request-focus
                       (fn []
                         [(request-focus {:focus-id ~[ident 'id]})])
                       :membrane.lanterna/request-focus
                       (fn []
                         [(request-focus {:focus-id ~[ident 'id]})])
                       ~body)
               body)

        factory-name (symbol (str "ui-" (clojure.string/lower-case (name cname))
                                  "*"))

        ]
    `(do
       (defsc ~cname [ ~'this ~props]
         {:initial-state
          ;; defsc requires an initial state function
          ;; to be a list (and not a seq)
          ~(list 'fn '[params]
                 `(merge ~initial-state ~'params))
          :ident ~ident
          :query ~query
          }
         (wrap-membrane-intents ~body))
       (def ~factory-name
          (comp/factory ~cname))
       (defn ~(symbol (str "ui-" (clojure.string/lower-case (name cname))))
         ~'[props]
         (component->view (~factory-name ~'props))))))

(defmacro fulcroize-membrane-component [c prefix cname]
  (fulcroize-membrane-component* c prefix cname))





