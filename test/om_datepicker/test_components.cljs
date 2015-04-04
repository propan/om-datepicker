(ns om-datepicker.test-components
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [cemerick.cljs.test :refer [done is deftest testing test-var]])
  (:require [cemerick.cljs.test :as t]
            [cljs.core.async :refer [timeout <!]]
            [om.core :as om :include-macros true]
            [om-datepicker.components :refer [monthpicker-panel]]
            [om-datepicker.test-utils :as u :refer [click sel sel1 text]]))

(defn- node-text
  [parent selector]
  (-> parent
      (sel1 selector)
      text))

(deftest ^:async test-monthpicker-panel-with-lower-bound
  (go
   (let [state     (atom {:month {:value (js/Date. 2015 5 1)}})
         test-node (u/html->dom "<div class='content'></div>")]
     (om/root monthpicker-panel state {:path   [:month]
                                       :target test-node
                                       :opts   {:min-date (js/Date. 2015 3 1)}})
     
     (testing "Renders correctly initial state"
       (is (= "June 2015" (node-text test-node ".label"))))

     (testing "Decreases date by a month on click on .left control"
       (click (sel1 test-node ".left"))
       (<! (timeout 100))
       (is (= {:value (js/Date. 2015 4 1)} (:month @state)))
       (is (= "May 2015" (node-text test-node ".label"))))

     (testing "Does not go below min-date"
       (dotimes [_ 5]
         (click (sel1 test-node ".left")))
       (<! (timeout 100))
       (is (= {:value (js/Date. 2015 3 1)} (:month @state)))
       (is (= "April 2015" (node-text test-node ".label")))))
   
   (done)))

(deftest ^:async test-monthpicker-panel-with-upper-bound
  (go
   (let [state     (atom {:month {:value (js/Date. 2015 5 1)}})
         test-node (u/html->dom "<div class='content'></div>")]
     (om/root monthpicker-panel state {:path   [:month]
                                       :target test-node
                                       :opts   {:max-date (js/Date. 2015 7 10)}})
     
     (testing "Renders correctly initial state"
       (is (= "June 2015" (node-text test-node ".label"))))

     (testing "Increases date by a month on click on .right control"
       (click (sel1 test-node ".right"))
       (<! (timeout 100))
       (is (= {:value (js/Date. 2015 6 1)} (:month @state)))
       (is (= "July 2015" (node-text test-node ".label"))))

     (testing "Does not go above max-date"
       (dotimes [_ 5]
         (click (sel1 test-node ".right")))
       (<! (timeout 100))
       (is (= {:value (js/Date. 2015 7 1)} (:month @state)))
       (is (= "August 2015" (node-text test-node ".label")))))
   
   (done)))
