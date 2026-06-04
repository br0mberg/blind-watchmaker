(ns biomorph.engine.real
  (:require [biomorph.engine.protocols :as p])
  (:import [java.awt Color BasicStroke RenderingHints]
           [java.awt.image BufferedImage]))

;; ─── Геном → стволы (симметричное дерево Докинза) ───────────────────────────
;; 7 генов (0–6) задают 8 направлений с билатеральной симметрией.
;; Правая половина зеркалит левую: dirs 5,6,7 = mirror 3,2,1.

(defn genome->stems
  "Возвращает вектор из 8 направляющих векторов {:dx :dy}."
  [g]
  [{:dx 0         :dy (g 0)}
   {:dx (g 1)     :dy (g 2)}
   {:dx (g 3)     :dy 0}
   {:dx (g 4)     :dy (- (g 5))}
   {:dx 0         :dy (- (g 6))}
   {:dx (- (g 4)) :dy (- (g 5))}
   {:dx (- (g 3)) :dy 0}
   {:dx (- (g 1)) :dy (g 2)}])

;; ─── Геном → дерево Clojure ──────────────────────────────────────────────────
;; Дерево — вложенные map'ы:
;;   {:x1 :y1 :x2 :y2          ← отрезок этого узла
;;    :children [left right]}   ← ветви (отсутствуют у листьев)

(defn build-tree
  "Рекурсивно строит дерево биоморфы.
   Возвращает вложенный Clojure map (дерево отрезков)."
  [x y len dir stems]
  (let [d  (mod (+ (long dir) 1000000) 8)
        s  (nth stems d)
        nx (+ (double x) (* (double len) (double (:dx s))))
        ny (+ (double y) (* (double len) (double (:dy s))))
        node {:x1 x :y1 y :x2 nx :y2 ny}]
    (if (> len 1)
      (assoc node :children
             [(build-tree nx ny (dec len) (inc dir) stems)
              (build-tree nx ny (dec len) (dec dir) stems)])
      node)))

(defn phenotype-tree
  "Строит дерево биоморфы из генотипа."
  [genotype]
  (let [stems (genome->stems genotype)
        depth (genotype 15)]
    (build-tree 0.0 0.0 depth 0 stems)))

;; ─── Дерево → отрезки ────────────────────────────────────────────────────────

(defn tree->segments
  "Обходит дерево и собирает все отрезки (каждый узел = один отрезок)."
  [tree]
  (when tree
    (lazy-cat [(select-keys tree [:x1 :y1 :x2 :y2])]
              (mapcat tree->segments (:children tree)))))

;; ─── Отрезки → матрица пикселей ──────────────────────────────────────────────

(def ^:const IMG 150)
(def ^:const FIT 130.0)

(defn- bbox [segs]
  (reduce (fn [[ax ay bx by] s]
            [(min ax (:x1 s) (:x2 s))
             (min ay (:y1 s) (:y2 s))
             (max bx (:x1 s) (:x2 s))
             (max by (:y1 s) (:y2 s))])
          [0.0 0.0 0.0 0.0]
          segs))

(defn segments->matrix
  "Растеризует отрезки в BufferedImage 150×150, масштабируя под кадр.
   Возвращает {:width :height :pixels} — формат совместимый с images/matrix->fx-image."
  [segs]
  (let [img (BufferedImage. IMG IMG BufferedImage/TYPE_INT_RGB)
        gfx (.createGraphics img)]
    (.setColor gfx Color/WHITE)
    (.fillRect gfx 0 0 IMG IMG)
    (.setRenderingHint gfx RenderingHints/KEY_ANTIALIASING
                       RenderingHints/VALUE_ANTIALIAS_ON)
    (.setColor gfx Color/BLACK)
    (.setStroke gfx (BasicStroke. 1.0))
    (when (seq segs)
      (let [[minx miny maxx maxy] (bbox segs)
            w     (max 1.0 (- maxx minx))
            h     (max 1.0 (- maxy miny))
            scale (/ FIT (max w h))
            cx    (/ (+ minx maxx) 2.0)
            cy    (/ (+ miny maxy) 2.0)
            c     (/ IMG 2.0)
            px    (fn [v ctr] (int (Math/round (+ c (* scale (- (double v) ctr))))))]
        (doseq [{:keys [x1 y1 x2 y2]} segs]
          (.drawLine gfx (px x1 cx) (px y1 cy) (px x2 cx) (px y2 cy)))))
    (.dispose gfx)
    (let [n IMG out (int-array (* n n))]
      (dotimes [y n]
        (dotimes [x n]
          (let [rgb (.getRGB img x y)
                r   (bit-and (bit-shift-right rgb 16) 0xFF)
                gv  (bit-and (bit-shift-right rgb 8)  0xFF)
                b   (bit-and rgb 0xFF)
                lum (int (Math/round (+ (* 0.299 r) (* 0.587 gv) (* 0.114 b))))]
            (aset out (+ (* y n) x) lum))))
      {:width n :height n :pixels out})))

;; ─── Реализация протоколов ───────────────────────────────────────────────────

(deftype RealEngine []
  p/BiomorphEngine
  (generate-initial-genotype [_]
    (into (vec (repeatedly 15 #(- (rand-int 19) 9)))
          [(+ 2 (rand-int 11))]))

  (mutate-genotype [_ genotype]
    (let [idx   (rand-int 16)
          delta (if (< (rand) 0.5) -1 1)
          [lo hi] (if (< idx 15) [-9 9] [2 12])]
      (assoc genotype idx (max lo (min hi (+ (genotype idx) delta))))))

  (draw-phenotype [_ genotype]
    (phenotype-tree genotype))

  p/FitnessEvaluator
  (rasterize [_ tree _w _h]
    (-> tree tree->segments segments->matrix))

  ;; Заглушка — реализуется в Этапе 3
  (evaluate-similarity [_ candidate _target _metric-type]
    (+ 0.3 (/ (mod (Math/abs (hash (:pixels candidate))) 700) 1000.0))))

(def engine (->RealEngine))
