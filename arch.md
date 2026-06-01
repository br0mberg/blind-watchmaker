Для реализации симулятора эволюции биоморф Ричарда Докинза на языке Clojure с использованием библиотеки `cljfx` оптимальным выбором является архитектура одномерного потока данных (**Unidirectional Data Flow, UDF**). Учитывая вычислительную сложность генерации фенотипов и расчета метрик близости матриц (150x150 пикселей), архитектура должна жестко разделять детерминированный рендеринг интерфейса и асинхронный конвейер генетического движка.

Ниже представлена детальная декомпозиция и проектирование системы с акцентом на идиоматичные подходы Clojure, эффективное управление состоянием и детальную проработку UI-слоя.

---

## 1. Архитектурная декомпозиция системы

Система разделяется на три изолированных слоя:

1. **Core Engine & Services (Вычислительный слой):** Чистые функции и протоколы для мутации генотипов, генерации геометрии фенотипа, растеризации в матрицы и расчета математических дистанций. Полностью изолирован от JavaFX.
2. **Application State & Dispatcher (Слой управления состоянием):** Единый источник истины (Single Source of Truth) на базе `clojure.core/atom`. Изменение состояния происходит через чистые функции-редукторы (мультиметоды), вызываемые диспетчером.
3. **UI Layer (cljfx) (Реактивный интерфейс):** Декларативное описание интерфейса в виде чистых функций, принимающих состояние (или контекст) и возвращающих структуры данных (дерево компонентов JavaFX).

```
[ UI Layer (cljfx) ] ---> (Dispatch Event) ---> [ Dispatcher / Reducers ]
        ^                                                    |
        | (Reactive Update via Atom)                         v
[ Atom State ] <---------------------------------------- [ Swap! ]
        ^
        | (Asynchronous callback via cljfx.api/run-later)
[ Engine Execution Thread / Agents ] <--- (Offload Heavy Computations)

```

---

## 2. Контракты вычислительного ядра и сервисов (Protocols)

Поскольку Clojure поощряет абстрагирование через интерфейсы, определим полиморфные контракты для эволюционного движка и метрик подобия.

```clojure
(ns biomorph.engine.protocols)

(defprotocol BiomorphEngine
  (generate-initial-genotype [this]
    "Генерирует случайный вектор генотипа: 15 генов [-9, +9], 16-й ген [2, 12].")
  (mutate-genotype [this genotype]
    "Применяет точечную мутацию: случайный выбор одного гена и изменение его значения на +/- 1 в допустимых границах.")
  (draw-phenotype [this genotype]
    "Вычисляет геометрию биоморфы (вектор отрезков) на основе генотипа."))

(defprotocol FitnessEvaluator
  (rasterize [this geometry width height]
    "Преобразует геометрическую структуру отрезков в матрицу пикселей (двумерный массив байт/интов).")
  (evaluate-similarity [this candidate-matrix target-matrix metric-type]
    "Вычисляет коэффициент подобия между матрицей фенотипа и целевым изображением.
     metric-type: :euclidean | :manhattan | :normalized-cross-correlation"))

```

---

## 3. Модель состояния приложения (Domain State)

Состояние описывается иммутабельным map-литералом. Для предотвращения деградации производительности UI, тяжелые бинарные данные (матрица целевого изображения) хранятся в эффективных структурах (например, примитивных массивах Java или `core.matrix`), а UI оперирует метаданными и готовыми объектами `javafx.scene.image.Image`, подготовленными в фоновом потоке.

```clojure
(def initial-state
  {:evolution/status             :idle      ; :idle, :running, :paused, :converged
   :evolution/generation         0
   :evolution/stagnation-counter 0          ; Счетчик поколений без улучшения (критерий останова = 10)
   :evolution/metric-type        :euclidean ; Текущая метрика расстояния
   
   :target/image-path            nil
   :target/matrix                nil        ; Двумерный массив для математических расчетов
   :target/fx-image              nil        ; Для отображения в UI
   
   :population/size              9          ; N биоморф в поколении
   :population/biomorphs         []         ; Вектор карт: [{:id 0, :genotype [...], :fitness 0.85, :fx-image img}]
   :population/best-biomorph     nil        ; Ссылка на лучшую особь текущего поколения
   
   :ui/selected-biomorph-id      nil})

```

---

## 4. Проектирование UI-слоя на базе Cljfx

В `cljfx` интерфейс описывается как функция от состояния. Используем механизм `cljfx.api/sub` (аналог подписок в re-frame) для точечной реактивности, чтобы изменение счетчика поколений не приводило к полной перерисовке тяжелых Canvas/ImageView элементов.

### 4.1. Диспетчер событий (Dispatcher)

Реализуем идиоматичный диспетчер через `defmulti` по типу события. Все операции, требующие времени (эволюционный шаг, расчет матриц), выносятся из потока UI (JavaFX Application Thread) через `clojure.core/future` или пулы потоков.

