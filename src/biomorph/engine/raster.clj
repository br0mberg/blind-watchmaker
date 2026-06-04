(ns biomorph.engine.raster
  "Дерево → растр 150×150 (зона Dev B). Не зависит от генотипа."
  (:import [java.awt Color BasicStroke RenderingHints]
           [java.awt.geom Line2D$Double]
           [java.awt.image BufferedImage]))

(defn tree->segments
  "Обходит PhenotypeTree и собирает все отрезки."
  [tree]
  (when tree
    (lazy-cat [(select-keys tree [:x1 :y1 :x2 :y2])]
              (mapcat tree->segments (:children tree)))))

(def ^:const default-width 150)
(def ^:const default-height 150)
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
  "Растеризует отрезки. line-width — толщина штриха в пикселях кадра (nil → 1px)."
  ([segs width height] (segments->matrix segs width height nil))
  ([segs width height line-width]
   (let [img (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
         gfx (.createGraphics img)]
     (.setColor gfx Color/WHITE)
     (.fillRect gfx 0 0 width height)
     (.setRenderingHint gfx RenderingHints/KEY_ANTIALIASING
                        RenderingHints/VALUE_ANTIALIAS_ON)
     (.setColor gfx Color/BLACK)
     (when (seq segs)
       (let [thick? (some? line-width)
             [minx miny maxx maxy] (bbox segs)
             w     (max 1.0 (- maxx minx))
             h     (max 1.0 (- maxy miny))
             scale (/ FIT (max w h))
             stroke-w (float (if thick? line-width 1.0))
             cx    (/ (+ minx maxx) 2.0)
             cy    (/ (+ miny maxy) 2.0)
             c     (/ width 2.0)
             px    (fn [v ctr] (+ c (* scale (- (double v) ctr))))]
         (when thick?
           (.setRenderingHint gfx RenderingHints/KEY_ANTIALIASING
                              RenderingHints/VALUE_ANTIALIAS_OFF))
         (.setStroke gfx (BasicStroke. stroke-w BasicStroke/CAP_ROUND BasicStroke/JOIN_ROUND))
         (doseq [{:keys [x1 y1 x2 y2]} segs]
           (.draw gfx (Line2D$Double. (px x1 cx) (px y1 cy) (px x2 cx) (px y2 cy))))))
     (when (empty? segs)
       (.setStroke gfx (BasicStroke. 1.0)))
    (.dispose gfx)
    (let [total   (* width height)
          rgb-arr (int-array total)
          out     (int-array total)]
      (.getRGB img 0 0 width height rgb-arr 0 width)
      (dotimes [i total]
        (let [rgb (aget rgb-arr i)
              r   (bit-and (bit-shift-right rgb 16) 0xFF)
              gv  (bit-and (bit-shift-right rgb 8)  0xFF)
              b   (bit-and rgb 0xFF)]
          (aset out i (int (Math/round (+ (* 0.299 r) (* 0.587 gv) (* 0.114 b)))))))
      {:width width :height height :pixels out}))))

(defn rasterize-tree
  "Единая точка входа: PhenotypeTree → матрица пикселей."
  ([tree width height]
   (rasterize-tree tree width height (:render/line-width tree)))
  ([tree width height line-width]
   (-> tree tree->segments (segments->matrix width height line-width))))
