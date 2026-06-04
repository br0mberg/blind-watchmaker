(ns biomorph.engine.stub
  (:require [biomorph.engine.protocols :as p]
            [biomorph.engine.images :as images]))

(defn- rand-gene [lo hi]
  (+ lo (rand-int (inc (- hi lo)))))

(defn- clamp [v lo hi]
  (max lo (min hi v)))

(deftype StubEngine []
  p/BiomorphEngine
  (generate-initial-genotype [_]
    (into (vec (repeatedly 15 #(rand-gene -9 9)))
          [(rand-gene 2 12)]))

  (mutate-genotype [_ genotype]
    (let [idx (rand-int 16)
          [lo hi] (if (< idx 15) [-9 9] [2 12])
          value (genotype idx)
          delta (cond
                  (= value lo) 1
                  (= value hi) -1
                  (< (rand) 0.5) -1
                  :else 1)]
      (assoc genotype idx (clamp (+ (genotype idx) delta) lo hi))))

  (draw-phenotype [_ _genotype]
    {:segments []}))

(extend-type StubEngine
  p/FitnessEvaluator
  (rasterize [_ _geometry _width _height]
    (images/genotype->matrix (vec (repeatedly 16 #(rand-int 17)))))

  (evaluate-similarity [_ candidate _target]
    (+ 0.3 (/ (mod (Math/abs (hash (:pixels candidate))) 700) 1000.0))))

(def engine (->StubEngine))
