(ns biomorph.engine.render
  "Генотип → дерево → растр (единая точка, без dynamic vars)."
  (:require [biomorph.engine.phenotype :as phenotype]
            [biomorph.engine.raster :as raster]))

(defn genotype->matrix
  [genotype gene-14-as-thickness?]
  (let [tree (phenotype/phenotype-tree genotype gene-14-as-thickness?)
        line-width (when gene-14-as-thickness?
                     (phenotype/line-width-from-gene-14 genotype))]
    (raster/rasterize-tree tree raster/default-width raster/default-height line-width)))
