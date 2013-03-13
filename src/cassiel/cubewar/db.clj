(ns cassiel.cubewar.db
  "Database wrapper."
  (:require (clojure.java [jdbc :as sql]))
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import [java.sql BatchUpdateException]
           [org.apache.commons.codec.digest DigestUtils]))

(defprotocol LEAGUE
  "Interface to DB representing a league."
  (clear [this] "Drop all tables and recreate.")
  (add-user [this user pass rgb] "Add a user with this password and display colour.")
  (lookup-id [this user] "Look up an ID from a username. (Probably not needed.)")
  (lookup-user [this id] "Look up user details as a map.")
  (authenticate [this user pass] "Authenticate (returning an ID), or return null.")
  (num-users [this] "Return number of users."))

(defn- crypt
  "SHA1 encryption. Not very smart (no salting), but this is only a game; this is
   really just to avoid password leakage from HSQLDB's database files."
  [string]
  (DigestUtils/sha1Hex string))

(def spec-template
  {:subprotocol "hsqldb"
   :user "SA"
   :password ""})

(defn- mem-db-spec
  "A specification for an in-memory database."
  [name]
  (assoc spec-template
    :subname
    (str "mem:" name)))

(defn- file-db-spec
  "A specification for a file-based database. Path is rooted in home directory;
   we add the initial separator. (So, best keep it as a single token.)"
  [path]
  (assoc spec-template
    :subname
    (str "file:"
         (System/getProperty "user.home")
         java.io.File/separator
         path
         #_ ";shutdown=true")))

(def ID [:id "INTEGER" "GENERATED BY DEFAULT AS IDENTITY (START WITH 1) PRIMARY KEY"])

(defn- make-db
  "Create a database from a spec."
  [db]
  (reify LEAGUE
    (clear [this]
      (sql/with-connection db
        (try+
         (sql/drop-table :Users)
         (catch BatchUpdateException _ this))

        (sql/create-table
         :Users
         ID
         [:Username "VARCHAR(255)" "NOT NULL"]
         [:Password "VARCHAR(255)" "NOT NULL"]
         [:RGB "INTEGER" "NOT NULL"])))

    (add-user [this user pass rgb]
      (if (lookup-id this user)
        (throw+ {:type ::DUPLICATE-USER})
        (sql/with-connection db
          (:id (sql/insert-record :Users {:Username user
                                          :Password (crypt pass)
                                          :RGB rgb})))))

    ;; TODO do we need this? (Only in `add-user` perhaps.)
    (lookup-id [this name]
      (sql/with-connection db
        (sql/with-query-results rows
          ["SELECT ID FROM Users WHERE Username = ?" name]
          (:id (first rows)))))

    (lookup-user [this id]
      (sql/with-connection db
        (sql/with-query-results rows
          [(str "SELECT Username AS User, "
                "       RGB AS RGB "
                "FROM Users WHERE ID = ?") id]
          (if (seq rows)
            (first rows)
            (throw+ {:type ::INTERNAL :source 'lookup-user})))))

    (authenticate [this name pass]
      (let [c (crypt pass)]
        (sql/with-connection db
          (sql/with-query-results rows
            ["SELECT ID FROM Users WHERE Username = ? AND Password = ?" name c]
            (if (seq rows)
              (:id (first rows))
              (throw+ {:type ::AUTH-FAILED}))))))

    (num-users [this]
      (sql/with-connection db
        (sql/with-query-results rows
          ["SELECT Count(*) AS C FROM Users"]
          (:c (first rows))))
      )))

(defn mem-db
  "Create a database in memory (for testing)."
  [name]
  (make-db (mem-db-spec name)))

(defn file-db
  "Create a database on disk."
  [path]
  (make-db (file-db-spec path)))
