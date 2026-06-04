(ns biomorph.ui.components)

(defn biomorph-cell [{:keys [id genotype fitness fx-image selected?]}]
  {:fx/type :v-box
   :alignment :center
   :spacing 5
   :padding 10
   :style (if selected?
            (str "-fx-border-color: #8B1A1A;"
                 "-fx-border-width: 2;"
                 "-fx-background-color: rgba(240,237,228,0.94);"
                 "-fx-effect: dropshadow(gaussian,rgba(139,26,26,0.4),14,0.1,0,4);")
            (str "-fx-border-color: rgba(40,40,40,0.6);"
                 "-fx-border-width: 1;"
                 "-fx-background-color: rgba(245,242,234,0.90);"
                 "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.7),12,0.05,0,5);"))
   :on-mouse-clicked {:event/type :ui/select-biomorph :payload id}
   :children [{:fx/type :image-view
               :image fx-image
               :fit-width 150
               :fit-height 150
               :preserve-ratio true}
              {:fx/type :v-box
               :alignment :center
               :spacing 2
               :padding {:top 6 :bottom 6 :left 8 :right 8}
               :style "-fx-background-color: rgba(178,192,165,0.82);"
               :children [{:fx/type :label
                           :style (str "-fx-font-family: 'Courier New';"
                                       "-fx-font-weight: bold;"
                                       "-fx-font-size: 11;"
                                       "-fx-text-fill: #1A1A1A;")
                           :text (str "Fitness: " (format "%.4f" (double fitness)))}
                          {:fx/type :label
                           :style (str "-fx-font-family: 'Courier New';"
                                       "-fx-font-size: 9;"
                                       "-fx-text-fill: #666655;")
                           :text (str "G14=" (genotype 14) " · " (pr-str (vec (take 6 genotype))) "…")}]}]})

(defn control-panel [{:keys [status generation stagnation-count gene-14-as-thickness?]}]
  {:fx/type :v-box
   :spacing 15
   :padding 15
   :pref-width 250
   :style (str "-fx-background-color: rgba(6,10,6,0.82);"
               "-fx-border-color: rgba(40,60,40,0.5);"
               "-fx-border-width: 0 1 0 0;")
   :children [{:fx/type :label
               :text "Эволюционные параметры"
               :style (str "-fx-font-family: 'Courier New';"
                           "-fx-font-size: 15;"
                           "-fx-font-weight: bold;"
                           "-fx-text-fill: #CCCCCC;")}
              {:fx/type :grid-pane
               :hgap 10
               :vgap 8
               :children [{:fx/type :label
                           :grid-pane/column 0
                           :grid-pane/row 0
                           :style (str "-fx-font-family: 'Courier New';"
                                       "-fx-font-size: 12;"
                                       "-fx-text-fill: #556655;")
                           :text "Поколение:"}
                          {:fx/type :label
                           :grid-pane/column 1
                           :grid-pane/row 0
                           :style (str "-fx-font-family: 'Courier New';"
                                       "-fx-font-size: 13;"
                                       "-fx-font-weight: bold;"
                                       "-fx-text-fill: #BBCCBB;")
                           :text (str generation)}
                          {:fx/type :label
                           :grid-pane/column 0
                           :grid-pane/row 1
                           :style (str "-fx-font-family: 'Courier New';"
                                       "-fx-font-size: 12;"
                                       "-fx-text-fill: #556655;")
                           :text "Стагнация (max 10):"}
                          {:fx/type :label
                           :grid-pane/column 1
                           :grid-pane/row 1
                           :style (str "-fx-font-family: 'Courier New';"
                                       "-fx-font-size: 13;"
                                       "-fx-font-weight: bold;"
                                       "-fx-text-fill: " (if (>= stagnation-count 8) "#B03030" "#BBCCBB") ";")
                           :text (str stagnation-count)}]}
              {:fx/type :separator}
              {:fx/type :check-box
               :text "Ген 14 → толщина линии (иначе смещение dir)"
               :selected gene-14-as-thickness?
               :on-action {:event/type :ui/toggle-gene-14-mode}}
              {:fx/type :separator}
              {:fx/type :button
               :max-width Double/MAX_VALUE
               :style (case status
                        :running  (str "-fx-base: #8B1A1A; -fx-text-fill: #FFFFFF;"
                                       "-fx-font-family: 'Courier New';")
                        :converged (str "-fx-base: #1B5E37; -fx-text-fill: #FFFFFF;"
                                        "-fx-font-family: 'Courier New';")
                        (str "-fx-background-color: rgba(10,16,10,0.85);"
                             "-fx-text-fill: #8BAA8B;"
                             "-fx-border-color: rgba(80,110,80,0.6);"
                             "-fx-border-width: 1;"
                             "-fx-font-family: 'Courier New';"))
               :text (case status
                       :running   "Пауза"
                       :idle      "Запустить эволюцию"
                       :paused    "Продолжить"
                       :converged "Эволюция завершена (Сходимость)")
               :on-action {:event/type :evolution/toggle-status}}
              {:fx/type :button
               :max-width Double/MAX_VALUE
               :style (str "-fx-background-color: rgba(10,16,10,0.7);"
                           "-fx-text-fill: #556655;"
                           "-fx-border-color: rgba(50,70,50,0.5);"
                           "-fx-border-width: 1;"
                           "-fx-font-family: 'Courier New';")
               :text "Сбросить"
               :on-action {:event/type :evolution/reset}}]})

