(ns biomorph.engine.phenotype
  "Геном → симметричное дерево биоморфы (зона Dev A).
   Гены 0–6: 8 направлений stems (как calculateStems в biomorph.js).
   Гены 7–14: смещение dir на каждом уровне рекурсии (скелет/позиция ветвей).
   Ген 15: глубина дерева (length).")

(defn genome->stems
  "Вектор из 8 направляющих {:dx :dy} — симметрия как в biomorph.js."
  [g]
  [{:dx 0         :dy (g 0)}
   {:dx (g 1)     :dy (g 2)}
   {:dx (g 3)     :dy 0}
   {:dx (g 4)     :dy (- (g 5))}
   {:dx 0         :dy (- (g 6))}
   {:dx (- (g 4)) :dy (- (g 5))}
   {:dx (- (g 3)) :dy 0}
   {:dx (- (g 1)) :dy (g 2)}])

(defn- level->skeleton-gene
  "Уровень 0 — у корня; гены 7–14 кодируют смещение направления по уровням."
  [level]
  (+ 7 (min 7 level)))

(defn- skeleton-dir-offset
  [level genotype gene-14-as-thickness?]
  (if (and gene-14-as-thickness? (= level 7))
    0
    (genotype (level->skeleton-gene level))))

(defn build-tree
  "Рекурсивно строит PhenotypeTree — вложенный map отрезков."
  [x y len max-depth dir stems genotype gene-14-as-thickness?]
  (let [level (long (- max-depth len))
        d     (mod (+ (long dir) (long (skeleton-dir-offset level genotype gene-14-as-thickness?))) 8)
        s     (nth stems d)
        nx    (+ (double x) (* (double len) (double (:dx s))))
        ny    (+ (double y) (* (double len) (double (:dy s))))
        node  {:x1 x :y1 y :x2 nx :y2 ny}]
    (if (> len 1)
      (assoc node :children
             [(build-tree nx ny (dec len) max-depth (inc dir) stems genotype gene-14-as-thickness?)
              (build-tree nx ny (dec len) max-depth (dec dir) stems genotype gene-14-as-thickness?)])
      node)))

(defn line-width-from-gene-14
  "Толщина в пикселях: |ген14|, диапазон 0–9 (как значение гена)."
  [genotype]
  (double (Math/abs (long (genotype 14)))))

(defn phenotype-tree
  "Строит дерево биоморфы из генотипа (16 генов).
   gene-14-as-thickness? — гена 14 не смещает dir, а задаёт :render/line-width."
  ([genotype] (phenotype-tree genotype false))
  ([genotype gene-14-as-thickness?]
   (let [stems (genome->stems genotype)
         depth (long (genotype 15))
         tree  (build-tree 0.0 0.0 depth depth 0 stems genotype gene-14-as-thickness?)]
     (if gene-14-as-thickness?
       (assoc tree :render/line-width (line-width-from-gene-14 genotype))
       tree))))

(def genotype->tree phenotype-tree)
