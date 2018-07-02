(ns clj-fdb.core-test
  (:require [byte-streams :as bs]
            [clj-fdb.core :as fc]
            [clj-fdb.FDB :as cfdb]
            [clj-fdb.internal.util :as u]
            [clj-fdb.range :as frange]
            [clj-fdb.transaction :as ftr]
            [clj-fdb.tuple.tuple :as ftup]
            [clojure.test :refer :all]
            [clj-fdb.subspace.subspace :as fsubspace])
  (:import com.apple.foundationdb.Transaction))

(use-fixtures :each u/test-fixture)

(deftest test-get-set
  (testing "Test the best-case path for `fc/set` and `fc/get`"
    (let [k (ftup/from u/*test-prefix* "foo")
          v (int 1)]
      (let [fdb (cfdb/select-api-version 510)]
        (with-open [db (cfdb/open fdb)]
          (fc/set db k v))
        (with-open [db (cfdb/open fdb)]
          (is (= (fc/get db k :valfn #(bs/convert %1 Integer))
                 v)))))))

(deftest test-get-non-existent-key
  (testing "Test that `fc/get` on a non-existent key returns `nil`"
    (let [fdb (cfdb/select-api-version 510)
          k (ftup/from u/*test-prefix* "non-existent")]
      (with-open [db (cfdb/open fdb)]
        (is (nil? (fc/get db k)))))))

(deftest test-clear-key
  (testing "Test the best-case path for `fc/clear`"
    (let [fdb (cfdb/select-api-version 510)
          k (ftup/from u/*test-prefix* "foo")
          v (int 1)]
      (with-open [db (cfdb/open fdb)]
        (fc/set db k v)
        (is (= (fc/get db k :valfn #(bs/convert %1 Integer))
               v))
        (fc/clear db k)
        (is (nil? (fc/get db k)))))))

(deftest test-get-range
  (testing "Test the best-case path for `fc/get-range`. End is exclusive."
    (let [fdb (cfdb/select-api-version 510)
          input-keys ["bar" "car" "foo" "gum"]
          begin      (ftup/pack (ftup/from u/*test-prefix* "b"))
          end        (ftup/pack (ftup/from u/*test-prefix* "g"))
          rg         (frange/range begin end)
          v          (int 1)
          expected-map {"bar" v "car" v "foo" v}]
      (with-open [db (cfdb/open fdb)]
        (ftr/run db
          (fn [^Transaction tr]
            (doseq [k input-keys]
              (let [k (ftup/from u/*test-prefix* k)]
                (fc/set tr k v)))))

        (is (= (fc/get-range db rg
                             :keyfn (comp second ftup/get-items ftup/from-bytes)
                             :valfn #(bs/convert %1 Integer))
               expected-map))))))

(deftest test-clear-range
  (testing "Test the best-case path for `fc/clear-range`. End is exclusive."
    (let [fdb (cfdb/select-api-version 510)
          input-keys ["bar" "car" "foo" "gum"]
          begin      (ftup/pack (ftup/from u/*test-prefix* "b"))
          end        (ftup/pack (ftup/from u/*test-prefix* "g"))
          rg         (frange/range begin end)
          v          (int 1)
          excluded-k "gum"]
      (with-open [db (cfdb/open fdb)]
        (ftr/run db
          (fn [^Transaction tr]
            (doseq [k input-keys]
              (let [k (ftup/from u/*test-prefix* k)]
                (fc/set tr k v)))))
        (fc/clear-range db rg)

        (is (= (fc/get db (ftup/from u/*test-prefix* excluded-k)
                       :valfn #(bs/convert % Integer))
               v))))))

(deftest test-get-set-subspaced-key
  (testing "Get/Set Subspaced Key using empty Tuple"
    (let [fdb (cfdb/select-api-version 510)
          random-prefixed-tuple (ftup/from u/*test-prefix* "subspace" (u/rand-str 5))
          prefixed-subspace (fsubspace/create-subspace random-prefixed-tuple)]
      (with-open [db (cfdb/open fdb)]
        (fc/set-subspaced-key db
                              prefixed-subspace
                              (ftup/from)
                              "value")
        (is (= (fc/get-subspaced-key db
                                     prefixed-subspace
                                     (ftup/from)
                                     :valfn #(bs/convert % String))
               "value"))
        (fc/set-subspaced-key db
                              prefixed-subspace
                              (ftup/from)
                              "New value")
        (is (= (fc/get-subspaced-key db
                                     prefixed-subspace
                                     (ftup/from)
                                     :valfn #(bs/convert % String))
               "New value")))))
  (testing "Get/Set Subspaced Key using non-empty Tuple"
    (let [fdb (cfdb/select-api-version 510)
          random-prefixed-tuple (ftup/from u/*test-prefix* "subspace" (u/rand-str 5))
          prefixed-subspace (fsubspace/create-subspace random-prefixed-tuple)]
      (with-open [db (cfdb/open fdb)]
        (fc/set-subspaced-key db
                              prefixed-subspace
                              (ftup/from "a")
                              "value")
        (= (fc/get-subspaced-key db
                                 prefixed-subspace
                                 (ftup/from "a")
                                 :valfn #(bs/convert % String))
           "value"))))
  (testing "Get non-existent Subspaced Key"
    (let [fdb (cfdb/select-api-version 510)
          random-prefixed-tuple (ftup/from u/*test-prefix* "subspace" (u/rand-str 5))
          prefixed-subspace (fsubspace/create-subspace random-prefixed-tuple)]
      (with-open [db (cfdb/open fdb)]
        (is (nil? (fc/get-subspaced-key db
                                        prefixed-subspace
                                        (ftup/from "a")
                                        :valfn #(bs/convert % String))))))))

(deftest test-clear-subspaced-key
  (testing "Clear subspaced key"
    (let [fdb (cfdb/select-api-version 510)
          random-prefixed-tuple (ftup/from u/*test-prefix* "subspace" (u/rand-str 5))
          prefixed-subspace (fsubspace/create-subspace random-prefixed-tuple)]
      (with-open [db (cfdb/open fdb)]
        (fc/set-subspaced-key db
                              prefixed-subspace
                              (ftup/from)
                              "value")
        (is (= (fc/get-subspaced-key db
                                     prefixed-subspace
                                     (ftup/from)
                                     :valfn #(bs/convert % String))
               "value"))
        (fc/clear-subspaced-key db
                                prefixed-subspace
                                (ftup/from))
        (is (nil? (fc/get-subspaced-key db
                                        prefixed-subspace
                                        (ftup/from)
                                        :valfn #(bs/convert % String))))))))

(deftest test-get-subspaced-range
  (testing "Get subspaced range"
    (let [fdb (cfdb/select-api-version 510)
          random-prefixed-tuple (ftup/from u/*test-prefix* "subspace" (u/rand-str 5))
          prefixed-subspace (fsubspace/create-subspace random-prefixed-tuple)
          input-keys ["bar" "car" "foo" "gum"]
          v "10"
          expected-map {"bar" v "car" v "foo" v "gum" v}]
      (with-open [db (cfdb/open fdb)]
        (doseq [k input-keys]
          (fc/set-subspaced-key db prefixed-subspace (ftup/from k) v))
        (is (= (fc/get-subspaced-range db prefixed-subspace (ftup/from)
                                       :keyfn (comp last ftup/get-items ftup/from-bytes)
                                       :valfn #(bs/convert % String))
               expected-map))))))

(deftest test-clear-subspaced-range
  (testing "Clear subspaced range"
    (let [fdb (cfdb/select-api-version 510)
          random-prefixed-tuple (ftup/from u/*test-prefix* "subspace" (u/rand-str 5))
          prefixed-subspace (fsubspace/create-subspace random-prefixed-tuple)
          input-keys ["bar" "car" "foo" "gum"]
          v "10"
          expected-map {"bar" v "car" v "foo" v "gum" v}]
      (with-open [db (cfdb/open fdb)]
        (doseq [k input-keys]
          (fc/set-subspaced-key db prefixed-subspace (ftup/from k) v))
        (is (= (fc/get-subspaced-range db prefixed-subspace (ftup/from)
                                       :keyfn (comp last ftup/get-items ftup/from-bytes)
                                       :valfn #(bs/convert % String))
               expected-map))
        (fc/clear-subspaced-range db prefixed-subspace)
        (is (nil? (fc/get-subspaced-key db prefixed-subspace (ftup/from)
                                        :valfn #(bs/convert % String))))))))
