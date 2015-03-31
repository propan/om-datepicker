(ns om-datepicker.components
  (:require-macros [cljs.core.async.macros :refer [go-loop alt!]])
  (:require [cljs.core.async :as async :refer [chan put! sliding-buffer]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-datepicker.dates :as d :refer [after? before? is-future?]]
            [om-datepicker.events :refer [mouse-click]]))

(def days
  {:short  ["S" "M" "T" "W" "T" "F" "S"]
   :medium ["Su" "Mo" "Tu" "We" "Th" "Fr" "Sa"]
   :long   ["Sun" "Mon" "Tue" "Wen" "Tho" "Fri" "San"]})

(def months
  {:short ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"]
   :long  ["January" "February" "March" "April" "May" "June"
           "July" "August" "September" "October" "November" "December"]})

(defn- calendar-start-date
  [month-date first-day]
  (let [month-date (doto (js/Date. month-date) (.setDate 1))
        day        (.getDay month-date)
        offset     (- day (dec first-day))
        offset     (if (pos? offset) offset (+ offset 7))]
    (.setDate month-date (- (.getDate month-date) offset))
    month-date))

(defn- generate-calendar
  [date selected-date allow-past? end-date first-day]
  (let [today        (d/today)
        sliding-date (calendar-start-date date first-day)]
    (for [week (range 6)
          day  (range 7)]
      (let [sliding-date (d/switch-date! sliding-date 1)
            allowed?     (and (or allow-past?
                                  (after? sliding-date today))
                              (or (nil? end-date)
                                  (before? sliding-date end-date)))
            day          (.getDay sliding-date)]
        {:class    (str "cell"
                        (when-not allowed?
                          " disabled")
                        (when (= sliding-date selected-date)
                          " selected")
                        (when (= (.getMonth date) (.getMonth sliding-date))
                          " instant")
                        (when (or (= day 0) (= day 6))
                          " weekend"))
         :allowed? allowed?
         :date     (d/date-instance sliding-date)
         :text     (.getDate sliding-date)}))))

