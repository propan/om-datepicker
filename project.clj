(defproject om-datepicker "0.1.0-SNAPSHOT"
  :description "a datepicker component for Om"
  :url "http://github.com/prokpa/om-datepicker"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2644" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha" :scope "provided"]
                 [om "0.8.0-beta2" :scope "provided"]

                 [lively "0.1.2"]]

  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]
            [lein-less "1.7.2"]]

  :source-paths ["src"]

  :less
  {:source-paths ["examples/less"]
   :target-path   "examples/public/css"}
  
  :cljsbuild
  {:builds [{:id           "dev"
             :source-paths ["src"]
             :compiler     {:output-to     "om-datepicker.js"
                            :output-dir    "out"
                            :optimizations :none
                            :source-map    true}}
            
            {:id           "examples"
             :source-paths ["src" "examples/src"]
             :compiler     {:output-to     "examples/app.js"
                            :output-dir    "examples/out"
                            :source-map    "examples/app.js.map"
                            :optimizations :none}}]})
