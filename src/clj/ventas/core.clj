(ns ventas.core
  (:refer-clojure :exclude [reset!])
  (:require
   [clojure.core.async :as core.async :refer [>! go]]
   [clojure.tools.nrepl.server :as nrepl]
   [mount.core :as mount]
   [taoensso.timbre :as timbre]
   [ventas.config :as config]
   [ventas.database :as db]
   [ventas.database.seed :as seed]
   [ventas.email.templates.core]
   [ventas.entities.core]
   [ventas.events :as events]
   [ventas.logging]
   [ventas.plugins.core]
   [ventas.search :as search]
   [ventas.seo :as seo]
   [ventas.server :as server]
   [ventas.server.api.admin]
   [ventas.server.api.description]
   [ventas.server.api.user]
   [ventas.server.api]
   [ventas.site :as site]
   [ventas.stats :as stats]
   [ventas.themes.clothing.core])
  (:gen-class))

(defn start! []
  (mount/start #'config/config-loader
               #'db/db
               #'search/elasticsearch
               #'search/indexer
               #'search/tx-report-queue-listener
               #'seo/driver
               #'server/server
               #'site/sites
               #'stats/kafka-indexer
               #'stats/producer)
  (core.async/put! (events/pub :init) true))

(defn -main [& args]
  (start!)
  (let [auth-secret (config/get :auth-secret)]
    (when (or (empty? auth-secret) (= auth-secret "CHANGEME"))
      (throw (Exception. (str ":auth-secret is empty or has not been changed.\n"
                              "Either edit resources/config.edn or add an AUTH_SECRET environment variable, and try again.")))))
  (let [{:keys [host port]} (config/get :nrepl)]
    (timbre/info (str "Starting nREPL server on " host ":" port))
    (nrepl/start-server :port port :bind host)))

(defn reset!
  "Returns everything to its default state, removing all data"
  []
  (seed/seed :recreate? true)
  (search/reindex)
  (entities.image-size/clean-storage)
  (entities.image-size/transform-all))