```clojure
(ns biomorph.ui.events
  (:require [cljfx.api :as fx]
            [biomorph.engine.core :as eng]))

(defmulti handle-event (fn [event-type _state _payload] event-type))

(defmethod handle-event :evolution/toggle-status [_ state _]
  (let [current-status (:evolution/status state)]
    (if (= current-status :running)
      (assoc state :evolution/status :paused)
      (do 
        ;; Запуск асинхронного цикла эволюции, если он был остановлен
        (send-off eng/evolution-agent eng/compute-next-generation-loop)
        (assoc state :evolution/status :running)))))

(defmethod handle-event :ui/select-biomorph [_ state id]
  (assoc state :ui/selected-biomorph-id id))

(defmethod handle-event :engine/generation-computed [_ state next-generation-data]
  (-> state
      (assoc :population/biomorphs (:biomorphs next-generation-data))
      (assoc :evolution/generation (:generation next-generation-data))
      (assoc :evolution/stagnation-counter (:stagnation next-generation-data))
      (cond-> (:converged? next-generation-data) (assoc :evolution/status :converged))))

```

### 4.2. Компоненты UI (Cljfx Views)

#### Рендеринг отдельной особи (Biomorph Cell Component)

Фенотипы передаются в UI уже в виде готовых `javafx.scene.image.Image` размерностью 150x150 пикселей. Это гарантирует максимальную производительность (60 FPS при отрисовке сетки).

```clojure
(ns biomorph.ui.components
  (:require [cljfx.api :as fx]))

(defn biomorph-cell [{:keys [id genotype fitness fx-image selected?]}]
  {:fx/type :v-box
   :alignment :center
   :spacing 5
   :padding 10
   :style (if selected? 
            {:-fx-border-color "#0078D7" :-fx-border-width 2 :-fx-background-color "#E5F1FB"}
            {:-fx-border-color "#CCCCCC" :-fx-border-width 1})
   :on-mouse-clicked {:event/type :ui/select-biomorph :payload id}
   :children [;; Отображение фенотипа
              {:fx/type :image-view
               :image fx-image
               :fit-width 150
               :fit-height 150
               :preserve-ratio true}
              ;; Информационная панель особи
              {:fx/type :label
               :style {:-fx-font-weight :bold}
               :text (str "Fitness: " (format "%.4f" (double fitness)))}
              {:fx/type :label
               :style {:-fx-font-size 10 :-fx-text-fill "#666666"}
               :text (str "G: " (pr-str (vec (take 8 genotype))) "...")}]}) ; Показываем часть генома для компактности

```

#### Панель управления параметрами (Control Panel Component)

```clojure
(defn control-panel [{:keys [status generation metric-type stagnation-count]}]
  {:fx/type :v-box
   :spacing 15
   :padding 15
   :pref-width 250
   :style {:-fx-background-color "#F3F3F3" :-fx-border-color "#E0E0E0" :-fx-border-width "0 1 0 0"}
   :children [{:fx/type :label
               :text "Эволюционные параметры"
               :style {:-fx-font-size 16 :-fx-font-weight :bold}}
              
              ;; Статистика
              {:fx/type :grid-pane
               :hgap 10
               :vgap 5
               :children [{:fx/type :label :grid-pane/column 0 :grid-pane/row 0 :text "Текущее поколение:"}
                          {:fx/type :label :grid-pane/column 1 :grid-pane/row 0 :style {:-fx-font-weight :bold} :text (str generation)}
                          {:fx/type :label :grid-pane/column 0 :grid-pane/row 1 :text "Стагнация (max 10):"}
                          {:fx/type :label :grid-pane/column 1 :grid-pane/row 1 :text (str stagnation-count)}]}
              
              {:fx/type :separator}
              
              ;; Выбор метрики подобия
              {:fx/type :v-box
               :spacing 5
               :children [{:fx/type :label :text "Метрика подобия:"}
                          {:fx/type :combo-box
                           :value metric-type
                           :items [:euclidean :manhattan :normalized-cross-correlation]
                           :on-value-changed {:event/type :evolution/set-metric}}]}
              
              {:fx/type :separator}
              
              ;; Управляющие кнопки
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
               :on-action {:event/type :evolution/toggle-status}}]})

```

#### Главное окно приложения (Main View / Screen Template)

