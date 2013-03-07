(ns cassiel.cubewar.test.tournament
  "Test tournament machinery."
  (:use clojure.test
        slingshot.test)
  (:require (cassiel.cubewar [players :as pl]
                             [state-navigation :as n]
                             [tournament :as t])))

(deftest basics
  (testing "occupied"
    (is (t/occupied {:P (pl/gen-player [0 0 0])} [0 0 0]))
    (is (not (t/occupied {} [0 0 0])))
    (is (not (t/occupied {:P (pl/gen-player [0 0 1])} [0 0 0])))
    (is (not (t/occupied {:P (pl/gen-player [0 0 0])} [1 0 0]))))

  (testing "find-space"
    (is (t/find-space {}))
    (let [arena {:P1 identity}
          pos (t/find-space arena)]
      (is pos)
      (is (not= ((:P1 arena) [0 0 0])
                pos)))))

(deftest game-state
  (testing "cannot move if not in arena"
    (let [world {:arena {} :scoring {:P 0}}]
      (is (= [{:to :P :action :error :args {:message "not currently in play"}}]
             (-> world (t/move :P :forward) (:journal))))))

  (testing "cannot fire if not in arena"
    (let [world {:arena {} :scoring {:P 0}}]
      (is (= [{:to :P :action :error :args {:message "not currently in play"}}]
             (-> world (t/fire :P) (:journal))))))

  (testing "attach doesn't put player in arena"
    (let [world (-> {:arena {} :scoring {}}
                    (t/attach :P))]
      (is (:P (:scoring world)))
      (is (nil? (:P (:arena world))))))

  (testing "attach when in play/scoring doesn't overwrite"
    (let [arena (-> {}
                    (pl/add-player :P (pl/gen-player [0 0 0])))
          world (-> {:arena arena :scoring {:P 7}}
                    (t/attach :P))]
      (is (:P (:arena world)))
      (is (= 7 (:P (:scoring world))))))

  (testing "player in arena not moved on round start"
    (let [arena (-> {}
                    (pl/add-player :P1 (pl/gen-player [2 2 2])))
          world (t/start-round {:arena arena :scoring {:P2 0}})]
      (is (-> world (:arena) (:P2)))
      (is (= [2 2 2]
             ((-> world (:arena) (:P1)) [0 0 0])))))

  (testing "round start puts two players into arena"
    (let [world (-> {:arena {} :scoring {:P1 0 :P2 0}}
                    (t/start-round))]
      (is (:P1 (:arena world)))
      (is (:P2 (:arena world)))))

  (testing "round start does not leave a single player in the arena"
    (let [world {:arena {} :scoring {:P1 0}}]
      (is (thrown+? [:type ::t/NOT-ENOUGH-PLAYERS]
                    (t/start-round world)))))

  (testing "can start round with one active player"
    (let [world {:arena {:P1 (pl/gen-player [0 0 0])}
                 :scoring {:P2 0}}]
      (is (= 2 (count (-> world (t/start-round) (:arena)))))))

  (testing "detach removes from arena and standby."
    (let [arena (-> {}
                    (pl/add-player :P (pl/gen-player [0 0 0])))
          world (t/detach {:arena arena :scoring {:P 10}} :P)]
      (is (nil? (:P (:arena world))))
      (is (nil? (:P (:scoring world))))))

  ;; TEST: detach when in standby and also when in play.
  )

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

      (is (thrown+? [:type ::t/NOT-IN-SYSTEM]
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
                 (t/move :P :forward)
                 (:journal))))))

  (testing "forward blocked"
    (let [arena (-> {}
                    (pl/add-player :P (pl/gen-player [0 2 0])))
          world {:arena arena :scoring nil}]
      (is (= [{:to :P :action :blocked}]
             (-> world
                 (t/move :P :forward)
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
                 (t/move :P :yaw-left)
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
