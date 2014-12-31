(ns om-datepicker.examples.app
  (:require [lively]
            [om.core :as om :include-macros true]
            [om-datepicker.components :refer [datepicker-panel monthpicker-panel]]))

(defonce app-state
  (atom {:date-panel {:value (js/Date.)}}))

(enable-console-print!)

(lively/start "/app.js" {:polling-rate 500})

(om/root
 monthpicker-panel
 app-state
 {:target (js/document.getElementById "monthpicker-panel")})

(om/root
 datepicker-panel
 app-state
 {:path   [:date-panel]
  :opts   {:t true}
  :target (js/document.getElementById "datepicker-panel")})
