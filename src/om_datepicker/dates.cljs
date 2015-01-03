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

(defn coerse-date
  [date]
  (if (number? date)
    (switch-date! (today) date)
    date))

(defprotocol DateTimeProtocol
  (after? [this that])
  (before? [this that])
  (is-future? [this]))

(extend-protocol DateTimeProtocol
  js/Date
  (after? [this that]
    (> (.getTime this) (.getTime that)))

  (before? [this that]
    (<= (.getTime this) (.getTime that)))

  (is-future? [this]
    (> (.getTime this) (.getTime (js/Date.)))))
