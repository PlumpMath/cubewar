(ns cassiel.cubewar.test.tournament
  "Test tournament machinery."
  (:use clojure.test
        midje.sweet
        slingshot.test)
  (:require (cassiel.cubewar [manifest :as m]
                             [players :as pl]
                             [state-navigation :as n]
                             [view :as v]
                             [tournament :as tm]
                             [db :as db]
                             [tools :as t])))

(defn- rgb-hack [w _] m/DEFAULT-RGB)
(defn- banner-hack [w _] m/MOCK-BANNER)

(defn- mock-player
  [name]
  {:name name
   :rgb m/DEFAULT-RGB
   :banner m/MOCK-BANNER})

(def TEST-DB (db/mem-db "test"))

(deftest basics
  (testing "journalise"
    (is (= {:journal [{:foo 99}]}
           (tm/journalise {} {:foo 99})))
    (is (= {:journal [{:to m/BROADCAST :action :alert :args {:message "BOO!"}}]}
           (tm/journalise {} (tm/broadcast-alert "BOO!"))))
    (is (= {:journal [1 2 3]}
           (tm/journalise {} 1 2 3))))

  (testing "occupied"
    (is (tm/occupied {:P (pl/gen-player [0 0 0])} [0 0 0]))
    (is (not (tm/occupied {} [0 0 0])))
    (is (not (tm/occupied {:P (pl/gen-player [0 0 1])} [0 0 0])))
    (is (not (tm/occupied {:P (pl/gen-player [0 0 0])} [1 0 0]))))

  (testing "find-space"
    (is (tm/find-space {}))
    (let [arena {:P1 identity}
          pos (tm/find-space arena)]
      (is pos)
      (is (not= ((:P1 arena) [0 0 0])
                pos)))))

(defn- overview-entry
  "Journal entry for overview."
  [world]
  {:to m/OBSERVER-NAME
   :action :overview
   :args (v/dict-format-3D (tm/add-overview-attrs world
                                                  (v/look-arena (:arena world))))})