(defn main-view [{:keys [state]}]
  (let [{:keys [evolution/status
                evolution/generation
                evolution/stagnation-counter
                population/biomorphs
                ui/selected-biomorph-id
                ui/gene-14-as-thickness?
                target/fx-image]} state
        pref-cols 3
        bg-url   (str (clojure.java.io/resource "hospital_bg_crop.jpg"))
        target-view (if fx-image
                      {:fx/type :image-view
                       :image fx-image
                       :fit-width 150
                       :fit-height 150
                       :preserve-ratio true}
                      {:fx/type :stack-pane
                       :pref-width 150
                       :pref-height 150
                       :style (str "-fx-background-color: rgba(230,226,216,0.85);"
                                   "-fx-border-color: rgba(60,80,60,0.4);"
                                   "-fx-border-width: 1;")
                       :children [{:fx/type :label
                                   :style (str "-fx-font-family: 'Courier New';"
                                               "-fx-font-size: 10;"
                                               "-fx-text-fill: #667766;")
                                   :text "Нет изображения"}]})]
    {:fx/type :stage
     :showing true
     :title "Genetic Biomorph Evolution Simulator (Dawkins Clockmaker Engine)"
     :width 1000
     :height 700
     :scene {:fx/type :scene
             :stylesheets [(str (clojure.java.io/resource "rorschach.css"))]
             :root {:fx/type :border-pane
                    :style (str "-fx-background-image: url('" bg-url "');"
                                "-fx-background-size: cover;"
                                "-fx-background-position: center center;"
                                "-fx-background-repeat: no-repeat;")
                    :left (control-panel
                           {:status status
                            :generation generation
                            :stagnation-count stagnation-counter
                            :gene-14-as-thickness? gene-14-as-thickness?})
                    :center (if (seq biomorphs)
                              {:fx/type :scroll-pane
                               :fit-to-width true
                               :style (str "-fx-background-color: transparent;"
                                           "-fx-background: transparent;")
                               :content
                               {:fx/type :tile-pane
                                :padding 15
                                :hgap 15
                                :vgap 15
                                :pref-columns pref-cols
                                :style "-fx-background-color: transparent;"
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
                               :style (str "-fx-font-family: 'Courier New';"
                                           "-fx-text-fill: #445544;")
                               :text "Нет особей в популяции"})
                    :right {:fx/type :v-box
                            :padding 15
                            :spacing 10
                            :alignment :center
                            :style (str "-fx-background-color: rgba(6,10,6,0.82);"
                                        "-fx-border-color: rgba(40,60,40,0.5);"
                                        "-fx-border-width: 0 0 0 1;")
                            :children [{:fx/type :label
                                        :text "Целевой паттерн (Target)"
                                        :style (str "-fx-font-family: 'Courier New';"
                                                    "-fx-font-size: 10;"
                                                    "-fx-text-fill: #556655;")}
                                       target-view
                                       {:fx/type :button
                                        :text "Загрузить цель…"
                                        :max-width Double/MAX_VALUE
                                        :style (str "-fx-background-color: rgba(10,16,10,0.7);"
                                                    "-fx-text-fill: #556655;"
                                                    "-fx-border-color: rgba(50,70,50,0.5);"
                                                    "-fx-border-width: 1;"
                                                    "-fx-font-family: 'Courier New';")
                                        :on-action {:event/type :target/load-image}}]}}}}))
