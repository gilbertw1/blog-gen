(ns blog-gen.web
  (:require [blog-gen.highlight :refer [highlight-code-blocks]]
            [optimus.link :as link]
            [optimus.assets :as assets]
            [optimus.optimizations :as optimizations]
            [optimus.prime :as optimus]
            [optimus.strategies :refer [serve-live-assets]]
            [optimus.export]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup.page :refer [html5]]
            [me.raynes.cegdown :as md]
            [stasis.core :as stasis]
            [clj-time.format :as tf]))

(def export-dir "dist")

(def mformat (tf/formatter "MMM"))
(def dformat (tf/formatter "dd"))
(def yformat (tf/formatter "yyyy"))

(defn format-month [date]
  (tf/unparse mformat date))

(defn format-day [date]
  (tf/unparse dformat date))

(defn format-year [date]
  (tf/unparse yformat date))

(defn layout-page [request page {:keys [date title tags]}]
  (html5
    [:head
      [:meta {:name "HandheldFriendly" :content "True"}]
      [:meta {:name "MobileOptimized" :content "320"}]      
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
      [:meta {:charset "utf-8"}]
      [:title "Random.next()"]
      [:link {:rel "stylesheet" :href (link/file-path request "/css/gilbertw1.css")}]
      [:link {:rel "stylesheet" :href (link/file-path request "/css/screen.css")}]
      [:link {:href "http://fonts.googleapis.com/css?family=PT+Serif:regular,italic,bold,bolditalic" :rel "stylesheet" :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=PT+Sans:regular,italic,bold,bolditalic" :rel "stylesheet" :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Poller+One" :rel "stylesheet" :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Germania+One" :rel "stylesheet" :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Fontdiner+Swanky" :rel "stylesheet" :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Lato" :rel "stylesheet" :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Cardo" :rel "stylesheet" :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Sorts+Mill+Goudy" :rel "stylesheet" :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=EB+Garamond" :rel "stylesheet" :type "text/css"}]
      [:link {:href "http://fonts.googleapis.com/css?family=Della+Respira" :rel "stylesheet" :type "text/css"}]]
    [:body
      [:img {:style "position: absolute; top: 0; right: 0; border: 0;"
             :src "https://s3.amazonaws.com/github/ribbons/forkme_right_gray_6d6d6d.png"
             :alt "Fork me on GitHub"}]
     
      [:header {:role "banner"}
        [:hgroup 
          [:h1
            [:a {:href "/"} "Bryan Gilbert - Random.next()"]]]]
      
      [:nav {:role "navigation"}
        [:ul.main-navigation
          [:li [:a {:href "/"} "Blog"]]
          [:li [:a {:href "/archive"} "Archive"]]
          [:li [:a {:href "http://twitter.com/bryangilbert"} "Twitter"]]
          [:li [:a {:href "http://linkedin.com/in/williamgilbert/"} "LinkedIn"]]
          [:li [:a {:href "mailto:gilbertw1@gmail.com"} "Email"]]]]
      
      [:div#main
        [:div#content
          [:article.hentry {:role "article"}
            [:header
              [:h1.entry-title title]
              (if date
                [:p.meta
                  [:time {:datetime date} (format-month date) " " (format-day date) [:span "th"] ", " (format-year date)]])]
            [:div.body.entry-content page]]]]]))

(defn partial-pages [pages]
  (zipmap (keys pages) (map #(fn [req] (layout-page req %)) (vals pages))))

(def pegdown-options
  [:autolinks :fenced-code-blocks :strikethrough])

(defn extract-meta-block [page]
  (->> page (re-seq #"(?is)^---(.*?)---") first second))

(defn extract-title [meta]
  (->> meta (re-seq #"title\s*:\s*(.*)") first second))

(defn extract-tags [meta]
  (if-let [tag-str (->> meta (re-seq #"tags\s*:\s*\[(.*?)\]") first second)]
    (str/split tag-str #",")
    []))

(def formatter (tf/formatter "yyyy-MM-dd"))

(defn extract-date [name]
  (when-let [date-str (->> name (re-seq #"(\d\d\d\d-\d\d-\d\d)") first second)]
    (tf/parse formatter date-str)))

(defn extract-meta [name page]
  (let [meta-section (extract-meta-block page)]
    {:title (or (extract-title meta-section) "Random Thought")
     :tags (extract-tags meta-section)
     :date (extract-date name)}))

(defn remove-meta [page]
  (str/replace page #"(?is)^---.*?---" ""))

(defn render-post-page [[name page]]
  (fn [req] (layout-page req (md/to-html (remove-meta page) pegdown-options) (extract-meta name page))))

(defn prepare-post-path [post-name]
  (-> post-name
      (str/replace #"\.md$" "")
      (str/replace #"(\d\d\d\d)-(\d\d)-(\d\d)-" "blog/$1/$2/$3/")))

(def post-meta-map 
  (let [raw-posts (stasis/slurp-directory "resources/posts" #".*\.md")]
    (zipmap (map prepare-post-path (keys raw-posts))
            (map #(apply extract-meta %) raw-posts))))

(defn post-pages [pages]
  (zipmap (map prepare-post-path (keys pages))
          (map render-post-page pages)))

(defn slurp-resource [path type]
  (let [dir (str "resources/" path)]
    (condp = type
      :markdown (post-pages (stasis/slurp-directory dir #".*\.md$"))
      :partial (partial-pages (stasis/slurp-directory dir #".*\.html$"))
      :html (stasis/slurp-directory dir #".*\.(html|css|js)$"))))

(defn archive-post [[path page]]
  (let [{:keys [date title tags]} (post-meta-map path)]
    [:article
      [:h1 [:a {:href path} title]]
      [:time {:datetime date}
        [:span.month (format-month date)] " "
        [:span.day (format-day date)]
        [:span.year (format-year date)]]]))

(defn archive-group [[year posts]]
  (let [sorted-posts (reverse (sort-by first posts))]
    (cons
      [:h2 year]
      (map archive-post sorted-posts))))

(defn archive-layout [posts]
  (let [post-groups (->> posts (group-by #(take 4 (drop 6 (first %)))) (sort-by first) reverse)]
    [:div#blog-archives
      (map archive-group post-groups)]))

(defn home-page [posts]
  (->> posts (sort-by first) reverse first second))

(defn archive-page [posts]
  (fn [req] (layout-page req (archive-layout posts) {:title "Archive"})))

(defn create-dynamic-pages [posts]
  {"/index.html" (home-page posts)
   "/archive/index.html" (archive-page posts)})

(defn get-raw-pages []
  (let [post-pages (slurp-resource "posts" :markdown)]
    (stasis/merge-page-sources
      {:public (slurp-resource "public" :html)
       :partials (slurp-resource "partials" :partial)
       :posts post-pages
       :dynamic (create-dynamic-pages post-pages)})))

(defn prepare-page [page req]
  (-> (if (string? page) page (page req))
      highlight-code-blocks))

(defn prepare-pages [pages]
  ;(doseq [[path page] pages] (println path " - " (nil? page)))
  (zipmap (keys pages) (map #(partial prepare-page %) (vals pages))))

(defn get-pages []
  (prepare-pages (get-raw-pages)))

(defn get-assets []
  (assets/load-assets "public" [#".*"]))

(def app
  (optimus/wrap (stasis/serve-pages get-pages)
                get-assets
                optimizations/all
                serve-live-assets))

(defn export []
  (let [assets (optimizations/all (get-assets) {})]
    (stasis/empty-directory! export-dir)
    (optimus.export/save-assets assets export-dir)
    (stasis/export-pages (get-pages) export-dir {:optimus-assets assets})))