(with-state-changes [(before :facts (db/initialize TEST-DB))]
  (deftest game-state
    (testing "cannot move if not in arena"
      (let [world {:arena {} :scoring {:P 0}}]
        (is (= [{:to :P :action :error :args {:message "not currently in play"}}]
               (-> world (tm/move :P :forward) (:journal))))))

    (testing "cannot fire if not in arena"
      (let [world {:arena {} :scoring {:P 0}}]
        (is (= [{:to :P :action :error :args {:message "not currently in play"}}]
               (-> world (tm/fire :P) (:journal))))))

    (testing "attach doesn't put player in arena"
      (let [world (-> {:arena {} :scoring {} :db TEST-DB}
                      (tm/attach :P))]
        (is (:P (:scoring world)))
        (is (nil? (:P (:arena world))))))

    (testing "attach when in play/scoring doesn't overwrite"
      (let [arena (-> {}
                      (pl/add-player :P (pl/gen-player [0 0 0])))
            world (-> {:arena arena
                       :scoring {:P 7}
                       :rgb-fn rgb-hack
                       :banner-fn banner-hack
                       :db TEST-DB}
                      (tm/attach :P))]
        (is (:P (:arena world)))
        (is (= 7 (:P (:scoring world))))))

    (testing "player in arena not moved on round start"
      (let [arena (-> {}
                      (pl/add-player :P1 (pl/gen-player [2 2 2])))
            world (tm/start-round {:arena arena
                                   :scoring {:P2 0}
                                   :rgb-fn rgb-hack
                                   :banner-fn banner-hack})]
        (is (-> world (:arena) (:P2)))
        (is (= [2 2 2]
               ((-> world (:arena) (:P1)) [0 0 0])))))

    (testing "round start puts two players into arena"
      (let [world (-> {:arena {}
                       :scoring {:P1 0 :P2 0}
                       :rgb-fn rgb-hack
                       :banner-fn banner-hack}
                      (tm/start-round))]
        (is (:P1 (:arena world)))
        (is (:P2 (:arena world)))))

    (testing "round start fails if unsufficient players"
      (let [world {:arena {:P1 (pl/gen-player [0 0 0])} :scoring {}}]
        (is (thrown+? [:type ::tm/NOT-ENOUGH-PLAYERS]
                      (tm/start-round world)))))

    ;; We shouldn't start a round unless the arena is empty.
    #_ (testing "can start round with one active player"
         (let [world {:arena {:P1 (pl/gen-player [0 0 0])}
                      :scoring {:P1 0 :P2 0}}]
           (is (= 2 (count (-> world (tm/start-round) (:arena)))))))

    (facts "can start round manually, with journal generated"
           (let [world (tm/start-round  {:arena {}
                                         :scoring {:P1 0 :P2 0}
                                         :rgb-fn rgb-hack
                                         :banner-fn banner-hack})]
             (fact "occupancy"
                   (count (:arena world)) => 2)

             (fact "initial view"
                   (:journal world)
                   => [{:to :P1 :action :start-round :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                                            :x1 {:y0 {:player (mock-player :P1)}
                                                                 :y1 :empty
                                                                 :y2 :empty}
                                                            :x2 {:y0 :empty :y1 :empty :y2 :empty}}}
                       {:to :P2 :action :start-round :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                                            :x1 {:y0 {:player (mock-player :P2)}
                                                                 :y1 :empty
                                                                 :y2 :empty}
                                                            :x2 {:y0 :empty :y1 :empty :y2 :empty}}}
                       (overview-entry world)])))

    (testing "attach doesn't start a game when not enough players"
      (let [world {:arena {}
                   :scoring {}
                   :db TEST-DB}
            world' (tm/attach world :P2)]
        (is (empty? (:arena world')))))

    (facts "attach starts a round when enough players"
           (let [world {:arena {}
                        :scoring {:P1 0}
                        :rgb-fn rgb-hack
                        :banner-fn banner-hack
                        :db TEST-DB}
                 world' (tm/attach world :P2)]
             (fact (:journal world')
                   => [{:to :P2
                        :action :welcome
                        :args {:league {:_0 {:name "Demo1" :played 0 :won 0}
                                        :_1 {:name "Demo2" :played 0 :won 0}
                                        :_2 {:name "Demo3" :played 0 :won 0}
                                        :_3 {:name "Observer" :played 0 :won 0}}}}
                       {:to :P1 :action :start-round :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                                            :x1 {:y0 {:player (mock-player :P1)}
                                                                 :y1 :empty
                                                                 :y2 :empty}
                                                            :x2 {:y0 :empty :y1 :empty :y2 :empty}}}
                       {:to :P2 :action :start-round :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                                            :x1 {:y0 {:player (mock-player :P2)}
                                                                 :y1 :empty
                                                                 :y2 :empty}
                                                            :x2 {:y0 :empty :y1 :empty :y2 :empty}}}
                       (overview-entry world')])
             (fact (count (:arena world')) => 2)))

    (testing "detach removes from arena and standby."
      (let [arena (-> {}
                      (pl/add-player :P (pl/gen-player [0 0 0])))
            world (tm/detach {:arena arena :scoring {:P 10}} :P)]
        (is (nil? (:P (:arena world))))
        (is (nil? (:P (:scoring world))))))

    (testing "detach does not leave too few players in arena"
      (let [arena (-> {}
                      (pl/add-player :P1 (pl/gen-player [0 0 0]))
                      (pl/add-player :P2 (pl/gen-player [0 0 1])))
            world (tm/detach {:arena arena :scoring {:P1 10 :P2 10}} :P1)]
        (is (nil? (:P2 (:arena world))))
        (is (= [{:to m/BROADCAST :action :end-round}
                {:to m/BROADCAST :action :alert :args {:message "round over (no winner)"}}]
               (:journal world)))))

    (fact "a detaching player refreshes the overview and the views for remaining players"
          (let [arena (-> {}
                          (pl/add-player :P1 (pl/gen-player [0 0 0]))
                          (pl/add-player :P2 (pl/gen-player [0 0 1]))
                          (pl/add-player :P3 (pl/gen-player [0 0 2])))
                world (tm/detach {:arena arena
                                  :rgb-fn rgb-hack
                                  :banner-fn banner-hack} :P2)]
            (:journal world)
            =>
            [{:to :P1
              :action :view
              :view {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                     :x1 {:y0 {:player {:name :P1}} :y1 :wall :y2 :wall}
                     :x2 {:y0 :wall :y1 :wall :y2 :wall}}}
             {:to :P3
              :action :view
              :view {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                     :x1 {:y0 {:player {:name :P3}} :y1 :wall :y2 :wall}
                     :x2 {:y0 :wall :y1 :wall :y2 :wall}}}
             (overview-entry world)]))))

(facts "fire-journal"
  (let [arena (-> {}
                  (pl/add-player :P (pl/gen-player [0 0 0])))
        world {:arena arena
               :scoring {:P 10}}]
    (fact "miss"
          (-> world (tm/fire :P) (:journal))
          =>
          [{:to :P :action :miss}]))

  (let [arena (-> {}
                  (pl/add-player :P1 (pl/gen-player [0 0 0]))
                  (pl/add-player :P2 (pl/gen-player [0 1 0])))
        world {:arena arena
               :scoring {:P1 10 :P2 10}
               :rgb-fn rgb-hack
               :banner-fn banner-hack}]
    (fact "hit"
          (-> world (tm/fire :P1) (:journal))
          =>
          [{:to :P1 :action :hit :args {:player {:name :P2}}}
           {:to :P2 :action :hit-by :args {:player {:name :P1}
                                           :hit-points 9}}
           {:action :view
            :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                   :x1 {:y0 {:player (mock-player :P1)}
                        :y1 {:player (mock-player :P2)}
                        :y2 :empty}
                   :x2 {:y0 :empty :y1 :empty :y2 :empty}}
            :to :P1}
           {:action :view
            :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                   :x1 {:y0 {:player (mock-player :P2)}
                        :y1 :empty
                        :y2 :wall}
                   :x2 {:y0 :empty :y1 :empty :y2 :wall}}
            :to :P2}
           {:to m/OBSERVER-NAME
            :action :overview
            :args {:z0 {:x0 {:y0 {:player (mock-player :P1)}
                             :y1 {:player (mock-player :P2)}
                             :y2 :empty}
                        :x1 {:y0 :empty :y1 :empty :y2 :empty}
                        :x2 {:y0 :empty :y1 :empty :y2 :empty}}
                   :z1 {:x0 {:y0 :empty :y1 :empty :y2 :empty}
                        :x1 {:y0 :empty :y1 :empty :y2 :empty}
                        :x2 {:y0 :empty :y1 :empty :y2 :empty}}
                   :z2 {:x0 {:y0 :empty :y1 :empty :y2 :empty}
                        :x1 {:y0 :empty :y1 :empty :y2 :empty}
                        :x2 {:y0 :empty :y1 :empty :y2 :empty}}}}])))

(deftest sanity-check
  (testing "rogue in arena"
    (let [arena (-> {}
                    (pl/add-player :P1 (pl/gen-player [0 0 0]))
                    (pl/add-player :P2 (pl/gen-player [0 1 0])))
          world {:arena arena :scoring {:P1 10}}]

      (is (thrown+? [:type ::tm/NOT-IN-SYSTEM]
                    (-> world (tm/fire :P1)))))))

(deftest move-journal
  (testing "forward OK, one player"
    (let [arena (-> {}
                    (pl/add-player :P (pl/gen-player [0 0 0])))
          world {:arena arena :scoring nil :rgb-fn rgb-hack :banner-fn banner-hack}
          world' (-> world
                     (tm/move :P :forward))]
      (is (= [{:to :P :action :view :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                           :x1 {:y0 {:player (assoc (mock-player :P)
                                                               :manoeuvre :forward
                                                               )}
                                                :y1 :empty
                                                :y2 :wall}
                                           :x2 {:y0 :empty :y1 :empty :y2 :wall}}}
              (overview-entry world')]
             (:journal world')))))

  (testing "forward OK, two players"
    (let [arena (-> {}
                    (pl/add-player :P1 (pl/gen-player [0 0 0]))
                    (pl/add-player :P2 (pl/gen-player [1 0 0])))
          world {:arena arena :scoring nil :rgb-fn rgb-hack :banner-fn banner-hack}
          world' (-> world
                     (tm/move :P1 :forward))]
      ;; The order of these journal items is implementation-dependent (we reduce over
      ;; the set of active players). TODO: we could sort them first.
      (is (= [{:to :P1 :action :view :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                            :x1 {:y0 {:player (assoc (mock-player :P1)
                                                                :manoeuvre :forward)}
                                                 :y1 :empty
                                                 :y2 :wall}
                                            :x2 {:y0 :empty :y1 :empty :y2 :wall}}}
              {:to :P2 :action :view :args {:x0 {:y0 :empty
                                                 :y1 {:player (assoc (mock-player :P1)
                                                                :manoeuvre :forward)}
                                                 :y2 :empty}
                                            :x1 {:y0 {:player (mock-player :P2)}
                                                 :y1 :empty :y2 :empty}
                                            :x2 {:y0 :empty :y1 :empty :y2 :empty}}}
              (overview-entry world')]
             (:journal world')))))

  (testing "forward blocked"
    (let [arena (-> {}
                    (pl/add-player :P (pl/gen-player [0 2 0])))
          world {:arena arena :scoring nil}]
      (is (= [{:to :P :action :blocked}]
             (-> world
                 (tm/move :P :forward)
                 (:journal))))))

  (facts "asking for view"
         (fact "view 1"
               (let [arena (-> {}
                               (pl/add-player :P (pl/gen-player [0 0 0])))
                     world {:arena arena :scoring nil :rgb-fn rgb-hack :banner-fn banner-hack}
                     world' (-> world (tm/view :P))]
                 (:journal world')
                 =>
                 [{:to :P :action :view :args {:x0 {:y0 :wall :y1 :wall :y2 :wall }
                                               :x1 {:y0 {:player (mock-player :P)}
                                                    :y1 :empty
                                                    :y2 :empty}
                                               :x2 {:y0 :empty :y1 :empty :y2 :empty}}}])))

  (testing "yaw left"
    (let [arena (-> {}
                    (pl/add-player :P (pl/gen-player [0 0 0])))
          world {:arena arena :scoring nil :rgb-fn rgb-hack :banner-fn banner-hack}
          world' (-> world
                     (tm/move :P :yaw-left))]
      (is (= [{:to :P :action :view :args {:x0 {:y0 :wall :y1 :wall :y2 :wall }
                                           :x1 {:y0 {:player (assoc (mock-player :P)
                                                               :manoeuvre :yaw-left)}
                                                :y1 :wall
                                                :y2 :wall}
                                           :x2 {:y0 :empty :y1 :wall :y2 :wall}}}
              (overview-entry world')]
             (:journal world'))))))

(defn setup-db
  []
  (-> TEST-DB
      (db/clear)
      (db/add-user ":P1" "Pass" 0)
      (db/add-user ":P2" "Pass" 0)
      (db/add-user ":P3" "Pass" 0)))

(with-state-changes [(before :facts (setup-db))]
  (facts "basic-round-scoring"
         ;; We can't really get an empty cube, but:
         (let [world {:arena {} :scoring {:P 10}}
               {j :journal} (tm/fire world :P)]
           (fact "in scoring but not in round"
                 j => [{:to :P :action :error :args {:message "not currently in play"}}]))

         (let [arena (-> {}
                         (pl/add-player :P1 (pl/gen-player [0 0 0]))
                         (pl/add-player :P2 (pl/gen-player [0 1 0])))
               world {:arena arena
                      :scoring {:P1 10 :P2 10}
                      :rgb-fn rgb-hack
                      :banner-fn banner-hack
                      :db TEST-DB}
               world' (tm/fire world :P1)]
           (fact
            (:journal world')
            =>
            [{:to :P1 :action :hit :args {:player {:name :P2}}}
             {:to :P2 :action :hit-by :args {:player {:name :P1}
                                             :hit-points 9}}
             {:to :P1 :action :view :args {:x0 {:y2 :wall :y1 :wall :y0 :wall}
                                           :x1 {:y2 :empty
                                                :y1 {:player (mock-player :P2)}
                                                :y0 {:player (mock-player :P1)}}
                                           :x2 {:y2 :empty :y1 :empty :y0 :empty}}}
             {:to :P2 :action :view :args {:x0 {:y2 :wall :y1 :wall :y0 :wall}
                                           :x1 {:y2 :wall
                                                :y1 :empty
                                                :y0 {:player (mock-player :P2)}}
                                           :x2 {:y2 :wall :y1 :empty :y0 :empty}}}
             (overview-entry world')])

           (fact (:P2 (:scoring world')) => 9))

         (facts "knockout"
                (let [arena (-> {}
                                (pl/add-player :P1 (pl/gen-player [0 0 0]))
                                (pl/add-player :P2 (pl/gen-player [0 1 0]))
                                (pl/add-player :P3 (pl/gen-player [2 2 2])))
                      world {:arena arena
                             :scoring {:P1 1 :P2 1 :P3 10}
                             :rgb-fn rgb-hack
                             :banner-fn banner-hack
                             :db TEST-DB}
                      world' (tm/fire world :P1)]
                  (fact "journal"
                        (:journal world')
                        =>
                        [{:to :P1 :action :hit :args {:player {:name :P2}}}
                         {:to :P2 :action :hit-by :args {:player {:name :P1}
                                                         :hit-points 0}}
                         {:to :P2 :action :you-dead}
                         {:to m/BROADCAST
                          :action :dead
                          :args {:player {:name :P2}
                                 :league {:_0 {:name ":P2" :played 1 :won 0}
                                          :_1 {:name ":P1" :played 0 :won 0}
                                          :_2 {:name ":P3" :played 0 :won 0}}}}
                         {:action :view :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                               :x1 {:y0 {:player (mock-player :P1)} :y1 :empty :y2 :empty}
                                               :x2 {:y0 :empty :y1 :empty :y2 :empty}}
                          :to :P1}
                         {:action :view :args {:x0 {:y0 :empty :y1 :wall :y2 :wall}
                                               :x1 {:y0 {:player (mock-player :P3)} :y1 :wall :y2 :wall}
                                               :x2 {:y0 :wall :y1 :wall :y2 :wall}}
                          :to :P3}
                         (overview-entry world')])
                  (fact "score"
                        (-> world' (:scoring) (:P2)) => 0)
                  (fact ":P1 in play"
                        (:P1 (:arena world')) => truthy)
                  (fact ":P2 removed"
                        (:P2 (:arena world')) => falsey)
                  (fact ":P3 in play"
                        (:P3 (:arena world')) => truthy)))

         (facts "restart OK"
                (let [arena  {}
                      world {:arena arena
                             :scoring {:P1 1 :P2 1 :P3 10}
                             :rgb-fn rgb-hack
                             :banner-fn banner-hack}
                      world' (tm/kick world)]
                  (fact "restart journal"
                        (:journal world')
                        =>
                        [{:to :P1
                          :action :start-round
                          :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                 :x1 {:y0 {:player (mock-player :P1)}
                                      :y1 :empty
                                      :y2 :empty}
                                 :x2 {:y0 :empty :y1 :empty :y2 :empty}}}
                         {:to :P2
                          :action :start-round
                          :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                 :x1 {:y0 {:player (mock-player :P2)}
                                      :y1 :empty
                                      :y2 :empty}
                                 :x2 {:y0 :empty :y1 :empty :y2 :empty}}}
                         {:to :P3
                          :action :start-round
                          :args {:x0 {:y0 :wall :y1 :wall :y2 :wall}
                                 :x1 {:y0 {:player (mock-player :P3)}
                                      :y1 :empty
                                      :y2 :empty}
                                 :x2 {:y0 :empty :y1 :empty :y2 :empty}}}
                         (overview-entry world')])
                  ;; Players have been put back into play:
                  (fact (-> world' (:arena) (:P1)) => truthy)
                  (fact (-> world' (:arena) (:P2)) => truthy)
                  (fact (-> world' (:arena) (:P3)) => truthy)))

         (facts "restart benign"
                (let [arena (-> {}
                                (pl/add-player :P1 (pl/gen-player [0 0 0]))
                                (pl/add-player :P2 (pl/gen-player [0 1 0])))
                      world {:arena arena
                             :scoring {:P1 1 :P2 1 :P3 10}
                             :journal []
                             :rgb-fn rgb-hack
                             :banner-fn banner-hack}
                      world' (tm/kick world)]
                  (fact "restart ignored"
                        (:journal world') => [])))

         (facts "knockout, end of round"
                (let [arena (-> {}
                                (pl/add-player :P1 (pl/gen-player [0 0 0]))
                                (pl/add-player :P2 (pl/gen-player [0 1 0])))
                      world {:arena arena
                             :scoring {:P1 1 :P2 1 :P3 10}
                             :rgb-fn rgb-hack
                             :banner-fn banner-hack
                             :db TEST-DB}
                      world' (tm/fire world :P1)]
                  (fact (:journal world')
                        => [{:to :P1 :action :hit :args {:player {:name :P2}}}
                            {:to :P2 :action :hit-by :args {:player {:name :P1}
                                                            :hit-points 0}}
                            {:to :P2 :action :you-dead}
                            {:to m/BROADCAST
                             :action :dead
                             :args {:player {:name :P2}
                                    :league {:_0 {:name ":P2" :played 1 :won 0}
                                             :_1 {:name ":P1" :played 0 :won 0}
                                             :_2 {:name ":P3" :played 0 :won 0}}}}
                            (overview-entry (assoc world' :arena {}))
                            {:to m/BROADCAST
                             :action
                             :end-round
                             :args {:league {:_0 {:name ":P1" :played 1 :won 1}
                                             :_1 {:name ":P2" :played 1 :won 0}
                                             :_2 {:name ":P3" :played 0 :won 0}}}}
                            {:to m/BROADCAST :action :alert :args {:message "round over, winner :P1"}}])))))
