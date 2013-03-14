(ns cassiel.cubewar.state-navigation
  "Navigation at the level of the entire game state, by composition,
   with collision detection."
  (:require (cassiel.cubewar [view :as v]))
  (:use [slingshot.slingshot :only [throw+]]))

(defn navigate
  "Alter the state by letting this player perform a single navigation step.
   Throws IllegalArgumentException for collision. (TODO: should probably
   be something less generic.)"
  [state name nav-fn]
  (let [p (get state name)]
    (if p
      ;; Speculatively test the navigation move to see whether that would
      ;; put us in a wall or on top of another player (i.e. ensure the
      ;; new location is empty).
      (let [new-p (comp p nav-fn)
            dest-cell (v/look state new-p [0 0 0])]
        ;; The destination must be empty, or it must (already) be us:
        (if (#{:empty {:player {:name name}}} dest-cell)
          (assoc state name new-p)
          (throw+ {:type ::NOT-EMPTY :contents dest-cell})))
      (throw+ {:type ::NOT-IN-PLAY :name name}))))
