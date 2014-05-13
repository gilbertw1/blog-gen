(defproject blog-gen "0.1.0-SNAPSHOT"
  :description "bryan.codes && bryangilbert.com source code"
  :url "http://bryan.codes"
  :license {:name "BSD 2 Clause"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [stasis "1.1.1"]
                 [ring "1.2.2"]
                 [hiccup "1.0.5"]
                 [me.raynes/cegdown "0.1.1"]
                 [enlive "1.1.5"]
                 [clygments "0.1.1"]
                 [optimus "0.14.2"]
                 [clj-time "0.7.0"]]
  :ring {:handler blog-gen.web/app}
  :aliases {"build-site" ["run" "-m" "blog-gen.web/export"]}
  :profiles {:dev {:plugins [[lein-ring "0.8.10"]]}
                   :test {:dependencies [[midje "1.6.0"]]
                          :plugins [[lein-midje "3.1.3"]]}})
