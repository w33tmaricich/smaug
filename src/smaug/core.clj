;;; smaug - simple ip camera dvr
;;;  Alexander Maricich 2019

; Launch the application:
 ; kiki lein run

; Run some tests:
 ; kiki lein test

; Build a jar (f):
 ; kiki lein uberjar

 ; kiki cat ./config.json | grep rtsp

;;; To learn more about kiki, please visit
;;; https://github.com/w33tmaricich/kiki

 ; [ ] TODO: Ensure the datetime format formatting doesn't cause it to crash
 ;           again.

(ns smaug.core
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojure.data.json :as json]
   [clojure.java.io :refer [make-parents]]
   [clojure.string :refer [includes?]]
   [clojure.tools.cli :refer [parse-opts]]
   [kawa.core :refer [ffmpeg! ffplay!]]
   [kawa.manager :as recording-manager])
  (:gen-class))

; defaults
(def config-file "/etc/smaug/config.json")
(def ffplay-prefix "ffplay-")
(def datetime-format "yyyy-MM-dd-hh_mm_SSS")

; command line options
(def cli-options
  [["-c" "--config PATH"
    "Path to json config file."
    :default config-file]
   ["-d" "--display" :default false]
   ["-h" "--help"]])

(defn- date []
  (.format (java.text.SimpleDateFormat. datetime-format) (java.util.Date.)))

(defn- retrieve-configuration
  "Reads in a json file at a given path. Checks that the json contains all
  the information we need, and then returns it as a map."
  [path]
  (if true ; [ ] TODO: Add a check to ensure all required fields are present.
    (let [file-contents (slurp path)
          configuration (json/read-str file-contents :key-fn keyword)]
      configuration)))

(defn- record-camera
  "Starts up a recording for a particular camera."
  [id url storage-directory chunk-length-seconds]
  (let [file-name (str storage-directory id "/" id (date) ".mp4")]
    (make-parents file-name) ; create directories if they dont exist.
    (recording-manager/register (keyword id)
                                (ffmpeg! :input-url url
                                         :duration chunk-length-seconds
                                         :c:v "copy" :disable-audio
                                         file-name))))

(defn- recordings-start
  "Spin up an ffmpeg process for each camera."
  [{storage-directory :storage-directory
    segments-minutes :segments-minutes
    cameras :cameras}]
  (println "------------")
  (loop [camera (first cameras)
         next-cameras (rest cameras)]
    (let [{camera-name :name
           camera-url :url} camera]
      (println (str camera-name ":") camera-url)
      (record-camera camera-name camera-url storage-directory (* 60000 segments-minutes))
      (if (not-empty next-cameras)
        (recur (first next-cameras) (rest next-cameras)))))
  (keys (recording-manager/ls))
)

(defn- recording-stop
  "Stop a recording."
  [id]
  ; Dont kill our ffplay processes please.
  (if (not (includes? (name id) ffplay-prefix))
    (recording-manager/kill (keyword id))))

(def not-nil? (complement nil?))
(defn- recordings-stop
  "Stop a list of recordings."
  [ids]
  (loop [id (first ids)
         id-list (rest ids)]
    (if (not-nil? id)
      (do
       (recording-stop id)
       (recur (first id-list)
              (rest id-list))))))

(defn file-data
  "Takes in a path and returns parsed data."
  [f]
  (let [path (str f)
        file (re-find #"[\w\-]+.mp4$" path)
        dir (second (re-find #"[[\w\/\\]+(\/|\\)]" path))
        date (re-find #"\d+-\d+-\d+" file)
        timestamp (re-find #"\d+\_\d+\_\d+" file)
        stream-name (second (re-find #"([\w]+)\d\d\d\d-\d\d-\d\d" file))
        joda-string (str date "-" timestamp)
        joda-format (f/formatter datetime-format)
        joda (f/parse joda-format joda-string)]
    {:path path
     :file f
     :directory dir
     :date date
     :time timestamp
     :joda joda
     :name stream-name}))

(defn storage-state
  "Takes a list of files and returns information about the directory and its
  contents."
  [files]
  (let [files-info (doall (map file-data files))
        fcount (count files-info)
        fnames (into #{} (doall (map #(keyword (:name %)) files-info)))
        streams (reduce merge {} (doall (for [file-name fnames]
                                          {(keyword file-name)
                                           (filter #(= file-name (keyword (:name %))) files-info)})))]
    {:files files-info
     :count fcount
     :stream-names fnames
     :streams streams}))

(defn oldest-file
  "returns the oldest file"
  [files]
  (loop [file (first files)
         rest-files (rest files)
         oldest nil]
    (if (and (nil? file)
             (empty? rest-files))
      oldest
      (if (t/before? (:joda file) (:joda oldest))
        (recur (first rest-files) (rest rest-files) file)
        (recur (first rest-files) (rest rest-files) oldest)))))

(defn clean-storage
  "If there are more files stored than allowed, delete the oldest."
  [{dir :storage-directory limit :max-segments}]
  (let [folder (clojure.java.io/file dir)
        files (drop 1 (doall (file-seq folder)))
        state (storage-state files)
        {stream-names :stream-names
         streams :streams} state]
    (doall (for [stream-name stream-names]
      (let [streams-with-name (stream-name streams)
            num-streams (count streams-with-name)
            oldest (oldest-file streams-with-name)]
        (if (> num-streams limit)
          (try
            (clojure.java.io/delete-file (:file oldest))
            (catch Exception e (do
                                 (println (str "Unable to delete file: " (.getMessage e)))
                                 (str "[clean-storage] Could not delete file: " (.getMessage e)))))))))))

(defn- view-cameras
  "Spin up an ffplay process for each camera."
  [{cameras :cameras}]
  (doall (for [camera cameras]
    (let [{id :name
           url :url} camera]
      (recording-manager/register (keyword (str ffplay-prefix id))
                                  (ffplay! url))))))

(defn parse-int [s]
  (.parse (java.text.NumberFormat/getInstance s)))

(defn -main
  "Lets start some recordings!"
  [& args]
  ; parse configuration
  (let [options (:options (parse-opts args cli-options))
        display? (-> options :display)
        config (retrieve-configuration (-> options :config))
        {storage-directory :storage-directory
         segments-minutes :segments-minutes
         max-segments :max-segments} config
        ]

    ; debugging print
    (println "Storage location:" storage-directory)
    (println "Capture length:" segments-minutes "minutes")
    (println "Maximum number of files:" max-segments)

    ; play the video with ffplay if requested.
    (if display? (view-cameras config))

    ; recording segment loop
    (loop [recording? (recordings-start config)]

      ; clean up excess files
      (let [recording-ids (keys (recording-manager/ls))
            recording-directories (map #(str storage-directory (name %))
                                       recording-ids)]
        (loop [dir (first recording-directories)
               dirs (rest recording-directories)]
          (if (and (nil? dir)
                   (empty? dirs))
            :done
            (do
             (clean-storage (update config
                                    :storage-directory
                                    #(if (not-nil? %) dir)))
             (recur (first dirs) (rest dirs))))))

      ; wait for the recording to finish before starting another
      (Thread/sleep (* 60000 segments-minutes))

      ; stop current recording segments.
      (recordings-stop (keys (recording-manager/ls)))

      ; restart recordings
      (recordings-start config)

      (recur recording?))))
