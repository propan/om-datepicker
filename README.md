# om-datepicker [![Build Status](https://travis-ci.org/propan/om-datepicker.svg)](https://travis-ci.org/propan/om-datepicker)

a collection of various date/month picker components for [Om][0].

[Live demo] [1]

## Usage

Include the library in your leiningen project dependencies:

```clojure
[om-datepicker "0.0.5"]
```

An example LESS file that can be used to customize components can be found at `examples/less/datepicker.less`.

### Datepicker

```clojure
(ns om-datepicker.examples.app
  (:require [om.core :as om :include-macros true]
            [om-datepicker.components :refer [datepicker]]))

(defonce app-state
  (atom {:datepicker  {:value (js/Date.)}}))

(om/root
 datepicker
 app-state
 {:path   [:datepicker]
  :target (js/document.getElementById "datepicker-demo")})
```

**Optional parameters:**

* :min-date    - if set, picking a date from the past is limited by that date. Can be a date or a number of days from today.
* :max-date    - if set, picking a date from the future is limited by that date. Can be a date or a number of days from today.
* :first-day   - the first day of the week. Default: 1 (Monday)
* :result-ch   - if passed, then picked values are put in that channel instead of :value key of the cursor.
* :style       - the style that will be applied to the string representations of days of the week. Possible values are :short, :medium and :long. Default value is :medium.

### Datepicker Panel

```clojure
(ns om-datepicker.examples.app
  (:require [om.core :as om :include-macros true]
            [om-datepicker.components :refer [datepicker-panel]]))

(defonce app-state
  (atom {:date-panel  {:value (js/Date.)}}))

(om/root
 datepicker-panel
 app-state
 {:path   [:date-panel]
  :opts   {:min-date -15
           :max-date  15}
  :target (js/document.getElementById "datepicker-panel")})
```

**Optional parameters:**

* :min-date    - if set, picking a date from the past is limited by that date. Can be a date or a number of days from today.
* :max-date    - if set, picking a date from the future is limited by that date. Can be a date or a number of days from today.
* :first-day   - the first day of the week. Default: 1 (Monday)
* :result-ch   - if passed, then values are put in that channel instead of :value key of the cursor.
* :style       - the style that will be applied to the string representations of days of the week. Possible values are :short, :medium and :long. Default value is :medium.

### Monthpicker

```clojure
(ns om-datepicker.examples.app
  (:require [om.core :as om :include-macros true]
            [om-datepicker.components :refer [monthpicker-panel]]))

(defonce app-state
  (atom {:month-panel {}}))

(om/root
 monthpicker-panel
 app-state
 {:path   [:month-panel]
  :opts   {:max-date (js/Date. 2015 3 0)}
  :target (js/document.getElementById "monthpicker-panel")})
```

**Optional parameters:**

* :min-date    - if set, picking a date from the past is limited by that date. Can be a date or a number of days from today.
* :max-date    - if set, picking a date from the future is limited by that date. Can be a date or a number of days from today.
* :value-ch    - if set, the picker value is updated with the values from that channel.
* :result-ch   - if passed, then picked values are put in that channel instead of :value key of the cursor.
* :value       - initial value, used when there is no value in :value cursor.

### Rangepicker

```clojure
(ns om-datepicker.examples.app
  (:require [om.core :as om :include-macros true]
            [om-datepicker.components :refer [rangepicker]]))

(defonce app-state
  (atom {:range {:start (js/Date. 2015 4 10)
                 :end   (js/Date. 2015 4 20)}}))

(om/root
 rangepicker
 app-state
 {:path   [:range]
  :opts   {:max-date 180}
  :target (js/document.getElementById "rangepicker")})
```

**Optional parameters:**

* :min-date    - if set, picking a date from the past is limited by that date. Can be a date or a number of days from today.
* :max-date    - if set, picking a date from the future is limited by that date. Can be a date or a number of days from today.
* :first-day   - the first day of the week. Default: 1 (Monday)
* :result-ch   - if passed, then picked values are put in that channel as a map with :start and :end keys, otherwise they will
be put in the :start and :end keys of the cursor.

## License

Copyright © 2015 Pavel Prokopenko

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[0]: http://github.com/swannodette/om
[1]: http://propan.github.io/om-datepicker/
