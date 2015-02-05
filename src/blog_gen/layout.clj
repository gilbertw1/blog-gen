(ns blog-gen.layout
  (:require [clojure.string :as str]
            [hiccup.page :refer [html5]]
            [optimus.link :as link]
            [clj-time.format :as tf]
            [clj-time.core :as t]))

(defn monthf [date]
  (tf/unparse (tf/formatter "MMM") date))

(defn dayf [date]
  (tf/unparse (tf/formatter "dd") date))

(defn yearf [date]
  (tf/unparse (tf/formatter "yyyy") date))


(defn main [request content]
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

      [:div#main content]

      [:footer {:role "contentinfo"}
        [:p
          "Website Copyright © " (t/year (t/now)) " - Bryan Gilbert &nbsp; | &nbsp; "
          [:span.credit "Powered by " [:a {:href "http://github.com/gilbertw1/blog-gen"} " a Little Side Project"]
          " &nbsp; | &nbsp; Mostly Themed with " [:a {:href "https://github.com/TheChymera/Koenigspress"} "Königspress"]]]]]))

(defn post [request {:keys [title tags date path disqus-path content]}]
  (main request
    [:div#content
      [:article.hentry {:role "article"}
        [:header
          [:h1.entry-title title]
          (when date
            [:p.meta
              [:time {:datetime date} (monthf date) " " (dayf date) [:span "th"] ", " (yearf date)]])]
        [:div.body.entry-content content]
        [:section
          [:h1 "Comments"
            [:div#disqus_thread {:aria-live "polite"}]
            [:script {:type "text/javascript"}
              (str "var disqus_shortname = 'bryangilbertsblog';
                    var disqus_url = 'http://bryangilbert.com" disqus-path "';
                    (function() {
                        var dsq = document.createElement('script'); dsq.type = 'text/javascript'; dsq.async = true;
                        dsq.src = '//' + disqus_shortname + '.disqus.com/embed.js';
                        (document.getElementsByTagName('head')[0] || document.getElementsByTagName('body')[0]).appendChild(dsq);
                    })();")]
            [:noscript "Please enable JavaScript to view the " [:a {:href "http://disqus.com/?ref_noscript"} "comments powered by Disqus."]]
            [:a.dsq-brlink {:href "http://disqus.com"} "comments powered by " [:span.logo-disqus "Disqus"]]]]]]))

(defn redirect [request page]
  (html5
    [:head
     [:meta {:http-equiv "refresh" :content (str "0; url=" page)}]]))

(defn home [request posts]
  (let [{:keys [title tags date path disqus-path content]} (->> posts (sort-by :date) reverse first)]
    (main request
      [:div#content
        [:div.blog-index
          [:article
            [:header
              [:h1.entry-title [:a {:href path} title]]
              (when date
                [:p.meta
                  [:time {:datetime date} (monthf date) " " (dayf date) [:span "th"] ", " (yearf date)]])]
            [:div.body.entry-content content]
            [:div.pagination
              [:a {:href "/archive"} "Blog Archive"]]]]])))

(defn- make-tag-link [tag]
  [:a {:href (str "/tags/" tag)} tag])

(defn- make-tag-links [tags]
  (reduce #(conj %1 ", " (make-tag-link %2)) (make-tag-link (first tags)) (rest tags)))

(defn- archive-post [{:keys [title date tags path]}]
  [:article
    [:h1 [:a {:href path} title]]
    [:time {:datetime date}
      [:span.month (monthf date)] " "
      [:span.day (dayf date)]
      [:span.year (yearf date)]]
    (when (not-empty tags)
      [:span.categories "Tags: " (make-tag-links tags)])])

(defn- archive-group [[year posts]]
  (let [sorted-posts (reverse (sort-by :path posts))]
    (cons
      [:h2 year]
      (map archive-post sorted-posts))))

(defn archive-like [request posts title]
  (let [post-groups (->> posts (group-by #(t/year (:date %))) (sort-by first) reverse)]
    (main request
      [:div#content
        [:article.hentry {:role "article"}
          [:header
            [:h1.entry-title title]]
          [:div.body.entry-content
            [:div#blog-archives
              (map archive-group post-groups)]]]])))

(defn archive [request posts]
  (archive-like request posts "Archive"))

(defn tag [request posts tag]
  (archive-like request posts tag))

(defn tag-post [{:keys [path title]}]
  [:article
   [:h1 [:a {:href path} title]]])

(defn tag-entry [tag posts]
  (let [sorted-posts (reverse (sort-by :path posts))]
    (cons
      [:h2 tag]
      (map tag-post sorted-posts))))

(defn tags [request posts]
  (let [unique-tags (->> posts (map :tags) flatten distinct sort)]
    (main request
      [:div#content
        [:article.hentry {:role "article"}
          [:header
            [:h1.entry-title "Tags"]]
          [:div.body.entry-content
            [:div#blog-archives
              (map #(tag-entry % posts) unique-tags)]]]])))