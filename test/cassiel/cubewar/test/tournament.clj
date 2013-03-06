(ns cassiel.cubewar.test.tournament
  "Test tournament machinery."
  (:use clojure.test)
  (:require (cassiel.cubewar [cube :as c]
                             [players :as pl]
                             [state-navigation :as n]
                             [tournament :as t])))

(deftest fire-journal
  (testing "miss"
    (let [arena (-> {}
                    (pl/add-player :P (pl/gen-player [0 0 0])))
          world {:arena arena :scoring {:P 10}}]
      (is (= [{:to :P :action :miss}]
             (-> world
                 (t/fire :P)
                 (:journal))))))

  (testing "hit"
    (let [arena (-> {}
                    (pl/add-player :P1 (pl/gen-player [0 0 0]))
                    (pl/add-player :P2 (pl/gen-player [0 1 0])))
          world {:arena arena :scoring {:P1 10 :P2 10}}]
      (is (= [{:to :P1 :action :hit :args {:player :P2}}
              {:to :P2 :action :hit-by :args {:player :P1 :hit-points 9}}]
             (-> world
                 (t/fire :P1)
                 (:journal)))))))

(deftest sanity-check
  (testing "rogue in arena"
    (let [arena (-> {}
                    (pl/add-player :P1 (pl/gen-player [0 0 0]))
                    (pl/add-player :P2 (pl/gen-player [0 1 0])))
          world {:arena arena :scoring {:P1 10}}]

      (is (thrown-with-msg? IllegalStateException #"player not in scoring system: :P2"
            (-> world (t/fire :P1)))))))

(deftest move-journal
  (testing "forward OK"
    (let [arena (-> {}
                    (pl/add-player :P (pl/gen-player [0 0 0])))
          world {:arena arena :scoring nil}]
      (is (= [{:to :P :action :view :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                           :x1 {:y0 {:player :P} :y1 :empty :y2 :wall}
                                           :x2 {:y0 :empty :y1 :empty :y2 :wall}
                                           :manoeuvre :forward}}]
             (-> world
                 (t/move :P :forward c/forward)
                 (:journal))))))

  (testing "forward blocked"
    (let [arena (-> {}
                    (pl/add-player :P (pl/gen-player [0 2 0])))
          world {:arena arena :scoring nil}]
      (is (= [{:to :P :action :blocked}]
             (-> world
                 (t/move :P :forward c/forward)
                 (:journal))))))

  (testing "yaw left"
    (let [arena (-> {}
                    (pl/add-player :P (pl/gen-player [0 0 0])))
          world {:arena arena :scoring nil}]
      (is (= [{:to :P :action :view :args {:x0 {:y0 :wall :y1 :wall :y2 :wall }
                                           :x1 {:y0 {:player :P} :y1 :wall :y2 :wall}
                                           :x2 {:y0 :empty :y1 :wall :y2 :wall}
                                           :manoeuvre :yaw-left}}]
             (-> world
                 (t/move :P :yaw-left c/yaw-left)
                 (:journal)))))))

(deftest basic-round-scoring
  (testing "in scoring but not in round"
    ;; We can't really get an empty cube, but:
    (let [world {:arena {} :scoring {:P 10}}
          {j :journal} (t/fire world :P)]
      (is (= [{:to :P :action :error :args {:message "not currently in play"}}]
             j))))

  (testing "score hit"
    (let [arena (-> {}
                    (pl/add-player :P1 (pl/gen-player [0 0 0]))
                    (pl/add-player :P2 (pl/gen-player [0 1 0])))
          world0 {:arena arena :scoring {:P1 10 :P2 10}}
          world1 (t/fire world0 :P1)]
      (is (= [{:to :P1 :action :hit :args {:player :P2}}
              {:to :P2 :action :hit-by :args {:player :P1 :hit-points 9}}]
             (:journal world1)))
      (is (= 9 (:P2 (:scoring world1))))))

  (testing "knockout"
    (let [arena (-> {}
                    (pl/add-player :P1 (pl/gen-player [0 0 0]))
                    (pl/add-player :P2 (pl/gen-player [0 1 0])))
          world0 {:arena arena :scoring {:P1 1 :P2 1}}
          world1 (t/fire world0 :P1)]
      (is (= [{:to :P1 :action :hit :args {:player :P2}}
              {:to :P2 :action :hit-by :args {:player :P1 :hit-points 0}}
              {:to :* :action :dead :args {:player :P2}}]
             (:journal world1)))
      (is (= 0 (-> world1 (:scoring) (:P2))))
      (is (-> world1 (:arena) (:P1)))
      (is (nil? (-> world1 (:arena) (:P2)))))))