(defn- month-panel-label
  [date]
  (str (get (:long months) (.getMonth date)) " " (.getFullYear date)))

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
  "Creates a month-picker panel component.

   opts - a map of options. The following keys are supported:

     :allow-past? - if false, picking a month from the past is not allowed.
     :end-date    - if set, picking a month from the future is limited by that date.
                    Can be a date or a number of days from today.
     :value-ch    - if set, the picker value is updated with the values from that channel.
     :result-ch   - if passed, then picked values are put in that channel instead of :value key of the cursor.
     :value       - initial value, used when there is no value in :value cursor.

   Example:

     (om/build monthpicker-panel app
            {:opts {:allow-past? false
                    :end-date    ...
                    :result-ch   ...}})
  "
  [cursor owner {:keys [allow-past? end-date value-ch result-ch value]
                 :or   {allow-past? true value (d/current-month)}
                 :as   opts}]
  (let [end-date (d/coerse-date end-date)]
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
        (let [value           (or (:value cursor) value)
              can-go-back?    (or allow-past?
                                  (is-future? value))
              can-go-forward? (or (nil? end-date)
                                  (before? (d/next-month value) end-date))]
          (dom/div #js {:className "month-panel navigation"}
                   (dom/div #js {:className (str "control left" (when-not can-go-back? " disabled"))
                                 :onClick   (when can-go-back?
                                              #(monthpicker-change-month cursor owner d/previous-month result-ch))} "")
                   (dom/div #js {:className "label"} (month-panel-label value))
                   (dom/div #js {:className (str "control right" (when-not can-go-forward? " disabled"))
                                 :onClick   (when can-go-forward?
                                              #(monthpicker-change-month cursor owner d/next-month result-ch))} "")))))))

(defn- calendar-cell
  [{:keys [allowed? class date text]} owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [highlighted]}]
      (let [select-ch (om/get-shared owner :select-ch)
            allowed?  (and (not (nil? text))
                           allowed?)
            touch-fn  (when allowed?
                        (fn []
                          (om/set-state! owner :highlighted nil)
                          (put! select-ch date)))]
        (dom/div #js {:className    (str class (when highlighted " highlighted"))
                      :onClick      touch-fn
                      :onTouchStart touch-fn
                      :onMouseEnter (when allowed?
                                      #(om/set-state! owner :highlighted true))
                      :onMouseLeave (when allowed?
                                      #(om/set-state! owner :highlighted nil))} text)))))

(defn datepicker-panel
  "Creates a date-picker panel component.

   opts - a map of options. The following keys are supported:

     :allow-past? - if false, picking a date from the past is not allowed.
     :end-date    - if set, picking a date from the future is limited by that date.
                    Can be a date or a number of days from today.
     :first-day   - the first day of the week. Default: 1 (Monday)
     :result-ch   - if passed, then values are put in that channel instead of :value key of the cursor.
     :style       - the style that will be applied to the string representations of days of the week.
                    Possible values are :short, :medium and :long. Default value is :medium.

   Example:

     (om/build datepicker-panel app
            {:opts {:allow-past? false
                    :end-date    ...
                    :first-day   0
                    :result-ch   ...
                    :style       :long}})
  "
  [cursor owner {:keys [allow-past? end-date first-day result-ch style]
                 :or   {allow-past? true first-day 1 style :medium}
                 :as   opts}]
  (let [end-date (d/coerse-date end-date)]
    (reify
      om/IInitState
      (init-state [_]
        {:month-change-ch (chan)
         :select-ch       (chan (sliding-buffer 1))
         :kill-ch         (chan (sliding-buffer 1))
         :value           (d/first-of-month (get cursor :value (d/today)))})

      om/IWillMount
      (will-mount [_]
        (let [{:keys [kill-ch month-change-ch select-ch]} (om/get-state owner)]
          (go-loop []
                   (let [[v ch] (alts! [kill-ch month-change-ch select-ch] :priority true)]
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
                       kill-ch         (do
                                         (async/close! month-change-ch)
                                         (async/close! select-ch)
                                         (async/close! kill-ch))
                       nil)))))

      om/IWillUnmount
      (will-unmount [_]
        (put! (om/get-state owner :kill-ch) true))

      om/IRenderState
      (render-state [_ {:keys [month-change-ch select-ch value]}]
        (let [selected (:value cursor)
              calendar (generate-calendar value selected allow-past? end-date first-day)]
          (apply dom/div #js {:className "gridline"}
                 (om/build monthpicker-panel
                           {:value value}
                           {:opts {:allow-past? allow-past?
                                   :end-date    end-date
                                   :result-ch   month-change-ch}})
                 ;; day names
                 (apply dom/div #js {:className "days"}
                        (let [days (take 7 (drop first-day (cycle (get days style))))]
                          (for [day days]
                            (dom/div #js {:className "cell"} day))))
                 ;; calendar grid
                 (for [week (partition 7 calendar)]
                   (apply dom/div #js {:className "week"}
                          (for [day week]
                            (om/build calendar-cell day
                                      {:shared {:select-ch select-ch}}))))))))))

(defn- datepicker-label
  [date style]
  (let [labels (get days style)]
    (str (get labels (.getDay date)) ", " (.getDate date) " " (get (:short months) (.getMonth date)))))

(defn datepicker
  "Creates a date-picker component.

   opts - a map of options. The following keys are supported:

     :allow-past? - if false, picking a date from the past is not allowed.
     :end-date    - if set, picking a date from the future is limited by that date.
                    Can be a date or a number of days from today.
     :first-day   - the first day of the week. Default: 1 (Monday)
     :result-ch   - if passed, then picked values are put in that channel instead of :value key of the cursor.
     :style       - the style that will be applied to the string representations of days of the week.
                    Possible values are :short, :medium and :long. Default value is :medium.

   Example:

     (om/build datepicker app
            {:opts {:allow-past? false
                    :end-date    ...
                    :first-day   0
                    :result-ch   ...
                    :style       :long}})
  "
  [cursor owner {:keys [allow-past? end-date first-day result-ch style]
                 :or   {allow-past? true first-day 1 style :medium}
                 :as   opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:expanded       false
       :mouse-click-ch (mouse-click)
       :select-ch      (chan (sliding-buffer 1))
       :kill-ch        (chan (sliding-buffer 1))})

    om/IWillMount
    (will-mount [_]
      (let [{:keys [kill-ch mouse-click-ch select-ch]} (om/get-state owner)]
        (go-loop []
                 (let [[v ch] (alts! [kill-ch mouse-click-ch select-ch] :priority true)]
                   (condp = ch
                     mouse-click-ch (let [n (om/get-node owner)]
                                      (when-not (.contains n (.-target v))
                                        (om/set-state! owner :expanded false))
                                      (recur))
                     select-ch      (do
                                      (if result-ch
                                        (put! result-ch v)
                                        (om/update! cursor [:value] v))
                                      (om/set-state! owner :expanded false)
                                      (recur))
                     kill-ch        (do
                                      (async/close! mouse-click-ch)
                                      (async/close! select-ch)
                                      (async/close! kill-ch))
                     nil)))))

    om/IWillUnmount
    (will-unmount [_]
      (put! (om/get-state owner :kill-ch) true))

    om/IRenderState
    (render-state [_ {:keys [highlighted expanded select-ch]}]
      (dom/div #js {:className "datepicker"}
               (dom/input #js {:type         "text"
                               :readOnly     "readonly"
                               :value        (datepicker-label (:value cursor) :long)
                               :className    (str "datepicker-input" (when highlighted
                                                                  " highlighted"))
                               :onClick      #(om/update-state! owner :expanded (fn [v]
                                                                                  (if v false true)))
                               :onMouseEnter #(om/set-state! owner :highlighted true)
                               :onMouseLeave #(om/set-state! owner :highlighted nil)})
               (dom/div #js {:className "datepicker-popup"
                             :style #js {:display (if expanded "block" "none")}}
                        (dom/div #js {:className "datepicker-popup-inner"}
                                 (dom/div #js {:className "datepicker-popup-content"}
                                          (dom/div #js {:className "pointer"})
                                          (om/build datepicker-panel cursor
                                                    {:opts {:allow-past? allow-past?
                                                            :end-date    end-date
                                                            :first-day   first-day
                                                            :result-ch   select-ch
                                                            :style       style}}))))))))

;; TODO: rename
(defn- range-date
  [date]
  (str (get (:short months) (.getMonth date)) " " (.getDate date) ", " (.getFullYear date)))

(defn- month-cell
  [{:keys [lable value]} owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "month"}
               lable))))

(defn- generate-month-gridline
  [month-date selection-start selection-end start-date end-date first-day]
  (let [sliding-date (calendar-start-date month-date first-day)]
    (for [week (range 6)
          day  (range 7)]
      (let [sliding-date (d/switch-date! sliding-date 1)
            allowed?     (and (or (nil? start-date)
                                  (after? sliding-date start-date))
                              (or (nil? end-date)
                                  (before? sliding-date end-date)))
            day          (.getDay sliding-date)
            same-month?  (= (.getMonth month-date) (.getMonth sliding-date))]
        {:class    (str "cell"
                        (when-not allowed?
v                          " disabled")
                        (when (and (after? sliding-date selection-start)
                                   (before? sliding-date selection-end))
                          " selected")
                        (when same-month?
                          " instant")
                        (when (or (= day 0) (= day 6))
                          " weekend"))
         :allowed? allowed?
         :date     (d/date-instance sliding-date)
         :text     (when same-month?
                     (.getDate sliding-date))}))))

(defn- gridline
  [{:keys [value selection-start selection-end]} owner {:keys [allow-past? end-date first-day only-this-month? result-ch style]
                                                        :or   {allow-past? true first-day 1 style :short}
                                                        :as   opts}]
  (reify
    om/IRenderState
    (render-state [_ _]
      (let [calendar (generate-month-gridline value selection-start selection-end (when-not allow-past? (d/today)) end-date first-day)]
        (apply dom/div #js {:className "gridline"}
                 ;; day names
                 (apply dom/div #js {:className "days"}
                        (let [days (take 7 (drop first-day (cycle (get days :short))))]
                          (for [day days]
                            (dom/div #js {:className "cell"} day))))
                 (for [week (partition 7 calendar)]
                   (apply dom/div #js {:className "week"}
                          (for [day week]
                            (om/build calendar-cell day
                                      {:shared {:select-ch select-ch}})))))))))

(defn- generate-months-range
  [date]
  (->> date
      (d/first-of-month)
      (d/previous-month)
      (iterate d/next-month)
      (take 3)
      (map (fn [month]
             {:lable (str (get-in months [:long (.getMonth month)]) " " (.getFullYear month))
              :value month}))))

(defn rangepicker
  [cursor owner {:keys [allow-past? end-date first-day]
                 :or   {allow-past? true first-day 1}
                 :as   opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:expanded  false
       :start     (get cursor :start (d/today))
       :end       (get cursor :end (d/today))
       :select-ch (chan (sliding-buffer 1))
       :kill-ch   (chan (sliding-buffer 1))})

    om/IRenderState
    (render-state [_ {:keys [expanded highlighted grid-date mode start end]
                      :or   {mode :start}}]
      (let [grid-date    (or grid-date
                             (if (= mode :start) start end))
            months-range (generate-months-range grid-date)]
        (dom/div #js {:className "rangepicker"}
                 (dom/input #js {:type         "text"
                                 :readOnly     "readonly"
                                 :value        (str (range-date (:start cursor)) " - " (range-date (:end cursor)))
                                 :className    (str "rangepicker-input" (when highlighted
                                                                          " highlighted"))
                                 :onClick      #(om/update-state! owner :expanded (fn [v]
                                                                                    (if v false true)))
                                 :onMouseEnter #(om/set-state! owner :highlighted true)
                                 :onMouseLeave #(om/set-state! owner :highlighted nil)})
                 (dom/div #js {:className "rangepicker-popup"
                               :style #js {:display (if expanded "block" "none")}}
                          (dom/div #js {:className "rangepicker-popup-inner"}
                                   (dom/div #js {:className "rangepicker-popup-content"}
                                            (dom/div #js {:className "controls-panel"}
                                                     (dom/div #js {:className "inputs-panel"}
                                                              (dom/input #js {:type      "text"
                                                                              :readOnly  "readonly"
                                                                              :value     (range-date start)
                                                                              :className (str "daterange-input"
                                                                                              (when (= mode :start) " highlighted"))
                                                                              :onClick   #(om/set-state! owner :mode :start)})
                                                              " - "
                                                              (dom/input #js {:type      "text"
                                                                              :readOnly  "readonly"
                                                                              :value     (range-date end)
                                                                              :className (str "daterange-input"
                                                                                              (when (= mode :end) " highlighted"))
                                                                              :onClick   #(om/set-state! owner :mode :end)}))
                                                     (dom/div #js {:className "buttons-panel"}
                                                              (dom/span #js {:className "button"}
                                                                        "Apply")
                                                              (dom/span #js {:className "button"}
                                                                        "Cancel")))
                                            (dom/div #js {:className "calendar-panel"}
                                                     (apply dom/div #js {:className "months navigation"}
                                                            ;; TODO: can it be simpler?
                                                            (concat
                                                             [(dom/div #js {:className "control left"
                                                                            :onClick   #(om/set-state! owner :grid-date (d/previous-month grid-date))})]
                                                             (om/build-all month-cell months-range)
                                                             [(dom/div #js {:className "control right"
                                                                            :onClick   #(om/set-state! owner :grid-date (d/next-month grid-date))})]))
                                                     (apply dom/div #js {:className "gridlines"}
                                                            (for [month months-range]
                                                              (om/build gridline {:value           (:value month)
                                                                                  :selection-start start
                                                                                  :selection-end   end}
                                                                        {:opts {:allow-past? allow-past?
                                                                                :end-date    end-date
                                                                                :first-day   first-day}}))))
                                            ))))))))
