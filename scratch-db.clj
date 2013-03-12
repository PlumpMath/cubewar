(ns user
  (:require (clojure.java [jdbc :as sql])
            (cassiel.cubewar [db :as db])))

(System/getProperty "user.home")

;; ---- JDBC ----

(def hsql-db {:subprotocol "hsqldb"
              :subname (str "file:" (System/getProperty "user.home") "/foobledb")
              :user "SA"
              :password ""})

(sql/with-connection hsql-db (sql/create-table
                              :footfall
                              [:id "INTEGER" "GENERATED BY DEFAULT AS IDENTITY (START WITH 1) PRIMARY KEY"]
                              [:sample_date "DATE"]
                              [:exhibition "varchar(255)"]
                              ))

(sql/with-connection hsql-db
  (sql/insert-records :footfall
                      {:exhibition "Exhibition 1"}
                      {:exhibition "Exhibition 2"}))

(sql/with-connection hsql-db (sql/create-table
                              :test
                              [:id "INTEGER" "GENERATED ALWAYS AS IDENTITY(START WITH 1)"]
                              [:footfall_id "INTEGER" "FOREIGN KEY REFERENCES footfall(id) ON DELETE CASCADE"]
                              [:exhibition "varchar(255)"]))


(sql/with-connection hsql-db
  (sql/drop-table :footfall))

(sql/with-connection hsql-db
  (sql/drop-table :test))

(sql/with-connection hsql-db
  (sql/with-query-results rows
    ["SELECT * FROM footfall"]
    (doall rows)))

;; --- Using package.

(def db (db/file-db "/test_hsqldb"))

(db/clear db)

(db/authenticate db "User" "Pass")

(db/num-users db)
