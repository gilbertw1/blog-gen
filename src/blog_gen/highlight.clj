(ns blog-gen.highlight
  (:require [clojure.java.io :as io]
            [clygments.core :as pygments]
            [net.cgrand.enlive-html :as enlive]))

(defn- extract-code
  [highlighted]
  (-> highlighted
      java.io.StringReader.
      enlive/html-resource
      (enlive/select [:pre])
      first
      :content))

(defn- highlight [node]
  (let [code (->> node :content (apply str))
        lang (->> node :attrs :class keyword)]
    (assoc node :content (-> code (pygments/highlight lang :html) extract-code))))

(defn highlight-code-blocks [page]
  (enlive/sniptest page
    [:pre :code] highlight
    [:pre :code] #(assoc-in % [:attrs :class] "codehilite")))

(defn drop-comments [html replace-with]  
  (let [res (-> html java.io.StringReader. enlive/html-resource)
        without-comments (enlive/transform res [:section] (enlive/substitute (or (-> replace-with java.io.StringReader. enlive/html-resource) "")))]
    (apply str (enlive/emit* without-comments))))

(defn replace-comments [html replace-with]
  (let [replacement (-> replace-with java.io.StringReader. enlive/html-resource)]
    (enlive/sniptest html [:section] (fn [c] replacement))))