(ns om-datepicker.events
  (:require [cljs.core.async :as async :refer [chan put!]]
            [goog.events :as events]))

(defn mouse-click-listen []
  (let [ch (chan)]
    {:ch           ch
     :listener-key (events/listen js/document events/EventType.CLICK #(put! ch %))}))

(defn unlisten-by-key [listener-key]
  (events/unlistenByKey listener-key))
