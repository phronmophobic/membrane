(ns rss
  (:require
   [rss.feed :as rss]
   [membrane.java2d :as backend]
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
    :refer [defui defeffect]]
   [membrane.basic-components :as basic]))

;; make it easier to inspect current state
;; for debugging
(def state (atom nil))
(defn rui [v s]
  (reset! state s)
  (backend/run (component/make-app v state)))

(defui feed-item [{:keys [title uri]}]
  (basic/button
   {:text title
    :on-click (fn [] [[::display-feed uri]])}))

(comment
  (rui #'feed-item {:title "Foo"}))

(def title-font
  (-> :sans-serif
      backend/logical-font->font-family
      (ui/font 14)
      (assoc :weight :bold)))

(defui feed-view [{items :item name :title}]
  (vertical-layout
   (label name)
   (basic/scrollview
    {:scroll-bounds [800 800]
     :body
     (apply
      vertical-layout
      (for [{:keys [title description]} items]
        (vertical-layout
         (label title title-font)
         (label description))))})))

(defui op-view [{:keys [feed-uri feeds current-feed]}]
  (on
   ::display-feed (fn [f] [[:set $current-feed f]])
   (horizontal-layout
    (vertical-layout
     (vertical-layout
      (ui/wrap-on
       :key-press
       (fn [default-handler s]
         (let [effects (default-handler s)]
           (if (and (seq effects)
                    (= s :enter))
             [[::add-feed $feeds feed-uri]
              [:set $feed-uri ""]]
             effects)))
       (basic/textarea {:text feed-uri}))
      (basic/button {:text "Add Feed"
                     :on-click (fn []
                                 [[::add-feed $feeds feed-uri]
                                  [:set $feed-uri ""]])}))
     (apply vertical-layout (for [feed feeds] (feed-item feed))))
    (when-let [feed (->> feeds
                         (filter (fn [{:keys [uri]}] (= current-feed uri)))
                         first)]

      (feed-view feed)))))

(comment
  (rui #'op-view {:feeds []
                  :current-feed ""
                  :feed-uri "https://www.cognitect.com/feed.xml"})
  (swap! state assoc :feed-uri "https://hnrss.org/frontpage" )
  ,
  )

(defeffect ::add-feed [$feeds feed-uri]
  (future
    (dispatch!
     :update
     $feeds
     #(conj % (assoc (rss/parse feed-uri) :uri feed-uri)))))
