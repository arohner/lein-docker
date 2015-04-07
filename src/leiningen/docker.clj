(ns leiningen.docker
  (:require [clojure.string :as str]
            [me.raynes.conch :as sh]
            [me.raynes.conch.low-level :as shll])
  (:import java.text.SimpleDateFormat
           java.util.Date))

(def time-pattern "YYYYMMdd-kkmmss")
(defn time-string []
  (->
   (SimpleDateFormat. time-pattern)
   (.format (Date.))))

(defn replace-snapshot
  "Replace a SNAPSHOT in version string with current date-time"
  [vs]
  (str/replace vs "SNAPSHOT" (time-string)))

(defn get-version [project]
  (-> project :version (replace-snapshot)))

(defn sh [& args]
  (let [p (apply shll/proc args)]
    (future (shll/stream-to-out p :out))
    (future (shll/stream-to-out p :err))
    (shll/exit-code p)))

(defn build* 
  [repo version]
  (assert repo "repo is required")
  (assert version "version is required")
  (let [tag (str repo ":" version)]
    (println "building" tag)
    (sh "docker" "build" "-t" tag ".")))

(defn build
  ([project args]
   (let [repo (-> project :docker :repo)
         version (get-version project)]
     (build* repo version))))

(defn parse-image-line [line]
  (-> line
      (str/split #" ")
      (->>
       (filter seq)
       ((fn [cols]
          (let [[repo tag image & args] cols]
            {:repo repo
             :tag tag
             :image image}))))))

(defn latest-version [repo]
  (some-> (sh/execute "docker" "images")
          (str/split #"\n")
          (rest)
          (->>
           (map parse-image-line)
           (first)
           :tag)))

(defn push*
  [repo version]
  (assert repo "repo is required")
  (assert version "version is required")
  (let [tag (str repo ":" version)]
    (println "pushing" tag)
    (sh "docker" "push" tag)))

(defn project-repo [project]
  (-> project :docker :repo))

(defn push
  ([project args]
   (let [repo (-> project :docker :repo)
         arg-vers (first args)
         _ (println "arg-vers=" arg-vers)
         version (cond
                   (or (nil? arg-vers) (= ":latest" arg-vers)) (latest-version repo)
                   :else arg-vers)]
     (push* repo version))))

(defn docker
  [project & args]
  (let [cmd (first args)]
    (condp = cmd
      "build" (build project (rest args))
      "push" (push project (rest args)))))
