(ns biomorph.engine.core
  (:require [biomorph.state :as state]
            [biomorph.engine.real :as real]
            [biomorph.engine.protocols :as p]
            [biomorph.engine.images :as images]
            [cljfx.api :as fx]))

(defonce engine real/engine)

(defonce app-state-atom (atom state/initial-state))

(defonce evolution-agent (agent nil))

(defn- compute-fitness [matrix]
  (let [target (:target/matrix @app-state-atom)
        metric (:evolution/metric-type @app-state-atom)]
    (if target
      (p/evaluate-similarity engine matrix target metric)
      0.5)))

(defn- make-biomorph [id genotype]
  (let [tree   (p/draw-phenotype engine genotype)
        matrix (p/rasterize engine tree 150 150)
        fx-img (images/matrix->fx-image matrix)]
    {:id id :genotype genotype :fitness (compute-fitness matrix) :fx-image fx-img}))

(defn seed-initial-population!
  []
  (let [n (:population/size @app-state-atom)
        biomorphs (vec (for [id (range n)]
                         (make-biomorph id (p/generate-initial-genotype engine))))
        best (apply max-key :fitness biomorphs)]
    (swap! app-state-atom assoc
           :population/biomorphs biomorphs
           :population/best-biomorph best)))

(defn- evolve-biomorph [biomorph]
  (let [genotype (p/mutate-genotype engine (:genotype biomorph))
        tree     (p/draw-phenotype engine genotype)
        matrix   (p/rasterize engine tree 150 150)]
    (assoc biomorph
           :genotype genotype
           :fitness  (compute-fitness matrix)
           :fx-image (images/matrix->fx-image matrix))))

(defn compute-next-generation-loop [_agent-state]
  (let [state @app-state-atom]
    (if (= (:evolution/status state) :running)
      (let [current-gen (:evolution/generation state)
            next-biomorphs (vec (pmap evolve-biomorph (:population/biomorphs state)))
            best (apply max-key :fitness next-biomorphs)
            current-best-fitness (get-in state [:population/best-biomorph :fitness] 0.0)
            best-fitness (:fitness best)
            improved? (> best-fitness current-best-fitness)
            next-stagnation (if improved? 0 (inc (:evolution/stagnation-counter state)))
            converged? (>= next-stagnation 10)
            payload {:biomorphs next-biomorphs
                     :generation (inc current-gen)
                     :stagnation next-stagnation
                     :converged? converged?
                     :best-biomorph best}]
        (fx/run-later
          (swap! app-state-atom state/apply-generation-computed payload))
        (when-not converged?
          (Thread/sleep 50)
          (when (= :running (:evolution/status @app-state-atom))
            (send-off evolution-agent compute-next-generation-loop)))
        nil)
      nil)))
