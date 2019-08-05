; Launch the application:
 ; kiki lein run

; Run some tests:
 ; kiki lein test

; Build a jar (f):
 ; kiki lein uberjar

 ; kiki cat ./config.json | grep rtsp


(ns smaug.core
  (:require
   [clojure.core.async :as a :refer [>! <! >!! <!! go chan buffer close! thread alts! alts!! timeout]]
   [clojure.data.json :as json]
   [clojure.tools.cli :refer [parse-opts]]
   [kawa.core :refer [ffmpeg! ffplay!]]
   [kawa.manager :as recording-manager]
   )
  (:gen-class))

; defaults
(def config-file "./config.json")
(def quit-chan (chan))

; command line options
(def cli-options
  [["-c" "--config PATH"
    "Path to json config file."
    :default config-file]
   ["-d" "--display" :default false]
   ["-h" "--help"]])

(defn- date []
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd-hh-mm-SS") (java.util.Date.)))

(defn- retrieve-configuration
  "Reads in a json file at a given path. Checks that the json contains all
  the information we need, and then returns it as a map."
  [path]
  (if true ; TODO: Add a check to ensure all required fields are present.
    (let [file-contents (slurp path)
          configuration (json/read-str file-contents :key-fn keyword)]
      configuration)))

(defn- record-camera
  "Starts up a recording for a particular camera."
  [id url storage-directory chunk-length-seconds]
  (recording-manager/register (keyword id)
                              (ffmpeg! :input-url url
                                       :t chunk-length-seconds
                                       :c:v "copy" :an
                                       (str storage-directory id (date) ".mp4"))))

(defn- recordings-start
  "Spin up an ffmpeg process for each camera."
  [{storage-directory :storage-directory
    segments-minutes :segments-minutes
    cameras :cameras}]
  (println "Starting recording...")
  (loop [camera (first cameras)
         next-cameras (rest cameras)]
    (let [{camera-name :name
           camera-url :url} camera]
      (println "------------")
      (println camera-name)
      (println camera-url)
      (println (record-camera camera-name camera-url storage-directory (* 60000 segments-minutes)))
      (if (not-empty next-cameras)
        (recur (first next-cameras) (rest next-cameras)))))
  (keys (recording-manager/ls))
)

(defn- recording-stop
  "Stop a recording."
  [id]
  (recording-manager/kill (keyword id)))

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

(defn -main
  "Lets start some recordings!"
  [& args]
  (let [options (:options (parse-opts args cli-options))
        display? (-> options :display)
        config (retrieve-configuration (-> options :config))
        {storage-directory :storage-directory
         segments-minutes :segments-minutes
         max-segments :max-segments
         cameras :cameras} config
        ]

    (println "Options:" options)
    (println "Display?:" display?)
    (println "Config:" config)
    (println "storage-directory:" storage-directory)
    (println "segments-minutes:" segments-minutes)
    (println "max-segments:" max-segments)
    (println "cameras:" cameras)
    


    (loop [recording? (recordings-start config)]
      (Thread/sleep (* 6000 segments-minutes))
      (println "Cameras currently recording:")
      (println (keys (recording-manager/ls)))
      (println "Stopping recordings...")
      (recordings-stop (keys (recording-manager/ls)))
      (println "Recordings stopped.")
      (println "Restarting recordings...")
      (recordings-start config)
      (recur recording?))

    ))
