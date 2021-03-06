(ns clj-fdb.internal.util
  (:require [clj-fdb.core :as fc]
            [clj-fdb.FDB :as cfdb]
            [clj-fdb.tuple.tuple :as ftup]))

(let [alphabet (vec "abcdefghijklmnopqrstuvwxyz0123456789")]
  (defn rand-str
    "Generate a random string of length l"
    [l]
    (loop [n l res (transient [])]
      (if (zero? n)
        (apply str (persistent! res))
        (recur (dec n) (conj! res (alphabet (rand-int 36))))))))

(def ^:dynamic *test-prefix* nil)

(defn- clear-all-with-prefix
  "Helper fn to ensure sanity of DB"
  [prefix]
  (let [fdb (cfdb/select-api-version 510)
        rg (ftup/range (ftup/from prefix))]
    (with-open [db (cfdb/open fdb)]
      (fc/clear-range db rg))))

(defn test-fixture
  [test]
  (let [random-prefix (str "testcycle:" (rand-str 5))]
    (binding [*test-prefix* random-prefix]
      (test))
    (clear-all-with-prefix random-prefix)))
