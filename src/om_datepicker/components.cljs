(ns om-datepicker.components
  (:require-macros [cljs.core.async.macros :refer [go-loop alt!]])
  (:require [cljs.core.async :refer [chan put!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-datepicker.dates :as d]))

(def days ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"])

(def months ["January", "February", "March", "April",
             "May", "June", "July", "August",
             "September", "October", "November", "December"])

(defn- calendar-start-date
  [month-date]
  (let [month-date (doto (js/Date. month-date) (.setDate 1))
        day        (.getDay month-date)
        offset     (if (= 0 day) 7 day)]
    (.setDate month-date (- (.getDate month-date) offset))
    month-date))

(defn- generate-calendar
  [date]
  (let [sliding-date (calendar-start-date date)]
    (for [week (range 6)
          day  (range 7)]
      (let [sliding-date (d/switch-date! sliding-date 1)
            day          (.getDay sliding-date)]
        {:class (str "cell"
                     (when (= (.getMonth date) (.getMonth sliding-date))
                       " instant")
                     (when (or (= day 0) (= day 6))
                       " weekend"))
         :date  (.getDate sliding-date)}))))

(defn- month-panel-label
  [date]
  (str (get months (.getMonth date)) " " (.getFullYear date)))

(defn- change-month
  [owner change-fn result-ch]
  (let [old-value (om/get-state owner :value)
        new-value (change-fn old-value)]
    (om/set-state! owner :value new-value)
    (when result-ch
      (put! result-ch new-value))))

(defn monthpicker-panel
  [cursor owner {:keys [allow-past? end-date result-ch value] :or {allow-past? true value (d/current-month) :as opts}}]
  (reify
    om/IInitState
    (init-state [_]
      {:value     value
       :result-ch result-ch})
    
    om/IRenderState
    (render-state [_ {:keys [value result-ch]}]
      (dom/div #js {:className "month-panel"}
               (dom/div #js {:className "control left"
                             :onClick   #(change-month owner d/previous-month result-ch)} "←")
               (dom/div #js {:className "label"} (month-panel-label value))
               (dom/div #js {:className "control right"
                             :onClick   #(change-month owner d/next-month result-ch)} "→")))))

(defn calendar-cell
  [cursor owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [highlighted]}]
      (dom/div #js {:className    (str (:class cursor) (when highlighted " highlighted"))
                    :onClick      #(print :selected (:date cursor))
                    :onMouseEnter #(om/set-state! owner :highlighted true)
                    :onMouseLeave #(om/set-state! owner :highlighted nil)} (:date cursor)))))

(defn datepicker-panel
  [cursor owner {:keys [allow-past?] :or {allow-past? true} :as options}]
  (reify
    om/IInitState
    (init-state [_]
      {:month-change-ch (chan)
       :value           (d/first-of-month (:value cursor))})

    om/IWillMount
    (will-mount [_]
      (let [{:keys [month-change-ch]} (om/get-state owner)]
        (go-loop []
                 (let [[v ch] (alts! [month-change-ch])]
                   (condp = ch
                     month-change-ch (do
                                       (om/set-state! owner :value v)
                                       (recur))
                     nil)))))

    om/IRenderState
    (render-state [_ {:keys [month-change-ch value]}]
      (let [selected value
            calendar (generate-calendar selected)]
        (apply dom/div #js {:className "date-panel"}
               (om/build monthpicker-panel cursor
                         {:opts {:value     selected
                                 :result-ch month-change-ch}})
               ;; day names
               (apply dom/div #js {:className "days"}
                      (for [day days]
                        (dom/div #js {:className "cell"} day)))
               ;; calendar grid
               (for [week (partition 7 calendar)]
                 (apply dom/div #js {:className "week"}
                        (for [day week]
                          (om/build calendar-cell day)))))))))