```clojure
(defn main-view [{:keys [state]}]
  (let [{:keys [evolution/status evolution/generation evolution/metric-type 
                evolution/stagnation-counter population/biomorphs ui/selected-biomorph-id
                target/fx-image]} state]
    {:fx/type :stage
     :showing true
     :title "Genetic Biomorph Evolution Simulator (Dawkins Clockmaker Engine)"
     :scene
     {:fx/type :scene
      :root
      {:fx/type :border-pane
       :left {:fx/type control-panel
              :status status
              :generation generation
              :metric-type metric-type
              :stagnation-count stagnation-counter}
       :center {:fx/type :scroll-pane
                :fit-to-width true
                :content
                {:fx/type :tile-pane
                 :padding 15
                 :hgap 15
                 :vgap 15
                 :pref-columns 3 ; Сетка N особей (например, 3х3 для N=9)
                 :children (mapv (fn [b]
                                   {:fx/type biomorph-cell
                                    :id (:id b)
                                    :genotype (:genotype b)
                                    :fitness (:fitness b)
                                    :fx-image (:fx-image b)
                                    :selected? (= (:id b) selected-biomorph-id)})
                                 biomorphs)}}
       ;; Отображение целевого изображения (котика) в правом углу для верификации
       :right {:fx/type :v-box
               :padding 15
               :spacing 10
               :alignment :top-center
               :style {:-fx-background-color "#F3F3F3"}
               :children [{:fx/type :label :text "Целевой паттерн (Target)" :style {:-fx-font-weight :bold}}
                          (if fx-image
                            {:fx/type :image-view
                             :image fx-image
                             :fit-width 150
                             :fit-height 150}
                            {:fx/type :stack-pane
                             :pref-width 150
                             :pref-height 150
                             :style {:-fx-background-color "#E0E0E0"}
                             :children [{:fx/type :label :text "Нет изображения"}]})]}}}}))

```

---

## 5. Механизмы реактивности и асинхронного сопряжения

Основная архитектурная проблема при работе с UI в задачах эволюционного моделирования — предотвращение "замерзания" (UI thread starvation). Так как вычисление $N$ фитнес-функций на поколение является CPU-bound задачей, применим `clojure.core/agent` для изоляции вычислений и функцию `cljfx.api/run-later` для отправки вычисленных поколений назад в поток отрисовки JavaFX.

### Пример реализации фонового эволюционного цикла

```clojure
(ns biomorph.engine.core
  (:require [cljfx.api :as fx]
            [biomorph.engine.protocols :as p]))

;; Глобальный атом состояния UI
(defonce app-state-atom (atom initial-state))

;; Агент для последовательного выполнения эволюционных шагов вне UI-потока
(defonce evolution-agent (agent nil))

(defn compute-next-generation-loop [agent-state]
  (let [state @app-state-atom]
    (if (= (:evolution/status state) :running)
      (let [current-gen (:evolution/generation state)
            stagnation  (:evolution/stagnation-counter state)
            target-mat  (:target/matrix state)
            
            ;; 1. Вычисление нового поколения (мутации, фитнес) параллельно через pmap
            next-biomorphs (pmap (fn [b] 
                                   ;; (псевдокод мутации и оценки фитнеса)
                                   b) 
                                 (:population/biomorphs state))
            
            ;; Нахождение лучшей особи
            best-fitness (apply max (map :fitness next-biomorphs))
            current-best-fitness (get-in state [:population/best-biomorph :fitness] 0.0)
            
            ;; Проверка критерия останова
            improved? (> best-fitness current-best-fitness)
            next-stagnation (if improved? 0 (inc stagnation))
            converged? (>= next-stagnation 10)
            
            payload {:biomorphs next-biomorphs
                     :generation (inc current-gen)
                     :stagnation next-stagnation
                     :converged? converged?}]
        
        ;; 2. Синхронизация с UI-потоком через run-later
        (fx/run-later
          (swap! app-state-atom (fn [s] 
                                  ;; Вызываем reducer обработки завершения поколения
                                  (biomorph.ui.events/handle-event :engine/generation-computed s payload))))
        
        ;; 3. Рекурсивный вызов следующего шага через send-off, если не сошлись
        (if-not converged?
          (do
            (Thread/sleep 50) ; Искусственная задержка для плавности анимации UI
            (send-off *agent* compute-next-generation-loop))
          (swap! app-state-atom assoc :evolution/status :converged))
        agent-state)
      agent-state)))

```

### Инициализация приложения (Mounting Renderer)

```clojure
(ns biomorph.core
  (:require [cljfx.api :as fx]
            [biomorph.ui.components :refer [main-view]]
            [biomorph.engine.core :refer [app-state-atom]]))

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc (fn [state]
                                    {:fx/type main-view
                                     :state state}))
    :opts {:fx.opt/map-event-handler (fn [event]
                                       ;; Перенаправляем UI-события в handle-event и обновляем атом
                                       (swap! app-state-atom 
                                              (fn [state] 
                                                (biomorph.ui.events/handle-event 
                                                  (:event/type event) 
                                                  state 
                                                  (:payload event)))))}))

(defn -main [& _args]
  ;; Подключаем рендерер к атому состояния
  (fx/mount-renderer app-state-atom renderer))

```

### Архитектурные преимущества спроектированного решения:

1. **Immutability & Pure Views:** Весь UI (`main-view`, `biomorph-cell`) представляет собой чистые функции. Их легко тестировать в REPL, передавая моковые структуры данных состояния.
2. **Concurrency Isolation:** Контур тяжелых вычислений (расчет матричного подобия по Манхэттену/Евклиду) полностью изолирован внутри `evolution-agent` и пула потоков `pmap`. JavaFX UI Thread никогда не блокируется.
3. **Data as API:** Все переходы состояний и декларативные описания компонентов JavaFX являются plain Clojure data-структурами (maps/vectors), что позволяет легко сохранять историю поколений (time-travel debugging) и сериализовать эволюционные цепочки.