(ns biomorph.engine.real
  (:require [biomorph.engine.protocols :as p]
            [biomorph.engine.phenotype :as phenotype]
            [biomorph.engine.raster :as raster]))

(defn- boundary-delta [value lo hi]
  (cond
    (= value lo) 1
    (= value hi) -1
    (< (rand) 0.5) -1
    :else 1))

(defn mutate-genotype*
  "thickness-mode? — чаще мутируем ген 14 (толщина); иначе отражение на границах гена."
  [genotype thickness-mode?]
  (let [idx (if (and thickness-mode? (< (rand) 0.4))
              14
              (rand-int 16))
        [lo hi] (if (< idx 15) [-9 9] [2 12])
        value (genotype idx)
        delta (boundary-delta value lo hi)]
    (assoc genotype idx (max lo (min hi (+ value delta))))))

(deftype RealEngine []
  p/BiomorphEngine
  (generate-initial-genotype [_]
    (into (vec (repeatedly 15 #(- (rand-int 19) 9)))
          [(+ 2 (rand-int 11))]))

  (mutate-genotype [_ genotype]
    (mutate-genotype* genotype false))

  (draw-phenotype [_ genotype]
    (phenotype/phenotype-tree genotype false))

  p/FitnessEvaluator
  (rasterize [_ tree _w _h]
    (raster/rasterize-tree tree raster/default-width raster/default-height
                           (:render/line-width tree)))

  (evaluate-similarity [_ candidate target]
    (let [^ints candidate-pixels (:pixels candidate)
          ^ints target-pixels (:pixels target)
          n (min (alength candidate-pixels) (alength target-pixels))]
      (if (zero? n)
        0.0
        (let [mean-c (/ (loop [i 0 acc 0.0]
                          (if (< i n)
                            (recur (inc i) (+ acc (aget candidate-pixels i)))
                            acc))
                        n)
              mean-t (/ (loop [i 0 acc 0.0]
                          (if (< i n)
                            (recur (inc i) (+ acc (aget target-pixels i)))
                            acc))
                        n)
              [dot norm-c norm-t]
              (loop [i 0 dot 0.0 norm-c 0.0 norm-t 0.0]
                (if (< i n)
                  (let [dc (- (double (aget candidate-pixels i)) mean-c)
                        dt (- (double (aget target-pixels i)) mean-t)]
                    (recur (inc i)
                           (+ dot (* dc dt))
                           (+ norm-c (* dc dc))
                           (+ norm-t (* dt dt))))
                  [dot norm-c norm-t]))
              denom (* (Math/sqrt norm-c) (Math/sqrt norm-t))]
          (if (zero? denom)
            0.0
            (/ dot denom)))))))

(def engine (->RealEngine))
