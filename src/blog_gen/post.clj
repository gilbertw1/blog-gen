(ns blog-gen.post
  (:require [blog-gen.parse :as parse]
            [clojure.string :as str]
            [clj-time.format :as tf]))

(def formatter (tf/formatter "yyyy-MM-dd"))

(def old-style-posts
  ["rx-the-importance-of-honoring-unsubscribe"
   "rxPlay-making-iteratees-and-observables-play-nice"
   "anatomy-of-a-clojure-macro"
   "escaping-callback-hell-with-core-async"
   "action-composition-auth"
   "anorm-pk-json"])

(defn- remove-meta [page]
  (str/replace page #"(?is)^---.*?---" ""))

(defn- extract-meta-block [page]
  (->> page (re-seq #"(?is)^---(.*?)---") first second))

(defn- extract-title [meta]
  (->> meta (re-seq #"title\s*:\s*(.*)") first second))

(defn- extract-tags [meta]
  (if-let [tag-str (->> meta (re-seq #"tags\s*:\s*\[(.*?)\]") first second)]
    (map str/trim (str/split tag-str #","))
    []))

(defn- extract-date [path]
  (when-let [date-str (->> path (re-seq #"(\d\d\d\d-\d\d-\d\d)") first second)]
    (tf/parse formatter date-str)))

(defn- prepare-path [path]
  (-> path
      (str/replace #"\.md$" "")
      (str/replace #"(\d\d\d\d)-(\d\d)-(\d\d)-" "blog/$1/$2/$3/")))

(defn- to-disqus-path [path]
  (if (some #(re-seq (re-pattern %) path) old-style-posts)
    (str (str/replace path #"blog" "code") "/")
    path))

(defn create-post [[raw-path raw-content]]
  (let [meta-section (extract-meta-block raw-content)
        content (remove-meta raw-content)
        path (prepare-path raw-path)]
    {:title (or (extract-title meta-section) "Random Thought")
     :tags (extract-tags meta-section)
     :date (extract-date raw-path)
     :path path
     :disqus-path (to-disqus-path path)
     :content (parse/markdown content)}))

(defn- is-old? [{path :path}]
  (some #(re-seq (re-pattern %) path) old-style-posts))

(defn- update-old-path [post]
  (let [old-path (str/replace (:path post) #"blog" "code")]
    (assoc post :path old-path)))

(defn prepare-old [posts]
  (let [old-posts (filter is-old? posts)]
    (map update-old-path old-posts)))