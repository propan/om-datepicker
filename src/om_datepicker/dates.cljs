(ns om-datepicker.dates)

(defn date-instance
  [date]
  (js/Date. (.getFullYear date) (.getMonth date) (.getDate date)))

(defn today
  []
  (date-instance (js/Date.)))

(defn first-of-month
  [date]
  (js/Date. (.getFullYear date) (.getMonth date) 1))

(defn- switch-date!
  [date offset]
  (.setDate date (+ (.getDate date) offset))
  date)

(defn current-month
  []
  (first-of-month (js/Date.)))

(defn next-month
  [date]
  (js/Date. (.getFullYear date) (inc (.getMonth date)) (.getDate date)))

(defn previous-month
  [date]
  (js/Date. (.getFullYear date) (dec (.getMonth date)) (.getDate date)))
