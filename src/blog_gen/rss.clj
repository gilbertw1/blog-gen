(ns blog-gen.rss
  (:require [clojure.data.xml :as xml]))

(defn- entry [post]
  [:entry
   [:title (:title post)]
   [:updated (:date post)]
   [:author [:name "Bryan Gilbert"]]
   [:link {:href (str "http://bryangilbert.com" (:path post))}]
   [:id (str "urn:bryangilbert-com:feed:post:" (:title post))]
   [:content {:type "html"} (:content post)]])

(defn atom-xml [posts]
  (let [sorted-posts (->> posts (sort-by :date) reverse)]
    (xml/emit-str
     (xml/sexp-as-element
      [:feed {:xmlns "http://www.w3.org/2005/Atom"}
       [:id "urn:bryangilbert-com:feed"]
       [:updated (-> posts first :date)]
       [:title {:type "text"} "Random.next()"]
       [:link {:rel "self" :href "http://bryangilbert.com/atom.xml"}]
       (map entry posts)]))))