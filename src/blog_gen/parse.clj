(ns blog-gen.parse
  (:require [me.raynes.cegdown :as md]))

(def pegdown-options
  [:autolinks :fenced-code-blocks :strikethrough])

(defn markdown [md-content]
  (md/to-html md-content pegdown-options))