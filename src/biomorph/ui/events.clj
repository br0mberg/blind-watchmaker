(ns biomorph.ui.events
  (:require [biomorph.state :as state]
            [biomorph.engine.core :as eng]
            [biomorph.engine.images :as images]
            [cljfx.api :as fx])
  (:import [javafx.stage FileChooser FileChooser$ExtensionFilter]))

(defmulti handle-event (fn [event-type _state _payload] event-type))

(defmethod handle-event :default [_ state _]
  state)

(defmethod handle-event :evolution/toggle-status [_ state _]
  (let [status (:evolution/status state)]
    (case status
      :running (assoc state :evolution/status :paused)
      (do
        (when (empty? (:population/biomorphs state))
          (eng/seed-initial-population!))
        (send-off eng/evolution-agent eng/compute-next-generation-loop)
        (assoc state :evolution/status :running)))))

(defmethod handle-event :evolution/set-metric [_ state metric-type]
  (if metric-type
    (assoc state :evolution/metric-type metric-type)
    state))

(defmethod handle-event :ui/select-biomorph [_ state id]
  (assoc state :ui/selected-biomorph-id id))

(defmethod handle-event :ui/toggle-gene-14-mode [_ state _]
  (eng/apply-gene-14-mode state (not (:ui/gene-14-as-thickness? state false))))

(defmethod handle-event :engine/generation-computed [_ state payload]
  (state/apply-generation-computed state payload))

(defmethod handle-event :target/loaded [_ state payload]
  (state/apply-target-loaded state payload))

(defn- show-file-chooser []
  (let [chooser (FileChooser.)
        _ (.setTitle chooser "Выберите целевое изображение")
        filter (FileChooser$ExtensionFilter.
                "Изображения" (into-array String ["*.png" "*.jpg" "*.jpeg" "*.gif" "*.bmp"]))]
    (.add (.getExtensionFilters chooser) filter)
    (.setSelectedExtensionFilter chooser filter)
    (.showOpenDialog chooser nil)))

(defn load-target-image-async! [dispatch!]
  (future
    (fx/run-later
      (when-let [file (show-file-chooser)]
        (try
          (let [loaded (images/load-target-image! file)]
            (dispatch! {:event/type :target/loaded :payload loaded}))
          (catch Exception e
            (println "Ошибка загрузки изображения:" (.getMessage e))))))))
