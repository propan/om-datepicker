(ns om-datepicker.examples.app
  (:require [om.core :as om :include-macros true]
            [om-datepicker.components :refer [datepicker datepicker-panel monthpicker-panel rangepicker]]
            [om-datepicker.dates :refer [today]]))

(defonce app-state
  (atom {:month-panel {}
         :date-panel  {:value (today)}
         :datepicker  {:value (today)}
         :rangepicker {:start (today)
                       :end   (today)}}))

(enable-console-print!)

(om/root
 monthpicker-panel
 app-state
 {:path   [:month-panel]
  :opts   {:min-date -60
           :max-date  60}
  :target (js/document.getElementById "monthpicker-panel")})

(om/root
 datepicker-panel
 app-state
 {:path   [:date-panel]
  :opts   {:min-date  -15
           :max-date   15
           :first-day  0}
  :target (js/document.getElementById "datepicker-panel")})

(om/root
 datepicker
 app-state
 {:path   [:datepicker]
  :target (js/document.getElementById "datepicker-demo")})

(om/root
 rangepicker
 app-state
 {:path   [:rangepicker]
  :opts   {:min-date -180
           :max-date  180}
  :target (js/document.getElementById "rangepicker-demo")})
