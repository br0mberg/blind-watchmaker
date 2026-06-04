(ns biomorph.ui.components)

(defn biomorph-cell [{:keys [id genotype fitness fx-image selected?]}]
  {:fx/type :v-box
   :alignment :center
   :spacing 5
   :padding 10
   :style (if selected?
            {:-fx-border-color "#0078D7"
             :-fx-border-width 2
             :-fx-background-color "#E5F1FB"}
            {:-fx-border-color "#CCCCCC"
             :-fx-border-width 1})
   :on-mouse-clicked {:event/type :ui/select-biomorph :payload id}
   :children [{:fx/type :image-view
               :image fx-image
               :fit-width 150
               :fit-height 150
               :preserve-ratio true}
              {:fx/type :label
               :style {:-fx-font-weight :bold}
               :text (str "Fitness: " (format "%.4f" (double fitness)))}
              {:fx/type :label
               :style {:-fx-font-size 10 :-fx-text-fill "#666666"}
               :text (str "G: " (pr-str (vec (take 8 genotype))) "...")}]})

(defn control-panel [{:keys [status generation stagnation-count]}]
  {:fx/type :v-box
   :spacing 15
   :padding 15
   :pref-width 250
   :style {:-fx-background-color "#F3F3F3"
           :-fx-border-color "#E0E0E0"
           :-fx-border-width "0 1 0 0"}
   :children [{:fx/type :label
               :text "Эволюционные параметры"
               :style {:-fx-font-size 16 :-fx-font-weight :bold}}
              {:fx/type :grid-pane
               :hgap 10
               :vgap 5
               :children [{:fx/type :label
                           :grid-pane/column 0
                           :grid-pane/row 0
                           :text "Текущее поколение:"}
                          {:fx/type :label
                           :grid-pane/column 1
                           :grid-pane/row 0
                           :style {:-fx-font-weight :bold}
                           :text (str generation)}
                          {:fx/type :label
                           :grid-pane/column 0
                           :grid-pane/row 1
                           :text "Стагнация (max 10):"}
                          {:fx/type :label
                           :grid-pane/column 1
                           :grid-pane/row 1
                           :text (str stagnation-count)}]}
              {:fx/type :separator}
              {:fx/type :button
               :max-width Double/MAX_VALUE
               :style (case status
                        :running {:-fx-base "#E81123" :-fx-text-fill "#FFFFFF"}
                        :converged {:-fx-base "#107C41" :-fx-text-fill "#FFFFFF"}
                        {:-fx-base "#0078D7" :-fx-text-fill "#FFFFFF"})
               :text (case status
                       :running "Пауза"
                       :idle "Запустить эволюцию"
                       :paused "Продолжить"
                       :converged "Эволюция завершена (Сходимость)")
               :on-action {:event/type :evolution/toggle-status}}
              {:fx/type :button
               :max-width Double/MAX_VALUE
               :text "Сбросить"
               :on-action {:event/type :evolution/reset}}]})

(defn main-view [{:keys [state]}]
  (let [{:keys [evolution/status
                evolution/generation
                evolution/stagnation-counter
                population/biomorphs
                ui/selected-biomorph-id
                target/fx-image]} state
        pref-cols 3
        target-view (if fx-image
                      {:fx/type :image-view
                       :image fx-image
                       :fit-width 150
                       :fit-height 150
                       :preserve-ratio true}
                      {:fx/type :stack-pane
                       :pref-width 150
                       :pref-height 150
                       :style {:-fx-background-color "#E0E0E0"}
                       :children [{:fx/type :label :text "Нет изображения"}]})]
    {:fx/type :stage
     :showing true
     :title "Genetic Biomorph Evolution Simulator (Dawkins Clockmaker Engine)"
     :width 1000
     :height 700
     :scene {:fx/type :scene
             :root {:fx/type :border-pane
                    :left (control-panel
                           {:status status
                            :generation generation
                            :stagnation-count stagnation-counter})
                    :center (if (seq biomorphs)
                              {:fx/type :scroll-pane
                               :fit-to-width true
                               :content
                               {:fx/type :tile-pane
                                :padding 15
                                :hgap 15
                                :vgap 15
                                :pref-columns pref-cols
                                :children (mapv (fn [b]
                                                  (biomorph-cell
                                                   {:id (:id b)
                                                    :genotype (:genotype b)
                                                    :fitness (:fitness b)
                                                    :fx-image (:fx-image b)
                                                    :selected? (= (:id b)
                                                                 selected-biomorph-id)}))
                                                biomorphs)}}
                              {:fx/type :label
                               :padding 20
                               :text "Нет особей в популяции"})
                    :right {:fx/type :v-box
                            :padding 15
                            :spacing 10
                            :alignment :center
                            :children [{:fx/type :label
                                        :text "Целевой паттерн (Target)"
                                        :style {:-fx-font-weight :bold}}
                                       target-view
                                       {:fx/type :button
                                        :text "Загрузить цель…"
                                        :max-width Double/MAX_VALUE
                                        :on-action {:event/type :target/load-image}}]}}}}))
