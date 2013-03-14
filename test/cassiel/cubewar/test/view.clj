(ns cassiel.cubewar.test.view
  "Players' view generation."
  (:use clojure.test)
  (:require (cassiel.cubewar [cube :as c]
                             [players :as pl]
                             [view :as v]
                             [state-navigation :as n])))

(deftest formatting
  (testing "view formatting"
    (is (= {:x0 {:y0 1 :y1 2 :y2 3}
            :x1 {:y0 4 :y1 5 :y2 6}
            :x2 {:y0 7 :y1 8 :y2 9}}
           (v/dict-format [[1 2 3] [4 5 6] [7 8 9]])))))

(deftest basics
  (testing "empty view"
    (let [p (pl/gen-player [0 0 0])
          state (pl/add-player {} 'me p)]
      (is (= :empty
             (v/look state p [0 1 0])))))

  (testing "can see self"
    (let [p (pl/gen-player [0 0 0])
          state (pl/add-player {} 'me p)]
      (is (= {:player {:name 'me}}
             (v/look state p [0 0 0])))))

  (testing "can see enemy ahead"
    (let [me (pl/gen-player [0 0 0])
          other (pl/gen-player [0 1 0])
          state (-> {}
                    (pl/add-player 'me me)
                    (pl/add-player 'other other))]
      (is (= {:player {:name 'other}}
             (v/look state me [0 1 0]))))))

(deftest plane-view
  (testing "initial view"
    (let [p0 (pl/gen-player [0 0 0])
          state (-> {} (pl/add-player 'me p0))]
      (is (= [(repeat 3 :wall)
              [{:player {:name 'me}} :empty :empty]
              (repeat 3 :empty)]
             (v/look-plane state p0)))))

  (testing "complex view"
    (let [state (-> {}
                    (pl/add-player :P1 (pl/gen-player [0 0 0]))
                    (pl/add-player :P2 (pl/gen-player [1 0 0]))
                    (pl/add-player :P3 (pl/gen-player [0 1 0])))]
      (is (= [[{:player {:name :P1}} {:player {:name :P3}} :empty]
              [{:player {:name :P2}} :empty :empty]
              (repeat 3 :empty)]
             (v/look-plane state (pl/gen-player [1 0 0])))))))

(deftest fire-view
  (testing "initial fire view"
    (is (= (repeat 3 :empty)
           (v/look-ahead {} (pl/gen-player [0 0 0])))))

  (testing "wall view"
    (is (= [:empty :wall :wall]
           (v/look-ahead {} (pl/gen-player [0 2 0])))))

  (testing "complex fire view"
    (let [state (-> {}
                    (pl/add-player :P1 (pl/gen-player [0 0 0]))
                    (pl/add-player :P2 (pl/gen-player [1 0 0]))
                    (pl/add-player :P3 (pl/gen-player [1 2 0])))]
      (is (= [{:player {:name :P2}} :empty {:player {:name :P3}}]
             (v/look-ahead state (pl/gen-player [1 0 0])))))))

(deftest test-firings
  (testing "miss"
    (let [p (pl/gen-player [0 0 0])
          state (pl/add-player {} :PLAYER p)]
      (is (nil? (v/fire state p)))))

  (testing "hit"
    (let [p (pl/gen-player [0 0 0])
          state (-> {}
                    (pl/add-player :P1 p)
                    (pl/add-player :P2 (pl/gen-player [0 1 0])))]
      (is (= {:name :P2} (v/fire state p)))))

  (testing "hit further"
    (let [p (pl/gen-player [0 0 0])
          state (-> {}
                    (pl/add-player :P1 p)
                    (pl/add-player :P2 (pl/gen-player [0 2 0])))]
      (is (= {:name :P2} (v/fire state p)))))

  (testing "hit nearest"
    (let [p (pl/gen-player [0 0 0])
          state (-> {}
                    (pl/add-player :P1 p)
                    (pl/add-player :P2 (pl/gen-player [0 1 0]))
                    (pl/add-player :P3 (pl/gen-player [0 2 0])))]
      (is (= {:name :P2} (v/fire state p)))))

  (testing "turn to hit"
    (let [state (-> {}
                    (pl/add-player :P1 (pl/gen-player [0 0 0]))
                    (pl/add-player :P2 (pl/gen-player [1 0 0])))
          state' (n/navigate state :P1 c/yaw-right)]
      (is (= {:name :P2}
             (v/fire state' (get state' :P1)))))))
