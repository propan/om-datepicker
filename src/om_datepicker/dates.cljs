(ns om-datepicker.dates)

(defn today
  []
  (let [now (js/Date.)]
    (js/Date. (.getFullYear now) (.getMonth now) (.getDate now) 0 0 0 0)))

(defn first-of-month
  [date]
  (doto (js/Date. date)
    (.setDate 1)))

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
