(ns om-datepicker.components
  (:require-macros [cljs.core.async.macros :refer [go-loop alt!]])
  (:require [cljs.core.async :as async :refer [chan put! sliding-buffer]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-datepicker.dates :as d :refer [after? before? between? is-future?]]
            [om-datepicker.events :refer [mouse-click-listen mouse-click-unlisten]]))

(def days
  {:short  ["S" "M" "T" "W" "T" "F" "S"]
   :medium ["Su" "Mo" "Tu" "We" "Th" "Fr" "Sa"]
   :long   ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat"]})

(def months
  {:short ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"]
   :long  ["January" "February" "March" "April" "May" "June"
           "July" "August" "September" "October" "November" "December"]})

;;
;; Date Formatting
;;

(defn- to-month-format
  [date]
  (str (get (:long months) (.getMonth date)) " " (.getFullYear date)))

(defn- to-day-format
  [date style]
  (let [labels (get days style)]
    (str (get labels (.getDay date)) ", " (.getDate date) " " (get (:short months) (.getMonth date)))))

(defn- to-date-format
  [date]
  (str (get (:short months) (.getMonth date)) " " (.getDate date) ", " (.getFullYear date)))

;;
;; Grid Generation
;;

(defn- calendar-start-date
  [month-date first-day]
  (let [month-date (doto (js/Date. month-date) (.setDate 1))
        day        (.getDay month-date)
        offset     (- day (dec first-day))
        offset     (if (pos? offset) offset (+ offset 7))]
    (.setDate month-date (- (.getDate month-date) offset))
    month-date))

(defn- generate-month-gridline
  [month-date selection-start selection-end min-date max-date first-day instant-only?]
  (let [sliding-date (calendar-start-date month-date first-day)]
    (for [week (range 6)
          day  (range 7)]
      (let [sliding-date (d/switch-date! sliding-date 1)
            allowed?     (and (or (nil? min-date)
                                  (before? min-date sliding-date))
                              (or (nil? max-date)
                                  (before? sliding-date max-date)))
            day          (.getDay sliding-date)
            same-month?  (= (.getMonth month-date) (.getMonth sliding-date))]
        {:class    (str "cell"
                        (when-not allowed?
                          " disabled")
                        (when (and same-month?
                                   (between? sliding-date selection-start selection-end))
                          " selected")
                        (when same-month?
                          " instant")
                        (when (or (= day 0) (= day 6))
                          " weekend"))
         :allowed? allowed?
         :date     (d/date-instance sliding-date)
         :text     (when (or same-month? (not instant-only?))
                     (.getDate sliding-date))}))))

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

;;
;; Sub-Components
;;

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
                      :onTouchEnd   #(.preventDefault %)
                      :onMouseEnter (when allowed?
                                      #(om/set-state! owner :highlighted true))
                      :onMouseLeave (when allowed?
                                      #(om/set-state! owner :highlighted nil))} text)))))

(defn- month-cell
  [{:keys [lable value]} owner {:keys [select-ch]}]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "month"
                    :onClick    #(put! select-ch value)}
               lable))))

(defn- gridline
  [{:keys [value min-date max-date selection-start selection-end]} owner {:keys [first-day instant-only? select-ch style]
                                                                          :or   {first-day 1 instant-only? false style :short}
                                                                          :as   opts}]
  (reify
    om/IRender
    (render [_]
      (let [calendar (generate-month-gridline value selection-start selection-end min-date max-date first-day instant-only?)]
        (apply dom/div #js {:className "gridline"}
               ;; day names
               (apply dom/div #js {:className "days"}
                      (let [days (take 7 (drop first-day (cycle (get days style))))]
                        (for [day days]
                          (dom/div #js {:className "cell"} day))))
               (for [week (partition 7 calendar)]
                 (apply dom/div #js {:className "week"}
                        (for [day week]
                          (om/build calendar-cell day
                                    {:shared {:select-ch select-ch}})))))))))

;;
;;
;;

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

(defn- corrected-month-start
  [month-date min-date]
  (let [start-date (d/first-of-month month-date)]
    (cond
     (nil? min-date)                     start-date
     (before? min-date start-date)       start-date
     (d/same-month? min-date start-date) min-date
     :else                               nil)))

(defn- corrected-month-end
  [month-date max-date]
  (let [end-date (d/last-of-month month-date)]
    (cond
     (nil? max-date)                   end-date
     (before? end-date max-date)       end-date
     (d/same-month? max-date end-date) max-date
     :else                             nil)))

;;
;; Component
;;

(defn monthpicker-panel
  "Creates a month-picker panel component.

   opts - a map of options. The following keys are supported:

     :min-date    - if set, picking a month from the past is limited by that date.
                    Can be a date or a number of days from today.
     :max-date    - if set, picking a month from the future is limited by that date.
                    Can be a date or a number of days from today.
     :value-ch    - if set, the picker value is updated with the values from that channel.
     :result-ch   - if passed, then picked values are put in that channel instead of :value key of the cursor.
     :value       - initial value, used when there is no value in :value cursor.

   Example:

     (om/build monthpicker-panel app
            {:opts {:min-date   ...
                    :max-date   ...
                    :result-ch  ...}})
  "
  [cursor owner {:keys [min-date max-date value-ch result-ch value]
                 :or   {value (d/current-month)}}]
  (let [min-date (d/coerse-date min-date)
        max-date (d/coerse-date max-date)]
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
              next            (d/next-month value)
              previous        (d/previous-month value)
              can-go-back?    (or (nil? min-date)
                                  (before? min-date previous)
                                  (d/same-month? previous min-date))
              can-go-forward? (or (nil? max-date)
                                  (before? next max-date)
                                  (d/same-month? next max-date))]
          (dom/div #js {:className "month-panel navigation"}
                   (dom/div #js {:className (str "control left" (when-not can-go-back? " disabled"))
                                 :onClick   (when can-go-back?
                                              #(monthpicker-change-month cursor owner d/previous-month result-ch))} "")
                   (dom/div #js {:className "label"} (to-month-format value))
                   (dom/div #js {:className (str "control right" (when-not can-go-forward? " disabled"))
                                 :onClick   (when can-go-forward?
                                              #(monthpicker-change-month cursor owner d/next-month result-ch))} "")))))))

(defn datepicker-panel
  "Creates a date-picker panel component.

   opts - a map of options. The following keys are supported:

     :min-date    - if set, picking a date from the past is limited by that date.
                    Can be a date or a number of days from today.
     :max-date    - if set, picking a date from the future is limited by that date.
                    Can be a date or a number of days from today.
     :first-day   - the first day of the week. Default: 1 (Monday)
     :result-ch   - if passed, then values are put in that channel instead of :value key of the cursor.
     :style       - the style that will be applied to the string representations of days of the week.
                    Possible values are :short, :medium and :long. Default value is :medium.

   Example:

     (om/build datepicker-panel app
            {:opts {:min-date    ...
                    :max-date    ...
                    :first-day   0
                    :result-ch   ...
                    :style       :long}})
  "
  [cursor owner {:keys [min-date max-date first-day result-ch style]
                 :or   {first-day 1 style :medium}}]
  (let [min-date (d/coerse-date min-date)
        max-date (d/coerse-date max-date)]
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
        (let [selected (:value cursor)]
          (dom/div #js {:className "gridline-wrapper"}
                 (om/build monthpicker-panel
                           {:value value}
                           {:opts {:min-date  min-date
                                   :max-date  max-date
                                   :result-ch month-change-ch}})
                 (om/build gridline {:value           value
                                     :min-date        min-date
                                     :max-date        max-date
                                     :selection-start selected
                                     :selection-end   selected}
                           {:opts {:first-day     first-day
                                   :select-ch     select-ch
                                   :style         style}})))))))

(defn datepicker
  "Creates a date-picker component.

   opts - a map of options. The following keys are supported:

     :min-date    - if set, picking a date from the past is limited by that date.
                    Can be a date or a number of days from today.
     :max-date    - if set, picking a date from the future is limited by that date.
                    Can be a date or a number of days from today.
     :first-day   - the first day of the week. Default: 1 (Monday)
     :result-ch   - if passed, then picked values are put in that channel instead of :value key of the cursor.
     :style       - the style that will be applied to the string representations of days of the week.
                    Possible values are :short, :medium and :long. Default value is :medium.

   Example:

     (om/build datepicker app
            {:opts {:min-date    ...
                    :max-date    ...
                    :first-day   0
                    :result-ch   ...
                    :style       :long}})
  "
  [cursor owner {:keys [min-date max-date first-day result-ch style]
                 :or   {first-day 1 style :medium}}]
  (reify
    om/IInitState
    (init-state [_]
      (let [{mouse-ch :ch mouse-listener :listener-key} (mouse-click-listen)]
        {:expanded       false
         :mouse-listener mouse-listener
         :mouse-click-ch mouse-ch
         :select-ch      (chan (sliding-buffer 1))
         :kill-ch        (chan (sliding-buffer 1))}))

    om/IWillMount
    (will-mount [_]
      (let [{:keys [kill-ch mouse-click-ch select-ch]} (om/get-state owner)]
        (go-loop []
                 (let [[v ch] (alts! [kill-ch mouse-click-ch select-ch] :priority true)]
                   (condp = ch
                     mouse-click-ch (do
                                      (when (and (om/mounted? owner)
                                                 (not (.contains (om/get-node owner) (.-target v))))
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
      (mouse-click-unlisten (om/get-state owner :mouse-listener))
      (put! (om/get-state owner :kill-ch) true))

    om/IRenderState
    (render-state [_ {:keys [highlighted expanded select-ch]}]
      (dom/div #js {:className "datepicker"}
               (dom/input #js {:type         "text"
                               :readOnly     "readonly"
                               :value        (to-day-format (:value cursor) :long)
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
                                                    {:opts {:min-date  min-date
                                                            :max-date  max-date
                                                            :first-day first-day
                                                            :result-ch select-ch
                                                            :style     style}}))))))))

(defn rangepicker
  "Creates a range-picker component.

   opts - a map of options. The following keys are supported:

     :min-date    - if set, the begining of a possible range is limited by that date.
                    Can be a date or a number of days from today.
     :max-date    - if set, the ending of a possible range is limited by that date.
                    Can be a date or a number of days from today.
     :first-day   - the first day of the week. Default: 1 (Monday)
     :result-ch   - if passed, then picked values are put in that channel as a map with
                    :start and :end keys, otherwise they will be put in the :start and
                    :end keys of the cursor.

   Example:

     (om/build rangepicker app
            {:opts {:min-date   ...
                    :max-date   ...
                    :first-day  0}
                    :result-ch  ...})
  "
  [cursor owner {:keys [min-date max-date first-day result-ch]
                 :or   {first-day 1}}]
  (let [min-date (d/coerse-date min-date)
        max-date (d/coerse-date max-date)]
    (reify
      om/IInitState
      (init-state [_]
        (let [{mouse-ch :ch mouse-listener :listener-key} (mouse-click-listen)]
          {:expanded        false
           :mode            :start
           :start           (get cursor :start (d/today))
           :end             (get cursor :end (d/today))
           :selected-start  (get cursor :start (d/today))
           :selected-end    (get cursor :end (d/today))
           :mouse-click-ch  mouse-ch
           :mouse-listener  mouse-listener
           :month-select-ch (chan (sliding-buffer 1))
           :select-ch       (chan (sliding-buffer 1))
           :kill-ch         (chan (sliding-buffer 1))}))

      om/IWillMount
      (will-mount [_]
        (let [{:keys [kill-ch mouse-click-ch month-select-ch select-ch]} (om/get-state owner)]
          (go-loop []
                   (let [[v ch] (alts! [kill-ch mouse-click-ch month-select-ch select-ch] :priority true)]
                     (condp = ch
                       month-select-ch (let [start-date (corrected-month-start v min-date)
                                             end-date   (corrected-month-end v max-date)]
                                         (when (and start-date end-date)
                                           (doto owner
                                             (om/set-state! :start start-date)
                                             (om/set-state! :end   end-date)
                                             (om/set-state! :mode  :start)))
                                         (recur))
                       mouse-click-ch  (do
                                         (when (and (om/mounted? owner)
                                                    (not (.contains (om/get-node owner) (.-target v))))
                                           (om/set-state! owner :expanded false))
                                         (recur))
                       select-ch       (let [mode (:mode (om/get-state owner))]
                                         (if (= :start mode)
                                           (doto owner
                                             (om/set-state! :start v)
                                             (om/set-state! :mode :end)
                                             (om/update-state! :end #(if (before? % v) v %)))
                                           (doto owner
                                             (om/set-state! :end v)
                                             (om/set-state! :mode :start)))
                                         (recur))
                       kill-ch         (do
                                         (async/close! month-select-ch)
                                         (async/close! mouse-click-ch)
                                         (async/close! select-ch)
                                         (async/close! kill-ch))
                       nil)))))

      om/IWillUnmount
      (will-unmount [_]
        (mouse-click-unlisten (om/get-state owner :mouse-listener))
        (put! (om/get-state owner :kill-ch) true))

      om/IRenderState
      (render-state [_ {:keys [expanded highlighted grid-date mode start end select-ch month-select-ch selected-start selected-end]}]
        (let [grid-date    (or grid-date
                               (if (= mode :start) start end))
              months-range (generate-months-range grid-date)
              is-modified? (not (and (= start selected-start)
                                     (= end   selected-end)))]
          (dom/div #js {:className "rangepicker"}
                   (dom/input #js {:type         "text"
                                   :readOnly     "readonly"
                                   :value        (str (to-date-format (:start cursor)) " - " (to-date-format (:end cursor)))
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
                                                                                :value     (to-date-format start)
                                                                                :className (str "daterange-input"
                                                                                                (when (= mode :start) " highlighted"))
                                                                                :onClick   #(om/set-state! owner :mode :start)})
                                                                " - "
                                                                (dom/input #js {:type      "text"
                                                                                :readOnly  "readonly"
                                                                                :value     (to-date-format end)
                                                                                :className (str "daterange-input"
                                                                                                (when (= mode :end) " highlighted"))
                                                                                :onClick   #(om/set-state! owner :mode :end)}))
                                                       (dom/div #js {:className "buttons-panel"}
                                                                (dom/span #js {:className (str "button" (when-not is-modified? " disabled"))
                                                                               :onClick   (when is-modified?
                                                                                            #(do
                                                                                               (doto owner
                                                                                                 (om/set-state! :expanded false)
                                                                                                 (om/set-state! :mode :start)
                                                                                                 (om/set-state! :selected-start start)
                                                                                                 (om/set-state! :selected-end end))
                                                                                               (if result-ch
                                                                                                 (put! result-ch {:start start
                                                                                                                  :end end})
                                                                                                 (doto cursor
                                                                                                   (om/update! [:start] start)
                                                                                                   (om/update! [:end] end)))))}
                                                                          "Apply")
                                                                (dom/span #js {:className (str "button" (when-not is-modified? " disabled"))
                                                                               :onClick   (when is-modified?
                                                                                            #(doto owner
                                                                                               (om/set-state! :mode :start)
                                                                                               (om/set-state! :start (:start cursor))
                                                                                               (om/set-state! :end   (:end cursor))))}
                                                                          "Cancel")))
                                              (dom/div #js {:className "calendar-panel"}
                                                       (apply dom/div #js {:className "months navigation"}
                                                              ;; TODO: can it be simpler?
                                                              (concat
                                                               [(dom/div #js {:className "control left"
                                                                              :onClick   #(om/set-state! owner :grid-date (d/previous-month grid-date))})]
                                                               (om/build-all month-cell months-range
                                                                             {:opts {:select-ch month-select-ch}})
                                                               [(dom/div #js {:className "control right"
                                                                              :onClick   #(om/set-state! owner :grid-date (d/next-month grid-date))})]))
                                                       (apply dom/div #js {:className "gridlines"}
                                                              (for [month months-range]
                                                                (om/build gridline {:value           (:value month)
                                                                                    :min-date        (if (= mode :start) min-date start)
                                                                                    :max-date        max-date
                                                                                    :selection-start start
                                                                                    :selection-end   end}
                                                                          {:opts {:first-day     first-day
                                                                                  :instant-only? true
                                                                                  :select-ch     select-ch}})))))))))))))
