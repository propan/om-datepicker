(defproject om-datepicker "0.0.4-SNAPSHOT"
  :description "a collection of various date/month picker components for Om"
  :url "http://github.com/prokpa/om-datepicker"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha" :scope "provided"]
                 [org.clojure/clojurescript "0.0-3126" :scope "provided"]
                 [cljsjs/react-with-addons "0.13.1-0" :scope "provided"]
                 [org.omcljs/om "0.8.8" :exclusions [cljsjs/react] :scope "provided"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [com.cemerick/clojurescript.test "0.3.3"]
            [lein-less "1.7.2"]]

  :source-paths ["src"]

  :less
  {:source-paths ["examples/less"]
   :target-path   "examples/public/css"}
  
  :cljsbuild
  {:builds        [{:id           "dev"
                    :source-paths ["src"]
                    :compiler     {:output-to     "om-datepicker.js"
                                   :output-dir    "out"
                                   :optimizations :none
                                   :source-map    true}}

                   {:id           "test"
                    :source-paths ["src" "test"]
                    :compiler     {:output-to     "target/om-datepicker-tests.js"
                                   :optimizations :simple
                                   :pretty-print  true}}

                   {:id           "examples"
                    :source-paths ["src" "examples/src"]
                    :compiler     {:output-to     "examples/app.js"
                                   :output-dir    "examples/out"
                                   :source-map    "examples/app.js.map"
                                   :optimizations :none}}

                   {:id           "gh-pages"
                    :source-paths ["src" "examples/src"]
                    :compiler     {:output-to        "examples/app.min.js"
                                   :optimizations    :advanced
                                   :pretty-print     false
                                   :closure-warnings {:externs-validation :off
                                                      :non-standard-jsdoc :off}}}]
   :test-commands {"unit-tests" ["phantomjs" :runner
                                 "target/om-datepicker-tests.js"]}})
