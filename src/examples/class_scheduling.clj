(ns examples.class-scheduling
  (:require [byte-streams :as bs]
            [clj-fdb.core :as fc]
            [clj-fdb.transaction :as ftr]
            [clj-fdb.tuple :as ftup]
            [clojure.string :as cs]
            [clojure.tools.logging :as ctl])
  (:import [com.apple.foundationdb Database FDB Transaction TransactionContext]
           java.lang.IllegalArgumentException))

(defn available-classes
  "Returns a list of available classes."
  [^TransactionContext db]
  (let [ac (fc/get-range db
                         (-> "class"
                             ftup/from
                             ftup/range)
                         :keyfn (fn [k-ba]
                                  (-> k-ba
                                      ftup/from-bytes
                                      ftup/get-items
                                      second))
                         :valfn (fn [v-ba]
                                  (bs/convert v-ba Integer)))]
    (reduce-kv (fn [m k v]
                 (if (> v 0)
                   (assoc m k v)
                   m))
               ;; Sorting here just for aesthetic purposes, this is
               ;; not part of the orignal example.
               (sorted-map-by (fn [k1 k2]
                                (compare [(get ac k1) k1]
                                         [(get ac k2) k2])))
               ac)))

(defn signup-student
  "Signs up a student for a class."
  [^TransactionContext db ^String student-id ^String class-id]
  (ftr/run db
    (fn [^Transaction tr]
      (let [attendance-key (ftup/from "attends" class-id student-id)
            class-key (ftup/from "class" class-id)
            exists? (fc/get tr attendance-key)]

        (if exists?
          ;; Nothing to do if student is already signed up.
          (ctl/info (format "[%s] Hello %s! You are already signed up for this class!"
                            class-id
                            student-id))
          (let [seats-left (fc/get tr
                                   class-key
                                   :valfn (fn [v-ba]
                                            (bs/convert v-ba Integer)))]
            (if (> seats-left 0)
              (do (fc/set tr attendance-key (ftup/from ""))
                  (fc/set tr class-key (int (dec seats-left)))
                  (ctl/info (format "[%s] Welcome %s! You have been signed up for this class!"
                                    class-id
                                    student-id)))
              (throw (IllegalArgumentException.
                      (str "No seats remaining in class: " class-id))))))))))

(defn drop-student
  "Drops a student from a class"
  [^TransactionContext db ^String student-id ^String class-id]
  (ftr/run db
    (fn [^Transaction tr]
      (let [attendance-key (ftup/from "attends" class-id student-id)
            class-key (ftup/from "class" class-id)
            exists? (fc/get tr attendance-key)]

        (if exists?
          (let [seats-left (fc/get tr
                                   class-key
                                   :valfn (fn [v-ba]
                                            (bs/convert v-ba Integer)))]
            (ctl/info (format "[%s] Hey %s! Sorry to see you go!"
                              class-id
                              student-id))
            (fc/clear tr attendance-key)
            (fc/set tr class-key (int (inc seats-left))))
          ;; Nothing to do here, the student isn't signed up.
          (ctl/info (format "[%s] Hello %s! You aren't currently already signed up for this class!"
                            class-id
                            student-id)))))))

(defn- add-class
  "Used to populate the database's class list."
  [^TransactionContext db ^String classname ^Integer available-seats]
  (fc/set db (ftup/from "class" classname) (int available-seats)))

(defn- init-db
  "Helper function to initialize the db with a bunch of classnames"
  [^Database db classnames]
  (ftr/run db
    (fn [^Transaction tr]
      ;; Clear list of who attends which class
      (->> "attends"
           ftup/from
           ftup/range
           (ftr/clear-range tr))
      ;; Clear list of classes
      (->> "class"
           ftup/from
           ftup/range
           (ftr/clear-range tr))
      ;; Add list of classes as given to us
      (doseq [c classnames]
        (add-class tr c (int 100))))))

(defn- reset-class
  "Helper function to remove all attendees from a class and reset it.
  If `available-seats` is provided, we use that number as the new
  value of `available-seats`. If not, we set the value to the number
  of attendees in the class.

  *NOTE*: This is not part of the original example."
  ([^TransactionContext db class-id]
   (ftr/run db
     (fn [^Transaction tr]
       (let [attendance-range-key (ftup/from "attends" class-id)
             class-key (ftup/from "class" class-id)
             attendee-count (->> attendance-range-key
                                 ftup/range
                                 (fc/get-range tr)
                                 count)
             seats-left (fc/get tr
                                class-key
                                :valfn (fn [v-ba]
                                         (bs/convert v-ba Integer)))]
         (reset-class db class-id (+ attendee-count seats-left))))))
  ([^TransactionContext db class-id ^Integer available-seats]
   (ctl/info (format "[%s] Attempting to reset class. Available seats: %s"
                     class-id
                     available-seats))
   (ftr/run db
     (fn [^Transaction tr]
       (->> (ftup/from "attends" class-id)
            ftup/range
            (ftr/clear-range tr))
       (add-class tr class-id (int available-seats))))))

(comment
  ;; Create classes for fun and profit
  (let [fdb (FDB/selectAPIVersion 510)
        class-levels ["intro" "for dummies" "remedial"
                      "101" "201" "301"
                      "mastery" "lab" "seminar"]
        class-types ["chem" "bio" "cs"
                     "geometry" "calc" "alg" "film"
                     "music" "art" "dance"]
        class-times ["2:00" "3:00" "4:00"
                     "5:00" "6:00" "7:00"
                     "8:00" "9:00" "10:00"
                     "11:00" "12:00" "13:00"
                     "14:00" "15:00" "16:00"
                     "17:00" "18:00" "19:00"]
        classnames (for [le class-levels
                         ty class-types
                         ti class-times]
                     (cs/join " " [le ty ti]))]
    (with-open [db (.open fdb)]
      (init-db db classnames))))