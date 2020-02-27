(ns membrane.uiserver
  (:require 
            

            [figwheel-sidecar.repl-api :as ra]
))

;; (defroutes my-app-routes
;;   ;; <other stuff>
;;   ;; (GET "/" request "hello")
;;   (GET "/ws" request websocket-handler)
;;   (GET  "/" [] (ring.util.response/resource-response "index.html" {:root "public"}))
;;   (route/resources  "/") 

;;   )



;; (def app
;;   (-> my-app-routes
;;       ;; Add necessary Ring middleware:
;;       ring.middleware.keyword-params/wrap-keyword-params
;;       ring.middleware.params/wrap-params))

;; (defonce server (atom nil))

(defn start-server [& args]
  ;; The #' is useful when you want to hot-reload code
  ;; You may want to take a look: https://github.com/clojure/tools.namespace
  ;; and http://http-kit.org/migration.html#reload
  (do
    (ra/start-figwheel! {:build-ids ["ui"]
                         :all-builds {:ui
                                      {:id "ui"
                                       :source-paths ["src"]

                                       :figwheel {:on-jsload "membrane.ui/on-js-reload"}
                                       
                                       :compiler {:main 'membrane.ui
                                                  :asset-path "js/compiled/out.ui"
                                                  :output-to "resources/public/js/compiled/membrane.ui.js"
                                                  :output-dir "resources/public/js/compiled/out.ui"
                                                  :source-map-timestamp true}}}
                         })
    nil)
  ;; (reset! nrepl-server (nrepl-server/start-server :port 7888 :handler cider-nrepl-handler))
  ;; (reset! server (httpkit/run-server #'app {:port 8080}))
  )

(defn stop-server []
  (do
    (ra/stop-figwheel!)
    nil))
