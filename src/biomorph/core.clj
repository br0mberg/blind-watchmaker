(ns biomorph.core
  (:require [cljfx.api :as fx]
            [biomorph.ui.components :refer [main-view]]
            [biomorph.ui.events :as events]
            [biomorph.engine.core :as eng])
  (:gen-class))

(defn- event-payload [event]
  (or (:payload event)
      (when-let [c (:fx/event event)]
        (try
          (.getNewValue c)
          (catch Exception _ nil)))))

(defn- dispatch-event! [event]
  (let [event-type (:event/type event)
        payload (event-payload event)]
    (if (= event-type :target/load-image)
      (events/load-target-image-async! dispatch-event!)
      (let [next-state (swap! eng/app-state-atom
                              #(events/handle-event event-type % payload))]
        (when (and (= event-type :evolution/toggle-status)
                   (= :running (:evolution/status next-state)))
          (send-off eng/evolution-agent eng/compute-next-generation-loop))))))

(def renderer
  (fx/create-renderer
   :middleware (fx/wrap-map-desc
                (fn [state]
                  {:fx/type main-view :state state}))
   :opts {:fx.opt/map-event-handler dispatch-event!}))

(defn -main [& _]
  (eng/seed-initial-population!)
  (fx/mount-renderer eng/app-state-atom renderer))
