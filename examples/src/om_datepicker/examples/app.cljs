(ns om-datepicker.examples.app
  (:require [lively]
            [om.core :as om :include-macros true]
            [om-datepicker.components :refer [datepicker datepicker-panel monthpicker-panel]]))

(defonce app-state
  (atom {:month-panel {}
         :date-panel  {:value (js/Date.)}
         :datepicker  {:value (js/Date.)}}))

(enable-console-print!)

(lively/start "/app.js" {:polling-rate 500})

(om/root
 monthpicker-panel
 app-state
 {:path   [:month-panel]
  :opts   {:allow-past? false
           :end-date    (js/Date. 2015 3 0)}
  :target (js/document.getElementById "monthpicker-panel")})

(om/root
 datepicker-panel
 app-state
 {:path   [:date-panel]
  :opts   {:allow-past? false
           :end-date    15}
  :target (js/document.getElementById "datepicker-panel")})

(om/root
 datepicker
 app-state
 {:path   [:datepicker]
  :opts   {:x 2}
  :target (js/document.getElementById "datepicker-demo")})
