(ns biomorph.engine.protocols)

(defprotocol BiomorphEngine
  (generate-initial-genotype [this]
    "Случайный генотип: 15 генов [-9, +9], 16-й [2, 12].")
  (mutate-genotype [this genotype]
    "Точечная мутация одного гена на +/- 1 в допустимых границах.")
  (draw-phenotype [this genotype]
    "Геометрия биоморфы (вектор отрезков) по генотипу."))

(defprotocol FitnessEvaluator
  (rasterize [this geometry width height]
    "Растеризация геометрии в матрицу пикселей.")
  (evaluate-similarity [this candidate-matrix target-matrix metric-type]
    "Подобие кандидата целевой матрице.
     metric-type: :euclidean | :manhattan | :normalized-cross-correlation"))
