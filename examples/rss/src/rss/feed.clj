(ns rss.feed
  (:require
   [clojure.xml :as xml]))

(defmulti -parse :tag)
(defmethod -parse :rss [{:keys [content]}] (into {} (map -parse) content))
(defmethod -parse :default [m]
  (let [k (:tag m)]
    (when-let [v (-> m
                     :content
                     first)]
      {k v})))

(defmethod -parse :channel [{:keys [content]}]
  (apply merge-with (fn [a b] (if (vector? a) (conj a b) [a b])) (map -parse content)))

(defmethod -parse :title [{:keys [content]}] {:title (first content)})
(defmethod -parse :description [{:keys [content]}] {:description (first content)})
(defmethod -parse :link [{:keys [content]}] {:link (first content)})
(defmethod -parse :atom:link [_] {})
(defmethod -parse :item [{:keys [content]}] {:item (apply merge-with vector (map -parse content))})
(defmethod -parse :pubDate [{:keys [content]}] {:pubDate (first content)})
(defmethod -parse :guid [{:keys [content]}] {:guid (first content)})

(defn parse
  [feed-uri]
  (-parse (xml/parse feed-uri)))
