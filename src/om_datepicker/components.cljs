(ns om-datepicker.components
  (:require-macros [cljs.core.async.macros :refer [go-loop alt!]])
  (:require [cljs.core.async :as async :refer [chan put! sliding-buffer]]
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
  [date selected-date]
  (let [sliding-date (calendar-start-date date)]
    (for [week (range 6)
          day  (range 7)]
      (let [sliding-date (d/switch-date! sliding-date 1)
            day          (.getDay sliding-date)]
        {:class (str "cell"
                     (when (= sliding-date selected-date)
                       " selected")
                     (when (= (.getMonth date) (.getMonth sliding-date))
                       " instant")
                     (when (or (= day 0) (= day 6))
                       " weekend"))
         :date  (d/date-instance sliding-date)
         :text  (.getDate sliding-date)}))))

(defn- month-panel-label
  [date]
  (str (get months (.getMonth date)) " " (.getFullYear date)))

(defn- monthpicker-change-month
  [cursor owner change-fn result-ch]
  (let [old-value (or (:value cursor)
                      (om/get-state owner :value))
        new-value (change-fn old-value)]
    (when-not (= old-value new-value)
      (om/set-state! owner :value new-value)
      (if result-ch
        (put! result-ch new-value)
        (om/update! cursor [:value] new-value)))))

(defn monthpicker-panel
  ;; TODO: allow-past? end-date
  [cursor owner {:keys [allow-past? end-date value-ch result-ch value] :or {allow-past? true value (d/current-month)}}]
  (reify
    om/IInitState
    (init-state [_]
      {:value     (or (:value cursor) value)
       :result-ch result-ch
       :kill-ch   (chan (sliding-buffer 1))})

    om/IWillMount
    (will-mount [_]
      (let [{:keys [kill-ch result-ch]} (om/get-state owner)]
        (when value-ch
          (go-loop []
                   (let [[v ch] (alts! [kill-ch value-ch] :priority true)]
                     (condp = ch
                       value-ch (do
                                  (monthpicker-change-month cursor owner (constantly v) result-ch)
                                  (recur))
                       kill-ch  (do
                                  (async/close! kill-ch))
                       nil))))))

    om/IWillUnmount
    (will-unmount [_]
      (put! (om/get-state owner :kill-ch) true))
    
    om/IRenderState
    (render-state [_ {:keys [value result-ch]}]
      (let [value (or (:value cursor) value)]
        (dom/div #js {:className "month-panel"}
                 (dom/div #js {:className "control left"
                               :onClick   #(monthpicker-change-month cursor owner d/previous-month result-ch)} "←")
                 (dom/div #js {:className "label"} (month-panel-label value))
                 (dom/div #js {:className "control right"
                               :onClick   #(monthpicker-change-month cursor owner d/next-month result-ch)} "→"))))))

(defn- calendar-cell
  [cursor owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [highlighted]}]
      (let [select-ch (om/get-shared owner :select-ch)]
        (dom/div #js {:className    (str (:class cursor) (when highlighted " highlighted"))
                      :onClick      #(put! select-ch (:date cursor))
                      :onMouseEnter #(om/set-state! owner :highlighted true)
                      :onMouseLeave #(om/set-state! owner :highlighted nil)} (:text cursor))))))

(defn datepicker-panel
  [cursor owner {:keys [allow-past? result-ch] :or {allow-past? true} :as options}]
  (reify
    om/IInitState
    (init-state [_]
      {:month-change-ch (chan)
       :select-ch       (chan (sliding-buffer 1))
       :value           (d/first-of-month (get cursor :value (d/today)))})

    om/IWillMount
    (will-mount [_]
      (let [{:keys [month-change-ch select-ch]} (om/get-state owner)]
        (go-loop []
                 (let [[v ch] (alts! [month-change-ch select-ch])]
                   (condp = ch
                     month-change-ch (do
                                       (om/set-state! owner :value v)
                                       (recur))
                     select-ch       (do
                                       (if result-ch
                                         (put! result-ch v)
                                         (om/update! cursor [:value] v))
                                       (om/set-state! owner :value (d/first-of-month v))
                                       (recur))
                     nil)))))

    om/IRenderState
    (render-state [_ {:keys [month-change-ch select-ch value]}]
      (let [selected (:value cursor)
            calendar (generate-calendar value selected)]
        (apply dom/div #js {:className "date-panel"}
               (om/build monthpicker-panel
                         {:value value}
                         {:opts {:result-ch month-change-ch}})
               ;; day names
               (apply dom/div #js {:className "days"}
                      (for [day days]
                        (dom/div #js {:className "cell"} day)))
               ;; calendar grid
               (for [week (partition 7 calendar)]
                 (apply dom/div #js {:className "week"}
                        (for [day week]
                          (om/build calendar-cell day
                                    {:shared {:select-ch select-ch}})))))))))

