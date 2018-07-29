(ns clj-fdb.directory.directory-subspace
  (:import com.apple.foundationdb.directory.DirectorySubspace))


(defn create
  [ts subpath other-layer prefix]
  (.create ts subpath other-layer prefix))

