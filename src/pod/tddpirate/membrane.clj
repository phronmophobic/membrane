(ns pod.tddpirate.membrane
  {:no-doc true}
  (:refer-clojure :exclude [read read-string])
  (:require [bencode.core :as bencode]
            [membrane.java2d]
            [membrane.ui]
            [membrane.component]
            [membrane.basic-components]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.io PushbackInputStream])
  (:gen-class))

(set! *warn-on-reflection* true)

(def debug? false)
(defn debug [& args]
  (when debug?
    (binding [*out* (io/writer "/tmp/debug.log" :append true)]
      (apply println args))))

(def stdin (PushbackInputStream. System/in))

(defn write [v]
  (bencode/write-bencode System/out v)
  (.flush System/out))

(defn read-string [^"[B" v]
  (String. v))

(defn read []
  (bencode/read-bencode stdin))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create a lookup table from a namespace
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ns+symbol->2tuple
  "Transform a ns name and the name of a symbol inside it
  into a 2-tuple for insertion into a map."
  [podprefix ns symb] ;; podprefix is typically "pod.tddpirate."
  {:pre [(string? podprefix)
         (= (type ns) clojure.lang.Namespace)
         (symbol? symb)]}
  (let [fullpodns (str podprefix (str ns))]
    ;;(println "!!! DEBUG\npodprefix =" podprefix "\nns =" ns "\nsymb =" symb "\nfullpodns =" fullpodns "\n!!! DEBUG (end)")
    ;;(println "!!!------------")
    ;;(println "!!! symbol 1" (symbol fullpodns (str symb)))
    ;;(println "!!!------------")
    ;;(println "!!! symbol 2" (ns-resolve ns symb))
    ;;(println "!!!------------")
    [(symbol fullpodns (str symb))
     (ns-resolve ns symb)]))

(defn nsmap->lookup
  "Transform the output of ns-map into a map which transforms
  pod.tddpirate.* variables into membrane variables.
  The argument is ns symbol, however."
  [nssym]
  {:pre [(symbol? nssym)]}
  (let [ns (find-ns nssym)]
    (->> (map #(ns+symbol->2tuple "pod.tddpirate." ns %) (-> ns ns-map keys))
         (into {}))))


(def lookup
  "The caller needs to apply var-get to the result of (lookup 'namespace/name)"
  (merge
   (nsmap->lookup 'membrane.java2d)
   (nsmap->lookup 'membrane.ui)
   (nsmap->lookup 'membrane.component)
   (nsmap->lookup 'membrane.basic-components)))
;; obsolete version
;; {'pod.borkdude.clj-kondo/merge-configs clj-kondo/merge-configs
;;  'clj-kondo.core/merge-configs clj-kondo/merge-configs
;;  'pod.borkdude.clj-kondo/run! clj-kondo/run!
;;  'clj-kondo.core/run! clj-kondo/run!})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create a description of a namespace
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn nsmap->maps
  "Transform the output of ns-map into a vector whose
  entries are maps from string \"name\" into symbol names."
  [nsmapoutput]
  (mapv #(as-> (% 0) val (name val) {"name" val}) nsmapoutput))

(defn describe-ns
  "Given a namespace, create a namespace description for the
  describe operation."
  [ns]
  {:pre [(= (type ns) clojure.lang.Namespace)]}
  {"name" (-> ns ns-name name)
   "vars" (-> ns
              ns-map
              nsmap->maps)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pod-ns---example-of-CODE-usge
  "!!! TODO Not actually used, retained to serve as an example."
  [name]
  {"name" name
   "vars" [{"name" "merge-configs"}
           {"name" "print!"
            "code" "
(defn print! [run-output]
  (print (print* run-output))
  (flush))"}
           {"name" "run!"}]})

(defn run-pod []
  (loop []
    (let [message (try (read)
                       (catch java.io.EOFException _
                         ::EOF))]
      (when-not (identical? ::EOF message)
        (let [op (get message "op")
              op (read-string op)
              op (keyword op)
              id (some-> (get message "id")
                         read-string)
              id (or id "unknown")]
          (case op
            :describe (do (write {"format" "edn"
                                  "namespaces" [(describe-ns (find-ns 'membrane.java2d))
                                                (describe-ns (find-ns 'membrane.ui))
                                                (describe-ns (find-ns 'membrane.component))
                                                (describe-ns (find-ns 'membrane.basic-components))
                                                ]
                                  "id" id})
                          (recur))
            :invoke (do (try
                          (let [var (-> (get message "var")
                                        read-string
                                        symbol)
                                args (get message "args")
                                args (read-string args)
                                args (edn/read-string args)]
                            (if-let [f (var-get (lookup var))]
                              (let [value (pr-str (apply f args))
                                    reply {"value" value
                                           "id" id
                                           "status" ["done"]}]
                                (write reply))
                              (throw (ex-info (str "Var not found: " var) {}))))
                          (catch Throwable e
                            (binding [*out* *err*]
                              (println e))
                            (let [reply {"ex-message" (.getMessage e)
                                         "ex-data" (pr-str
                                                    (assoc (ex-data e)
                                                           :type (class e)))
                                         "id" id
                                         "status" ["done" "error"]}]
                              (write reply))))
                        (recur))
            (do
              (write {"err" (str "unknown op:" (name op))})
              (recur))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  "Entry point for working as a Babashka pod."
  [ & args ]
  (if (= "true" (System/getenv "BABASHKA_POD"))
    (run-pod)
    (do
      (println "*** NOT OPERATING AS A BABASHKA POD - ABORTING ***")
      (System/exit 1))))
