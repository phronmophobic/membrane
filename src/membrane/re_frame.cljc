(ns membrane.re-frame
  (:require [re-frame.interop :refer [ratom]]
            [membrane.basic-components :as basic]
            [membrane.component :as component]
            [membrane.ui :as ui]
            [re-frame.core :as rf :refer [reg-event-db reg-event-fx inject-cofx path after reg-sub subscribe]]))

(def dispatch-sync
  #(when %
     (rf/dispatch-sync %)))

(defn re-frame-app [root]
  (membrane.ui/on-scroll
   (fn [offset mpos]
     (let [steps (membrane.ui/scroll root offset mpos)]
       (run! dispatch-sync steps)))
   (membrane.ui/on-mouse-move-global
    (fn [pos]
      (let [steps (membrane.ui/mouse-move-global root pos)]
        (run! dispatch-sync steps)))
    (membrane.ui/on-mouse-move
     (fn [pos]
       (let [steps (membrane.ui/mouse-move root pos)]
         (run! dispatch-sync steps)))
     (membrane.ui/on-mouse-event
      (fn [pos button mouse-down? mods]
        (let [steps (membrane.ui/mouse-event root pos button mouse-down? mods)]
          (if (seq steps)
            (run! dispatch-sync steps)
            #_(when mouse-down?
                (handle-step [:set [:context :focus] nil] emit!)))))
      (membrane.ui/on-key-press
       (fn [s]
         (let [steps (membrane.ui/key-press root s)]
           (run! dispatch-sync steps)
           steps)
         )
       (membrane.ui/on-key-event
        (fn [key scancode action mods]
          (let [steps (membrane.ui/key-event root key scancode action mods)]
            (run! dispatch-sync steps)
            steps)
          )
        (membrane.ui/on-clipboard-cut
         (fn []
           (let [steps (membrane.ui/clipboard-cut root)]
             (run! dispatch-sync steps)
             steps))
         (membrane.ui/on-clipboard-copy
          (fn []
            (let [steps (membrane.ui/clipboard-copy root)]
              (run! dispatch-sync steps)
              steps))
          (membrane.ui/on-clipboard-paste
           (fn [s]
             (let [steps (membrane.ui/clipboard-paste root s)]
               (run! dispatch-sync steps)
               steps))
           root))))))))))

(defonce re-frame-state (atom {}))
(def re-frame-dispatch (component/default-handler re-frame-state))

(defn set-scroll-offset! [sid [sx sy :as offset]]
  (swap! re-frame-state assoc-in [sid :offset] offset))

(defn scroll-to-top! [sid]
  (set-scroll-offset! sid [0 0]))

(defmacro defrf [ui-name component [id-sym m-args :as params]]
  (let [
        arglist (-> (resolve component)
                    meta
                    :arglists
                    first)
        m (first arglist)
        defaults (:or m)

        get-sid-sym (gensym "getsid-")
        args (into {}
                   cat
                   [(for [[sym v] defaults]
                      [(keyword sym) v])
                    (for [sym (:keys m)
                          :when (-> sym meta ::component/contextual)]
                      [(keyword sym)
                       `(get @re-frame-state
                             ~(keyword sym)
                             ~(when (contains? defaults sym)
                                (get defaults sym)))])
                    (for [sym (:keys m)
                          :let [$k (keyword (str "$" (name sym)))
                                $v (if (-> sym meta ::component/contextual)
                                     [(keyword sym)]
                                     [get-sid-sym (keyword sym)])
                                $v (if (contains? defaults sym)
                                     (conj $v `(quote ~(list 'nil->val (get defaults sym)) ))
                                     $v)]]
                      [$k $v])])
        
        m-sym (get m-args :as (gensym "m-"))
        params [id-sym (assoc m-args :as m-sym)]]
    `(defn ~ui-name ~params
       (let [~get-sid-sym ~(list 'list (quote 'get) id-sym)]
         (ui/on-bubble
          (fn [effects#]
            ~@(for [k (:keys m-args)]
                `(re-frame-dispatch :set ~(get args (keyword (str "$" (name k)))) ~k))
            (run! #(apply re-frame-dispatch %) effects#)
            (remove nil?
                    [~@(for [k (:keys m-args)]
                         `(let [new-val# (re-frame-dispatch :get ~(get args (keyword (str "$" (name k)))))]
                            (when (not= new-val# ~k)
                              [:change ~(keyword k) new-val#])))]))
          (~component (merge ~args (get @re-frame-state ~id-sym) ~m-sym)))))))

(defrf text-box basic/textarea [tid {:keys [text font]}])
(defrf scrollview basic/scrollview [sid {:keys [scroll-bounds]}])

(defn fix-scroll [elem]
  (ui/on-scroll (fn [[sx sy] pos]
                  (ui/scroll elem [(- sx) (- sy)] pos))
                elem))

(comment
  (def lorem-ipsum (clojure.string/join
                    "\n"
                    (repeatedly 800
                                (fn []
                                  (clojure.string/join
                                   (repeatedly (rand-int 50)
                                               #(rand-nth "abcdefghijklmnopqrstuvwxyz ")))))))
  (defn test-scrollview []
    [(ui/translate 10 10
                   (fix-scroll
                    (scrollview :test-scroll {:scroll-bounds [300 300]
                                              :body (ui/label lorem-ipsum)})))])

  (require '[membrane.skia :as skia])
  (skia/run #(re-frame-app (test-scrollview)))
  ,

  )
