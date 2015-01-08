(ns om-datepicker.events
  (:require [cljs.core.async :as async :refer [chan put!]]
            [goog.events :as events]))

(defn mouse-click []
  (let [ch (chan)]
    (events/listen js/document events/EventType.CLICK #(put! ch %))
    ch))
