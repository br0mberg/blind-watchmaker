(ns biomorph.engine.images
  (:import [javafx.scene.image Image WritableImage]
           [javafx.scene.paint Color]
           [java.io FileInputStream]))

(def ^:const phenotype-size 150)

(defn- gray-value [x y genotype]
  (let [h (mod (hash genotype) 997)
        v (mod (+ (* x 7) (* y 13) h) 256)]
    (if (even? (mod (+ v (nth genotype (mod (+ x y) 16) 0)) 2))
      0
      255)))

(defn genotype->matrix
  "Псевдо ч/б матрица 150×150 от генотипа (заглушка, не биоморф)."
  [genotype]
  (let [n phenotype-size
        pixels (int-array (* n n))]
    (dotimes [y n]
      (dotimes [x n]
        (aset pixels (+ (* y n) x) (gray-value x y genotype))))
    {:width n :height n :pixels pixels}))

(defn matrix->fx-image
  [{:keys [width height pixels]}]
  (let [img (WritableImage. width height)
        pw (.getPixelWriter img)]
    (dotimes [y height]
      (dotimes [x width]
        (let [g (aget pixels (+ (* y width) x))]
          (.setColor pw x y (Color/gray (double (/ g 255.0)))))))
    img))

(defn genotype->fx-image [genotype]
  (-> genotype genotype->matrix matrix->fx-image))

(defn- sample-to-size
  [^Image source]
  (let [sw (.getWidth source)
        sh (.getHeight source)
        out (WritableImage. phenotype-size phenotype-size)
        reader (.getPixelReader source)
        pw (.getPixelWriter out)]
    (dotimes [y phenotype-size]
      (dotimes [x phenotype-size]
        (let [sx (min (dec sw) (int (* x (/ sw phenotype-size))))
              sy (min (dec sh) (int (* y (/ sh phenotype-size))))
              argb (.getArgb reader sx sy)
              ;; ARGB → яркость
              r (bit-and (bit-shift-right argb 16) 0xFF)
              g (bit-and (bit-shift-right argb 8) 0xFF)
              b (bit-and argb 0xFF)
              gray (int (/ (+ r g b) 3.0))]
          (.setColor pw x y (Color/gray (/ gray 255.0))))))
    out))

(defn image->matrix
  "Плоская int-матрица 0/255 из JavaFX Image 150×150."
  [^Image img]
  (let [scaled (sample-to-size img)
        reader (.getPixelReader scaled)
        n phenotype-size
        pixels (int-array (* n n))]
    (dotimes [y n]
      (dotimes [x n]
        (let [c (.getColor reader x y)
              g (int (* 255 (.getBrightness c)))]
          (aset pixels (+ (* y n) x) (if (< g 128) 0 255)))))
    {:width n :height n :pixels pixels}))

(defn load-target-image!
  "Загружает файл → {:fx-image :matrix :path}."
  [^java.io.File file]
  (with-open [stream (FileInputStream. file)]
    (let [img (Image. stream)
          scaled (sample-to-size img)
          matrix (image->matrix scaled)]
      {:path (.getAbsolutePath file)
       :fx-image scaled
       :matrix matrix})))
