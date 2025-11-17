(ns membrane.component.present
  (:require [membrane.ui :as ui])
  (:import java.util.function.Function

           java.util.concurrent.ArrayBlockingQueue
           java.util.concurrent.ExecutorService
           java.util.concurrent.Executors))


(defonce nil-sentinel (Object.))
(defn ^:private hm-lookup
     "If the specified key is not already associated with a value (or is mapped to null), attempts to compute its value using the given mapping function and enters it into this map unless null."
     ([^java.util.Map hm key compute]
      (.computeIfAbsent hm key
                        (reify Function
                          (apply [_f _k]
                            (compute))))))

(defn ^:private memo1 [f]
  (let [cache* (ThreadLocal/withInitial
                (reify
                  java.util.function.Supplier
                  (get [_]
                    #_(java.util.HashMap.)
                    (java.util.WeakHashMap.))))]
    (fn [o]
      (let [cache ^java.util.Map (.get ^ThreadLocal cache*)]
        (hm-lookup cache o #(f o))))))

(defprotocol IOnPresent
  :extend-via-metadata true  
  (-on-present [elem]))

(defn present [elem]
  (-on-present elem))

(def xform
  (mapcat (memo1 present)))
(extend-protocol IOnPresent
  nil
  (-on-present [this] nil)

  #?(:clj Object
     :cljs default)
  (-on-present [this]
    (into [] xform (ui/children this))))

(defrecord OnPresent [on-present body]
  
  IOnPresent
  (-on-present [_]
    (when on-present
      (on-present)))

  ui/IOrigin
    (-origin [_]
        [0 0])

  ui/IBounds
  (-bounds [this]
    (ui/child-bounds body))


  ui/IMakeNode
    (make-node [this childs]
      (assert (= (count childs) 1))
      (OnPresent. on-present (first childs)))


  ui/IChildren
  (-children [this]
    [body]))

(defn on-present [on-present elem]
  (->OnPresent on-present elem))





