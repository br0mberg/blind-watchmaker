(ns biomorph.state)

(def initial-state
  {:evolution/status :idle
   :evolution/generation 0
   :evolution/stagnation-counter 0
   :evolution/metric-type :euclidean

   :target/image-path nil
   :target/matrix nil
   :target/fx-image nil

   :population/size 9
   :population/biomorphs []
   :population/best-biomorph nil

   :ui/selected-biomorph-id nil
   :ui/gene-14-as-thickness? false})

(defn apply-generation-computed
  [state {:keys [biomorphs generation stagnation converged? best-biomorph]}]
  (-> state
      (assoc :population/biomorphs biomorphs
             :evolution/generation generation
             :evolution/stagnation-counter stagnation
             :population/best-biomorph best-biomorph)
      (cond-> converged? (assoc :evolution/status :converged))))

(defn apply-target-loaded
  [state {:keys [path fx-image matrix]}]
  (assoc state
         :target/image-path path
         :target/fx-image fx-image
         :target/matrix matrix))
