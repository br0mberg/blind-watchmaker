(ns biomorph.core
  (:require [cljfx.api :as fx]
            [biomorph.ui.components :refer [main-view]]
            [biomorph.ui.events :as events]
            [biomorph.engine.core :as eng])
  (:import [javafx.scene.media Media MediaPlayer]
           [javafx.application Platform])
  (:gen-class))

(defonce ^:private ambient-player (atom nil))

(defn- event-payload [event]
  (or (:payload event)
      (when-let [c (:fx/event event)]
        (try
          (.getNewValue c)
          (catch Exception _ nil)))))

(defn- dispatch-event! [event]
  (let [event-type (:event/type event)
        payload (event-payload event)]
    (cond
      (= event-type :target/load-image)
      (events/load-target-image-async! dispatch-event!)

      (= event-type :ui/toggle-audio)
      (let [next-state (swap! eng/app-state-atom
                              #(update % :ui/audio-muted? not))]
        (when-let [player @ambient-player]
          (.setMute player (:ui/audio-muted? next-state))))

      (= event-type :app/quit)
      (do (when-let [player @ambient-player]
            (.stop player))
          (Platform/exit)
          (System/exit 0))

      :else
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

(defn- start-ambient! []
  (Platform/runLater
   (fn []
     (when-let [url (clojure.java.io/resource "ambient.mp3")]
       (let [player (MediaPlayer. (Media. (str url)))]
         (.setCycleCount player MediaPlayer/INDEFINITE)
         (.setVolume player 0.28)
         (.play player)
         (reset! ambient-player player))))))

(defn -main [& _]
  (eng/seed-initial-population!)
  (fx/mount-renderer eng/app-state-atom renderer)
  (start-ambient!))
