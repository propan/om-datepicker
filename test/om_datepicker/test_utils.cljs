(ns om-datepicker.test-utils
  (:require [cemerick.cljs.test :as t]
            [goog.dom]
            [goog.dom.classes]
            [goog.style]))

(def simulate js/React.addons.TestUtils.Simulate)

(defn click
  "Simulates a click event on the DOM node"
  [node]
  (.click simulate node))

(defn text [elem]
  (or (.-textContent elem) (.-innerText elem)))

(defn sel1
  ([selector] (sel1 js/document selector))
  ([parent selector]
     (.querySelector parent selector)))

(defn sel
  ([selector] (sel js/document selector))
  ([parent selector]
     (.querySelectorAll parent selector)))

(defn html->dom
  [html]
  (goog.dom/htmlToDocumentFragment html))

(defn has-class?
  [elem class-name]
  (goog.dom.classes.has elem class-name))

(defn classes
  [el]
  (goog.dom.classes.get el))

(defn is-shown?
  [el]
  (goog.style.isElementShown el))
