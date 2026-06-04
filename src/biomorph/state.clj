(ns biomorph.state)

(def initial-state
  {:evolution/status :idle
   :evolution/generation 0
   :evolution/stagnation-counter 0

   :target/image-path nil
   :target/matrix nil
   :target/fx-image nil

   :population/size 10
   :population/biomorphs []
   :population/best-biomorph nil

   :ui/selected-biomorph-id nil
   :ui/gene-14-as-thickness? false
   :ui/audio-muted? false})

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

(defn apply-population-reset
  [state {:keys [biomorphs best-biomorph]}]
  (assoc state
         :evolution/status :idle
         :evolution/generation 0
         :evolution/stagnation-counter 0
         :population/biomorphs biomorphs
         :population/best-biomorph best-biomorph
         :ui/selected-biomorph-id nil))
