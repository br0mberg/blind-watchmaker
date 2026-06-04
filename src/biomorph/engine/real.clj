(ns biomorph.engine.real
  (:require [biomorph.engine.protocols :as p]
            [biomorph.engine.phenotype :as phenotype]
            [biomorph.engine.raster :as raster]
            [biomorph.engine.render :as render]))

(defn mutate-genotype*
  "thickness-mode? — чаще мутируем ген 14 (толщина)."
  [genotype thickness-mode?]
  (let [idx (if (and thickness-mode? (< (rand) 0.4))
              14
              (rand-int 16))
        delta (if (< (rand) 0.5) -1 1)
        [lo hi] (if (< idx 15) [-9 9] [2 12])]
    (assoc genotype idx (max lo (min hi (+ (genotype idx) delta))))))

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

  ;; Заглушка — реализуется в Этапе 3
  (evaluate-similarity [_ candidate _target _metric-type]
    (+ 0.3 (/ (mod (Math/abs (hash (:pixels candidate))) 700) 1000.0))))

(def engine (->RealEngine))
