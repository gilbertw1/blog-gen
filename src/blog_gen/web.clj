(ns blog-gen.web
  (:require [blog-gen.highlight :as highlight]
            [blog-gen.post :as post]
            [blog-gen.layout :as layout]
            [blog-gen.rss :as rss]
            [optimus.assets :as assets]
            [optimus.optimizations :as optimizations]
            [optimus.prime :as optimus]
            [optimus.strategies :refer [serve-live-assets]]
            [optimus.export]
            [stasis.core :as stasis]))

(def export-dir "dist")

(defn layout-post [post]
  [(:path post) (fn [req] (layout/post req post))])

(defn layout-posts [posts]
  (let [post-layouts (map layout-post posts)]
    (into {} post-layouts)))

(defn create-dynamic-pages [posts]
  {"/index.html" (fn [req] (layout/home req posts))
   "/blog/index.html" (fn [req] (layout/home req posts))
   "/archive/index.html" (fn [req] (layout/archive req posts))
   "/tags/index.html" (fn [req] (layout/tags req posts))})

(defn layout-tag-page [tag posts]
  [(str "/tags/" tag "/index.html") (fn [req] (layout/tag req posts tag))])

(defn get-unique-tags [posts]
  (->> posts (map :tags) flatten distinct sort))

(defn tag-posts [tag posts]
  (filter #((-> % :tags set) tag) posts))

(defn group-tags-posts [posts]
  (let [unique-tags (get-unique-tags posts)]
    (reduce #(assoc %1 %2 (tag-posts %2 posts)) {} unique-tags)))

(defn create-tag-pages [posts]
  (let [tags-posts (group-tags-posts posts)
        tags-layouts (map #(apply layout-tag-page %) tags-posts)]
    (into {} tags-layouts)))

(defn tag-rss [tag posts]
  [(str "/" tag "-atom.xml") (fn [req] (rss/atom-xml posts))])

(defn create-rss-pages [posts]
  (let [tags-posts (group-tags-posts posts)
        tags-rss (map #(apply tag-rss %) tags-posts)
        main-rss ["/atom.xml" (fn [req] (rss/atom-xml posts))]]
    (conj (into {} tags-rss) main-rss)))

(defn slurp-posts [dir]
  (stasis/slurp-directory (str "resources/" dir) #".*\.md$"))

(defn slurp-static [dir]
  (stasis/slurp-directory (str "resources/" dir) #".*\.(html|css|js)$"))

(defn get-raw-pages []
  (let [posts (map post/create-post (slurp-posts "posts"))
        old-posts (post/prepare-old posts)]
    (stasis/merge-page-sources
      {:public (slurp-static "public")
       :posts (layout-posts posts)
       :dynamic (create-dynamic-pages posts)
       :tags (create-tag-pages posts)
       :old (layout-posts old-posts)
       :rss (create-rss-pages posts)})))

(defn prepare-page [page req]
  (-> (if (string? page) page (page req)) highlight/highlight-code-blocks))

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