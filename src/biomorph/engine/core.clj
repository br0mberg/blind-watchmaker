(ns biomorph.engine.core
  (:require [biomorph.state :as state]
            [biomorph.engine.stub :as stub]
            [biomorph.engine.protocols :as p]
            [biomorph.engine.images :as images]
            [cljfx.api :as fx]))

(defonce engine stub/engine)

(defonce app-state-atom (atom state/initial-state))

(defonce evolution-agent (agent nil))

(defn stub-fitness [genotype]
  (let [candidate (images/genotype->matrix genotype)]
    (p/evaluate-similarity engine candidate (:target/matrix @app-state-atom)
                           (:evolution/metric-type @app-state-atom))))

(defn- make-biomorph [id genotype]
  {:id id
   :genotype genotype
   :fitness (stub-fitness genotype)
   :fx-image (images/genotype->fx-image genotype)})

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
  (let [genotype (p/mutate-genotype engine (:genotype biomorph))]
    (assoc biomorph
           :genotype genotype
           :fitness (stub-fitness genotype)
           :fx-image (images/genotype->fx-image genotype))))

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
