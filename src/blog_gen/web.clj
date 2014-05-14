(ns blog-gen.web
  (:require [blog-gen.highlight :refer [highlight-code-blocks replace-comments]]
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
            [clj-time.format :as tf]
            [clj-time.core :as t]))

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

(def old-style-posts
  ["rx-the-importance-of-honoring-unsubscribe"
   "rxPlay-making-iteratees-and-observables-play-nice"
   "anatomy-of-a-clojure-macro"
   "escaping-callback-hell-with-core-async"
   "action-composition-auth"
   "anorm-pk-json"])

(defn get-disqus-path [path]
  (if (some #(re-seq (re-pattern %) path) old-style-posts)
    (str (str/replace path #"blog" "code") "/")
    path))

(defn prepare-post-path [post-name]
  (-> post-name
      (str/replace #"\.md$" "")
      (str/replace #"(\d\d\d\d)-(\d\d)-(\d\d)-" "blog/$1/$2/$3/")))

(defn layout-page [request [path page] {:keys [date title tags]}]
  (html5
    [:head
      [:meta {:name "HandheldFriendly" :content "True"}]
      [:meta {:name "MobileOptimized" :content "320"}]      
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
      [:meta {:charset "utf-8"}]
      [:title "Random.next()"]      
      [:link {:rel "stylesheet" :href (link/file-path request "/css/theme.css")}]
      [:link {:rel "stylesheet" :href (link/file-path request "/css/zenburn-custom.css")}]
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
      [:script "
        (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
        (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
        m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
        })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

        ga('create', 'UA-42597902-1', 'bryangilbert.com');
        ga('send', 'pageview');"]
      
    [:body
     [:a {:href "https://github.com/gilbertw1"}
        [:img {:style "position: absolute; top: 0; right: 0; border: 0;"
               :src "https://s3.amazonaws.com/github/ribbons/forkme_right_gray_6d6d6d.png"
               :alt "Fork me on GitHub"}]]
     
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
          [:li [:a {:href "/resume.html"} "Resume"]]]]
      
      [:div#main
        [:div#content
          [:article.hentry {:role "article"}
            [:header
              [:h1.entry-title title]
              (if date
                [:p.meta
                  [:time {:datetime date} (format-month date) " " (format-day date) [:span "th"] ", " (format-year date)]])]
            [:div.body.entry-content page]
            (when date
              [:section
                [:h1 "Comments"
                  [:div#disqus_thread {:aria-live "polite"}]
                  [:script {:type "text/javascript"}
                    (str "var disqus_shortname = 'bryangilbertsblog';
                          var disqus_url = 'http://bryangilbert.com" (get-disqus-path (prepare-post-path path)) "';
                          (function() {
                              var dsq = document.createElement('script'); dsq.type = 'text/javascript'; dsq.async = true;
                              dsq.src = '//' + disqus_shortname + '.disqus.com/embed.js';
                              (document.getElementsByTagName('head')[0] || document.getElementsByTagName('body')[0]).appendChild(dsq);
                          })();")]
                  [:noscript "Please enable JavaScript to view the " [:a {:href "http://disqus.com/?ref_noscript"} "comments powered by Disqus."]]
                  [:a.dsq-brlink {:href "http://disqus.com"} "comments powered by " [:span.logo-disqus "Disqus"]]]])]]]
      [:footer {:role "contentinfo"}
        [:p 
          "Website Copyright © " (t/year (t/now)) " - Bryan Gilbert &nbsp; | &nbsp; "
          [:span.credit "Powered by " [:a {:href "http://github.com/gilbertw1/blog-gen"} " a Little Side Project"]
          " &nbsp; | &nbsp; Mostly Themed with " [:a {:href "https://github.com/TheChymera/Koenigspress"} "Königspress"]]]]]))

(defn partial-pages [pages]
  (zipmap (keys pages) (map #(fn [req] (layout-page req %)) pages)))

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

(defn render-post-page [[path page]]
  (fn [req] (layout-page req [path (md/to-html (remove-meta page) pegdown-options)] (extract-meta path page))))

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
        [:span.year (format-year date)]]
      (when (not-empty tags)
        [:span.categories "Tags: " (str/join ", " tags)])]))

;<span class="categories">posted in <a class="category" href="/Blog/categories/appnexus/">appnexus</a></span>

(defn archive-group [[year posts]]
  (let [sorted-posts (reverse (sort-by first posts))]
    (cons
      [:h2 year]
      (map archive-post sorted-posts))))

(defn archive-layout [posts]
  (let [post-groups (->> posts (group-by #(take 4 (drop 6 (first %)))) (sort-by first) reverse)]
    [:div#blog-archives
      (map archive-group post-groups)]))

(def home-footer-html
  (html5
    [:div.pagination
      [:a {:href "/archive"} "Blog Archive"]]))

(defn home-page [posts]
  (let [page (->> posts (sort-by first) reverse first second)]
    (fn [req]
      (replace-comments (page req) home-footer-html))))

(defn archive-page [posts]
  (fn [req] (layout-page req ["/archive" (archive-layout posts)] {:title "Archive"})))

(defn is-old-post [post]
  (some #(re-seq (re-pattern %) (first post)) old-style-posts))

(defn create-old-links [posts]
  (let [old-posts (filter is-old-post posts)]
    (into {} 
      (for [[path page] old-posts] 
        [(str/replace path #"blog" "code") page]))))

(defn create-dynamic-pages [posts]
  {"/index.html" (home-page posts)
   "/blog/index.html" (home-page posts)
   "/archive/index.html" (archive-page posts)})

(defn get-raw-pages []
  (let [post-pages (slurp-resource "posts" :markdown)]
    (stasis/merge-page-sources
      {:public (slurp-resource "public" :html)
       :partials (slurp-resource "partials" :partial)
       :posts post-pages
       :dynamic (create-dynamic-pages post-pages)
       :old (create-old-links post-pages)})))

(defn prepare-page [page req]
  (-> (if (string? page) page (page req))
      highlight-code-blocks))

(defn prepare-pages [pages]
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