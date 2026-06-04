(ns biomorph.ui.events
  (:require [biomorph.state :as state]
            [biomorph.engine.core :as eng]
            [biomorph.engine.protocols :as p]
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
      (let [state (if (empty? (:population/biomorphs state))
                    (let [n (:population/size state)
                          target (:target/matrix state)
                          biomorphs (vec (for [id (range n)]
                                           (eng/make-biomorph
                                            id
                                            (p/generate-initial-genotype eng/engine)
                                            target)))
                          best (apply max-key :fitness biomorphs)]
                      (assoc state
                             :population/biomorphs biomorphs
                             :population/best-biomorph best))
                    state)]
        (assoc state :evolution/status :running)))))

(defmethod handle-event :evolution/reset [_ state _]
  (let [n (:population/size state)
        target (:target/matrix state)
        biomorphs (vec (for [id (range n)]
                         (eng/make-biomorph
                          id
                          (p/generate-initial-genotype eng/engine)
                          target)))
        best (apply max-key :fitness biomorphs)]
    (state/apply-population-reset state {:biomorphs biomorphs
                                         :best-biomorph best})))

(defmethod handle-event :ui/select-biomorph [_ state id]
  (assoc state :ui/selected-biomorph-id id))

(defmethod handle-event :engine/generation-computed [_ state payload]
  (state/apply-generation-computed state payload))

(defmethod handle-event :target/loaded [_ state payload]
  (let [next-state (state/apply-target-loaded state payload)
        biomorphs (:population/biomorphs next-state)]
    (if (seq biomorphs)
      (let [rescored (eng/rescore-biomorphs biomorphs (:matrix payload))
            best (apply max-key :fitness rescored)]
        (assoc next-state
               :population/biomorphs rescored
               :population/best-biomorph best))
      next-state)))

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
