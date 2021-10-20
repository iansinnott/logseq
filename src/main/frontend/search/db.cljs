(ns frontend.search.db
  (:refer-clojure :exclude [empty?])
  (:require [cljs-bean.core :as bean]
            [clojure.string :as string]
            [frontend.db :as db]
            [frontend.state :as state]
            [frontend.text :as text]
            [frontend.util.drawer :as drawer]
            ["fuse.js" :as fuse]))

(defonce indices (atom nil))

(defn empty?
  [repo]
  (nil? (get @indices repo)))

(defn block->index
  [{:block/keys [uuid content format page] :as block}]
  (when-let [result (->> (text/remove-level-spaces content format)
                         (drawer/remove-logbook))]
    {:id (:db/id block)
     :uuid (str uuid)
     :page page
     :content result}))

(defn build-blocks-indice
  [repo]
  (->> (db/get-all-block-contents)
       (map block->index)
       (remove nil?)
       (bean/->js)))

(defn make-blocks-indice!
  [repo]
  (let [blocks (build-blocks-indice repo)
        indice (fuse. blocks
                      (clj->js {:keys ["uuid" "content" "page"]
                                :shouldSort true
                                :tokenize true
                                :minMatchCharLength 1
                                :distance 1000
                                :threshold 0.35}))]
    (swap! indices assoc-in [repo :blocks] indice)
    indice))

;; The list of repositories that have been augmented already. This is to prevent
;; double augmentation of the same repo.
(def ^:private augmented (atom #{}))

(comment
  (reset! augmented #{})
  (doseq [[k v] @indices]
    (set! (.-repo js/window) (clj->js v)))
  (-> @indices keys)
  (-> @indices
      vals
      first
      (.-_docs)
      (count)))

;; @todo Remove if we don't end up using this. For now it proved too slow to add
;; URLs to the synchronous fuse index.
(comment
  (add-watch indices
             :augment-with-browsing-history
             (fn [k r o n]
               (doseq [[repo _indices] n]
                 (println "SUPER REPO INDEX" repo _indices)
                 (let [should-augment (nil? (@augmented repo))
                       page-index (get _indices :pages)]
                   (if-not should-augment
                     (println "Ignoring alrady augmented index" repo)
                     (when page-index
                       (swap! augmented conj repo)
                       (-> (js/fetch "http://localhost:5555/rest/nodes")
                           (.then (fn [res] (if (.-ok res) (.json res) (js/Promise.reject res))))
                           (.then (fn [json]
                                    (try
                                    ;; @note This is all working with plain JS.
                                    ;; No need to use up mem converting it since
                                    ;; its getting added to fuse.
                                      (let [results (.-results json)]
                                        (println "Adding results" (.-length results))
                                        (doseq [x results]
                                          (-> page-index (.add x))))
                                      (catch :default _ (js/console.error "Had an issue adding to the index.")))))
                           (.catch (fn [err] (js/console.error (ex-message err))))))))))))

(defn make-pages-indice!
  []
  (when-let [repo (state/get-current-repo)]
    (let [pages (->> (db/get-pages (state/get-current-repo))
                     (remove string/blank?)
                     (map (fn [p] {:name p}))
                     (bean/->js))
          indice (fuse. pages
                        (clj->js {:keys ["name"]
                                  :shouldSort true
                                  :tokenize true
                                  :minMatchCharLength 1
                                  :distance 1000
                                  :threshold 0.35}))]
      (swap! indices assoc-in [repo :pages] indice)
      indice)))
