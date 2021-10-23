(ns frontend.core
  (:require [rum.core :as rum]
            [frontend.handler :as handler]
            [frontend.handler.plugin :as plugin-handler]
            [frontend.handler.route :as route]
            [frontend.page :as page]
            [frontend.routes :as routes]
            [frontend.spec]
            [frontend.log]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [logseq.api]))

(defn set-router!
  []
  (rfe/start!
   (rf/router routes/routes nil)
   route/set-route-match!
   ;; set to false to enable HistoryAPI
   {:use-fragment true}))



(defn start []
  (when-let [node (.getElementById js/document "root")]
    (set-router!)
    (rum/mount (page/current-page) node)))

(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds

  (plugin-handler/setup!
   #(handler/start! start))

  ;; popup to notify user, could be toggled in settings
  ;; (handler/request-notifications-if-not-asked)

  ;; (handler/run-notify-worker!)
  )

(defn stop []
  ;; stop is called before any code is reloaded
  ;; this is controlled by :before-load in the config
  (handler/stop!)
  (js/console.log "stop"))
