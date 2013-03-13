(ns cassiel.cubewar.test.db
  "Database testing."
  (:use clojure.test
        slingshot.test)
  (:require (cassiel.cubewar [db :as db])))

(def TEST-DB (db/mem-db "test"))
;(def TEST-DB (db/file-db "test_hsqldb"))

(use-fixtures :each (fn [t]
                      (db/clear TEST-DB)
                      (t)))

(deftest basics
  (testing "initial DB state"
    (is (= 0 (db/num-users TEST-DB)))))

(deftest user-management
  (testing "add"
    (let [u (db/add-user TEST-DB "User" "Pass" 0)]
      (is (= u (db/lookup-id TEST-DB "User")))
      (is (nil? (db/lookup-id TEST-DB "Bogus")))
      (is (= 1 (db/num-users TEST-DB)))
      (is (thrown+? [:type ::db/DUPLICATE-USER]
                    (db/add-user TEST-DB "User" "Pass2" 0))))))

(deftest basic-authentication
  (testing "auth tests"
    (let [u (db/add-user TEST-DB "User" "Pass" 0)]
      (is (= u (db/authenticate TEST-DB "User" "Pass")))

      (is (thrown+? [:type ::db/AUTH-FAILED]
                    (db/authenticate TEST-DB "User" "Wrong-Pass")))
      (is (thrown+? [:type ::db/AUTH-FAILED]
                    (db/authenticate TEST-DB "Bogus" "Fogus"))))

    (testing "lookup"
      (let [u (db/add-user TEST-DB "User1" "Pass" 0xFF0000)]
        (is (= "User1" (:user (db/lookup-user TEST-DB u))))
        (is (= 0xFF0000 (:rgb (db/lookup-user TEST-DB u))))
        (is (= "User1" (:user (db/lookup-user TEST-DB
                                             (db/authenticate TEST-DB "User1" "Pass")))))))))