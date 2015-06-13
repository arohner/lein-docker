(ns leiningen.docker
  (:require [clojure.string :as str]
            [clojure.core.strint :refer (<<)]
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

(defn sh!
  "Same as sh, throw on error"
  [& args]
  (let [resp (apply sh args)]
    (if (not= 0 resp)
      (throw (Exception. (str args "returned" resp))))))

(defn build*
  [repo version]
  (assert repo "repo is required")
  (assert version "version is required")
  (let [tag (str repo ":" version)]
    (println "building" tag)
    (sh! "docker" "build" "-t" tag ".")))

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
    (sh! "docker" "push" tag)))

(defn project-repo [project]
  (-> project :docker :repo))

(defn push
  ([project args]
   (let [repo (-> project :docker :repo)
         arg-vers (first args)
         version (cond
                   (or (nil? arg-vers) (= ":latest" arg-vers)) (latest-version repo)
                   :else arg-vers)]
     (push* repo version))))

(defn sh-trampoline
  "Abuse trampoline functionality to run arbitrary shell, rather than project-specific code."
  [sh]
  (println "running:" sh)
  (spit (System/getenv "TRAMPOLINE_FILE") sh))

(defn maybe-sudo [project]
  (when (-> project :docker :sudo)
    "sudo"))

(defn maybe-m2-map [project]
  (when-let [m2 (-> project :docker :m2-dest)]
    (<< "-v ~/.m2/:~{m2}")))

(defn port-map [project]
  (when-let [ports (-> project :docker :ports)]
    (->>
     (for [[src dest] ports]
       (<< "-p ~{src}:~{dest}"))
     (str/join " "))))

(defn env-vars [project]
  (when-let [envs (-> project :docker :env)]
    (->>
     (for [e envs
           :let [e (name e)
                 val (System/getenv e)]
           :when val]
       (<< "-e ~{e}='~{val}'"))
     (str/join " "))))

(defn links [project]
  (when-let [links (-> project :docker :links)]
    (->>
     (for [[src dest] links
           :let [src (name src)
                 dest (name dest)]]
       (<< "--link ~{src}:~{dest}"))
     (str/join " "))))

(defn lein
  "Performs a `docker run` on image, mounting this project's source directory inside the container, and then runs leiningen inside the container with the supplied args. lein must already be installed on the container.

Optional Project config: In your project.clj or ~/.lein/profiles.clj, add the following to your :docker map
- :sudo true if docker requires sudo to `docker run`
- :ports {} a map, passed to -p for port mapping
- :m2-dest \"/home/username/.m2/\", will -v mount ~/.m2/ to :m2-dest, dramatically speeds up lein deps

  "
  ([project]
   (println "Usage: lein docker lein <imgId or tag> <lein args>"))
  ([project args]
   (let [img (first args)
         lein-args (rest args)
         src-dir (:root project)
         dest-dir (str "/src/" (:name project))
         lein-cmd (str "lein " (str/join " " lein-args))]
     (sh-trampoline (str/join " " [(maybe-sudo project) "docker" "run" "-it" (env-vars project) (port-map project) "-v" (str src-dir ":" dest-dir) (maybe-m2-map project) (links project) img (format "sh -c '%s'" (<< "cd ~{dest-dir}; ~{lein-cmd}"))])))))

(defn docker
  "Run docker commands"
  {:help-arglists '([build push lein])
   :subtasks [#'build #'push #'lein]}
  [project & args]
  (let [cmd (first args)]
    (condp = cmd
      "build" (build project (rest args))
      "push" (push project (rest args))
      "lein" (lein project (rest args)))))
