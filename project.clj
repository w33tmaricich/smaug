(defproject smaug "0.1.0-SNAPSHOT"
  :description "Simple IP camera DVR"
  :url "https://github.com/w33tmaricich/smaug"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[clj-time "0.15.1"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.cli "0.4.2"]
                 [w33t/kawa "0.1.2"]
                 [org.clojure/clojure "1.10.0"]]
  :main ^:skip-aot smaug.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
