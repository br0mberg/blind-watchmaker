(ns biomorph.engine.core
  (:require [biomorph.state :as state]
            [biomorph.engine.real :as real]
            [biomorph.engine.render :as render]
            [biomorph.engine.protocols :as p]
            [biomorph.engine.images :as images])
  (:import [javafx.application Platform]))

(defonce engine real/engine)

(defonce app-state-atom (atom state/initial-state))

(defonce evolution-agent (agent nil))

(defn compute-fitness
  ([matrix]
   (compute-fitness matrix (:target/matrix @app-state-atom)))
  ([matrix target]
   (if target
     (p/evaluate-similarity engine matrix target)
     0.0)))

(defn make-biomorph
  ([id genotype]
   (let [state @app-state-atom]
     (make-biomorph id genotype (:target/matrix state)
                    (:ui/gene-14-as-thickness? state false))))
  ([id genotype target]
   (make-biomorph id genotype target
                  (:ui/gene-14-as-thickness? @app-state-atom false)))
  ([id genotype target thickness?]
   (let [matrix (render/genotype->matrix genotype thickness?)
         fx-img (images/matrix->fx-image matrix)]
     {:id id
      :genotype genotype
      :matrix matrix
      :fitness (compute-fitness matrix target)
      :fx-image fx-img})))

(defn apply-gene-14-mode
  "Включить режим гена 14 и перерисовать текущую популяцию."
  [state gene-14-as-thickness?]
  (let [state (assoc state :ui/gene-14-as-thickness? gene-14-as-thickness?)
        target (:target/matrix state)]
    (if (seq (:population/biomorphs state))
      (let [biomorphs (vec (map-indexed
                            (fn [id b]
                              (make-biomorph id (:genotype b) target gene-14-as-thickness?))
                            (:population/biomorphs state)))
            best (apply max-key :fitness biomorphs)]
        (assoc state
               :population/biomorphs biomorphs
               :population/best-biomorph best))
      state)))

(defn reindex-biomorphs [biomorphs]
  (mapv (fn [id biomorph] (assoc biomorph :id id))
        (range)
        biomorphs))

(defn sort-and-trim [biomorphs n]
  (->> biomorphs
       (sort-by :fitness >)
       (take n)
       reindex-biomorphs))

(defn crossover-genotypes [parent-a parent-b]
  (let [genotype-a (:genotype parent-a)
        genotype-b (:genotype parent-b)]
    [(vec (concat (subvec genotype-a 0 8) (subvec genotype-b 8 16)))
     (vec (concat (subvec genotype-b 0 8) (subvec genotype-a 8 16)))]))

(defn child-genotypes [parents thickness?]
  (vec
   (for [i (range (count parents))
         j (range (inc i) (count parents))
         genotype (crossover-genotypes (nth parents i) (nth parents j))]
     (real/mutate-genotype* genotype thickness?))))

(defn next-generation [parents target population-size thickness?]
  (let [children (map-indexed (fn [idx genotype]
                                (make-biomorph (+ population-size idx) genotype target thickness?))
                              (child-genotypes parents thickness?))
        elite-count 5
        elite (sort-and-trim (into (vec parents) children) elite-count)
        random-biomorphs (vec (for [id (range elite-count population-size)]
                                (make-biomorph
                                 id
                                 (p/generate-initial-genotype engine)
                                 target
                                 thickness?)))]
    (reindex-biomorphs (into elite random-biomorphs))))

(defn rescore-biomorphs [biomorphs target]
  (sort-and-trim
   (mapv (fn [biomorph]
           (assoc biomorph :fitness (compute-fitness (:matrix biomorph) target)))
         biomorphs)
   (count biomorphs)))

(defn seed-initial-population!
  []
  (let [n (:population/size @app-state-atom)
        biomorphs (vec (for [id (range n)]
                         (make-biomorph id (p/generate-initial-genotype engine))))
        best (apply max-key :fitness biomorphs)]
    (swap! app-state-atom assoc
           :population/biomorphs biomorphs
           :population/best-biomorph best)))

(defn compute-next-generation-loop [_agent-state]
  (let [state @app-state-atom]
    (if (= (:evolution/status state) :running)
      (let [current-gen (:evolution/generation state)
            population-size (:population/size state)
            thickness? (:ui/gene-14-as-thickness? state false)
            next-biomorphs (next-generation (:population/biomorphs state)
                                            (:target/matrix state)
                                            population-size
                                            thickness?)
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
        (Platform/runLater
         #(swap! app-state-atom
                 (fn [latest-state]
                   (if (and (= :running (:evolution/status latest-state))
                            (= current-gen (:evolution/generation latest-state)))
                     (state/apply-generation-computed latest-state payload)
                     latest-state))))
        (when-not converged?
          (Thread/sleep 50)
          (when (= :running (:evolution/status @app-state-atom))
            (send-off evolution-agent compute-next-generation-loop)))
        nil)
      nil)))
