(ns om-datepicker.test-components
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [cemerick.cljs.test :refer [done is deftest testing test-var]])
  (:require [cemerick.cljs.test :as t]
            [cljs.core.async :refer [timeout <!]]
            [om.core :as om :include-macros true]
            [om-datepicker.components :refer [datepicker datepicker-panel monthpicker-panel rangepicker]]
            [om-datepicker.test-utils :as u :refer [click sel sel1 text]]))

(defn- node-text
  [parent selector]
  (-> parent
      (sel1 selector)
      text))

(extend-type js/NodeList
  ISeqable
  (-seq [array] (array-seq array 0)))

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

(deftest ^:async test-datepicker-panel-navigation-back
  (go
   (let [state     (atom {:panel {:value (js/Date. 2015 3 5)}})
         test-node (u/html->dom "<div class='content'></div>")]
     (om/root datepicker-panel state {:path   [:panel]
                                      :target test-node
                                      :opts   {:first-day 0
                                               :min-date  (js/Date. 2015 2 5)}})

     (testing "Renders correctly initial state"
       (is (= "April 2015" (node-text test-node ".label")))

       (is (= ["Su" "Mo" "Tu" "We" "Th" "Fr" "Sa"]
              (mapv text (sel test-node ".days .cell"))))

       (is (= ["29" "30" "31" "1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11" "12" "13" "14" "15" "16" "17" "18" "19" "20" "21" "22" "23" "24" "25" "26" "27" "28" "29" "30" "1" "2" "3" "4" "5" "6" "7" "8" "9"]
              (mapv text (sel test-node ".week > .cell"))))

       (is (every? true?
                   (mapv #(u/has-class? % "weekend") (sel test-node ".week > .cell:first-child"))))

       (is (every? true?
                   (mapv #(u/has-class? % "weekend") (sel test-node ".week > .cell:last-child")))))

     (testing "Shows previous month on click on .left control"
       (dotimes [_ 5]
         (click (sel1 test-node ".left")))
       (<! (timeout 100))

       (is (= "March 2015" (node-text test-node ".label")))

       (is (= ["1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11" "12" "13" "14" "15" "16" "17" "18" "19" "20" "21" "22" "23" "24" "25" "26" "27" "28" "29" "30" "31" "1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11"]
              (mapv text (sel test-node ".week > .cell"))))))

   (done)))

(deftest ^:async test-datepicker-panel-navigation-forward
  (go
   (let [state     (atom {:panel {:value (js/Date. 2015 3 5)}})
         test-node (u/html->dom "<div class='content'></div>")]
     (om/root datepicker-panel state {:path   [:panel]
                                      :target test-node
                                      :opts   {:style    :short
                                               :max-date (js/Date. 2015 4 5)}})

     (testing "Renders correctly initial state"
       (is (= "April 2015" (node-text test-node ".label")))

       (is (= ["M" "T" "W" "T" "F" "S" "S"]
              (mapv text (sel test-node ".days .cell"))))

       (is (= ["30" "31" "1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11" "12" "13" "14" "15" "16" "17" "18" "19" "20" "21" "22" "23" "24" "25" "26" "27" "28" "29" "30" "1" "2" "3" "4" "5" "6" "7" "8" "9" "10"]
              (mapv text (sel test-node ".week > .cell"))))

       (is (every? true?
                   (mapv #(u/has-class? % "weekend") (sel test-node ".week > .cell:nth-child(6)"))))

       (is (every? true?
                   (mapv #(u/has-class? % "weekend") (sel test-node ".week > .cell:nth-child(7)")))))

     (testing "Shows next month on click on .right control"
       (dotimes [_ 5]
         (click (sel1 test-node ".right")))
       (<! (timeout 100))

       (is (= "May 2015" (node-text test-node ".label")))

       (is (= ["27" "28" "29" "30" "1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11" "12" "13" "14" "15" "16" "17" "18" "19" "20" "21" "22" "23" "24" "25" "26" "27" "28" "29" "30" "31" "1" "2" "3" "4" "5" "6" "7"]
              (mapv text (sel test-node ".week > .cell"))))))

   (done)))

(deftest ^:async test-datepicker-panel-date-selection
  (go
   (let [state     (atom {:panel {:value (js/Date. 2015 3 15)}})
         test-node (u/html->dom "<div class='content'></div>")]
     (om/root datepicker-panel state {:path   [:panel]
                                      :target test-node
                                      :opts   {:min-date (js/Date. 2015 3 10)
                                               :max-date (js/Date. 2015 3 20)}})

     (testing "Allows selection of a date and highlights it"
       (click (first (drop 14 (seq (sel test-node ".week > .cell")))))
       (<! (timeout 100))

       (is (= {:value (js/Date. 2015 3 13)} (:panel @state)))
       (is (true? (u/has-class? (first (drop 14 (seq (sel test-node ".week > .cell")))) "selected"))))

     (testing "Does not allow selection below min-date and above max-date"

       (click (first (drop 4 (seq (sel test-node ".week > .cell")))))
       (<! (timeout 100))

       (is (= {:value (js/Date. 2015 3 13)} (:panel @state)))

       (click (first (drop 24 (seq (sel test-node ".week > .cell")))))
       (<! (timeout 100))

       (is (= {:value (js/Date. 2015 3 13)} (:panel @state)))))

   (done)))

(deftest ^:async test-datepicker-expansion-and-selection
  (go
   (let [state     (atom {:date {:value (js/Date. 2015 3 15)}})
         test-node (u/html->dom "<div class='content'></div>")]
     (om/root datepicker state {:path   [:date]
                                :target test-node
                                :opts   {:min-date (js/Date. 2015 3 10)
                                         :max-date (js/Date. 2015 3 20)}})

     (testing "Expands on click"
       (is (false? (u/is-shown? (sel1 test-node ".datepicker-popup"))))
       (click (sel1 test-node ".datepicker-input"))
       (<! (timeout 100))
       (is (true? (u/is-shown? (sel1 test-node ".datepicker-popup"))))
       (click (sel1 test-node ".datepicker-input"))
       (<! (timeout 100))
       (is (false? (u/is-shown? (sel1 test-node ".datepicker-popup")))))

     (testing "Allows selection of a date and highlights it"
       (click (first (drop 14 (seq (sel test-node ".week > .cell")))))
       (<! (timeout 100))

       (is (= {:value (js/Date. 2015 3 13)} (:date @state)))
       (is (true? (u/has-class? (first (drop 14 (seq (sel test-node ".week > .cell")))) "selected"))))

     (testing "Does not allow selection below min-date and above max-date"
       (click (first (drop 4 (seq (sel test-node ".week > .cell")))))
       (<! (timeout 100))

       (is (= {:value (js/Date. 2015 3 13)} (:date @state)))

       (click (first (drop 24 (seq (sel test-node ".week > .cell")))))
       (<! (timeout 100))

       (is (= {:value (js/Date. 2015 3 13)} (:date @state)))))

   (done)))

(deftest ^:async test-rangepicker-expansion
  (go
   (let [state     (atom {:start (js/Date. 2015 3 15)
                          :end   (js/Date. 2015 3 20)})
         test-node (u/html->dom "<div class='content'></div>")]
     (om/root rangepicker state {:target test-node})

     (testing "Expands on click"
       (is (false? (u/is-shown? (sel1 test-node ".rangepicker-popup"))))
       (click (sel1 test-node ".rangepicker-input"))
       (<! (timeout 100))
       (is (true? (u/is-shown? (sel1 test-node ".rangepicker-popup"))))
       (click (sel1 test-node ".rangepicker-input"))
       (<! (timeout 100))
       (is (false? (u/is-shown? (sel1 test-node ".rangepicker-popup"))))))

   (done)))

(deftest ^:async test-rangepicker-selection-on-month-click
  (go
   (let [state     (atom {:start (js/Date. 2015 3 15)
                          :end   (js/Date. 2015 3 20)})
         test-node (u/html->dom "<div class='content'></div>")]
     (om/root rangepicker state {:target test-node
                                 :opts   {:min-date (js/Date. 2015 2 10)
                                          :max-date (js/Date. 2015 4 20)}})

     (testing "Selects a full month on month name click"
       (click (second (sel test-node ".month")))
       (<! (timeout 100))
       (click (first (sel test-node ".button")))
       (<! (timeout 100))
       (let [{:keys [start end]} @state]
         (is (= (js/Date. 2015 3 1) start))
         (is (= (js/Date. 2015 3 30) end))))

     (testing "Selects a range limited by min-date on month click"
       (click (first (sel test-node ".month")))
       (<! (timeout 100))
       (click (first (sel test-node ".button")))
       (<! (timeout 100))
       (let [{:keys [start end]} @state]
         (is (= (js/Date. 2015 2 10) start))
         (is (= (js/Date. 2015 2 31) end)))
       )

     (testing "Selects a range limited by max-date on month click"
       ;; move to May
       (click (sel1 test-node ".right"))
       (<! (timeout 100))
       (click (last (sel test-node ".month")))
       (<! (timeout 100))
       (click (first (sel test-node ".button")))
       (<! (timeout 100))
       (let [{:keys [start end]} @state]
         (is (= (js/Date. 2015 4 1) start))
         (is (= (js/Date. 2015 4 20) end)))))

   (done)))
