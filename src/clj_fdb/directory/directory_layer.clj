(ns clj-fdb.directory.directory-layer
  (:import com.apple.foundationdb.directory.DirectoryLayer
           com.apple.foundationdb.subspace.Subspace))


(defn create-directory-layer
  ([]
   (DirectoryLayer.))
  ([^Boolean allow-manual-prefixes]
   (DirectoryLayer. allow-manual-prefixes))
  ([^Subspace node-subspace ^Subspace content-subspace]
   (DirectoryLayer. node-subspace content-subspace))
  ([^Subspace node-subspace ^Subspace content-subspace ^Boolean allow-manual-prefixes]
   (DirectoryLayer. node-subspace content-subspace allow-manual-prefixes)))


(defn get-directory-layer
  [^DirectoryLayer dl]
  (.getDirectoryLayer dl))


(defn get-default-directory-layer
  []
  (DirectoryLayer/getDefault))


(defn get-path
  [^DirectoryLayer dl]
  (.getPath dl))


(defn create
  [^DirectoryLayer dl ts path layer prefix]
  (.create dl ts path layer prefix))
