(ns frontend.augmentation.core
  (:require
   [clojure.string :as string]
   [frontend.db :as db]
   [frontend.db.model :as db-model]
   [frontend.state :as state]
   [frontend.util.property :as property]
   [frontend.handler.editor :as editor-handler]))

(defn- get-page-by-name
  "Get a page object by its name.
   deprecated: turns out db-model/get-page does this already"
  [^String page-name]
  (db/entity [:block/name (string/lower-case page-name)]))

(defn insert-first-page-block-if-not-exists!
  "An augmented version of the existing insert-first-page-block-if-not-exists!
  which can be found in editor handlers. This version of the function simply
  handles checking for an empty page before passing args on to the insertion
  API."
  [content opts]
  (let [page-name (:page opts)]
    (when (string? page-name)
      (when-let [page (db/entity [:block/name (string/lower-case page-name)])]
        (when (db/page-empty? (state/get-current-repo) (:db/id page))
          (editor-handler/api-insert-new-block! content opts))))))

(defn get-first-child-block
  [block]
  (let [children (:block/_parent block)
        blocks (db/sort-by-left children block)
        first-block-id (:db/id (first blocks))]
    (-> first-block-id (db/pull))))

(defn append-page-properties!
  [page-name kvs]
  (let [page (db-model/get-page page-name)
        properties-block (or (get-first-child-block page)
                             (insert-first-page-block-if-not-exists! "" {:page page-name}))
        block-uuid (:block/uuid properties-block)]
    (if-not page
      (println "Oh well no page for " page-name)
      (do
        (println "Must link page!" page-name)
        (doseq [[k v] kvs]
          (editor-handler/set-block-property! block-uuid k v))))))

;; @note Much experimenting here... this can likely be deleted at some point.
;; Example of the block insertion API
(comment

  ;; Get parent of a block or page?? I'm very confused by this. It seems to get the children of a page
  (-> (get-page-by-name "test page") :block/_parent)
  (-> (db-model/get-page "test page") :block/_parent)

  (append-page-properties! "example.com (1280x720)" {:super-block "maybe" :modified "very much"})
  (append-page-properties! "google lens - google search" {:super-block "maybe"})

  (-> "google lens - google search" get-page-by-name :db/id (db/pull))
  (-> "google meet - google search" get-page-by-name :db/id (db/pull))
  (insert-first-page-block-if-not-exists! "" {:page "google meet - google search" :properties {:programatically-inserted "yes"}})
  (append-page-properties! "google meet - google search" {:super-block "maybe"})

  (-> (get-page-by-name "test page")
      get-first-child-block
      :block/uuid)

  (-> (get-page-by-name "test page") :block/_parent)

  ;; Example from api-insert-new-block
  (let [block (get-page-by-name "test page")
        children (:block/_parent block)
        blocks (db/sort-by-left children block)
        first-block-id (:db/id (first blocks))
        last-block-id (:db/id (last blocks))]
    (-> first-block-id (db/pull) :block/content))

  (-> (get-page-by-name "test page") :block/properties)

  (property/insert-properties :markdown "Some content" {:hey "you"})

  ;; When using a short id, i.e. an 'eid', use db/pull
  (db/pull 2461)

  ;; Blocks can change their name, but it seems they keep the original
  (-> (get-page-by-name "test page")
      (select-keys [:block/original-name
                    :block/name]))

  (-> (get-page-by-name "test page")
      (get :block/_parent))

  ;; Check if empty
  (-> (get-page-by-name "test page")
      :db/id
      (->> (db/page-empty? (state/get-current-repo))))

  ;; Insert a new block into the page named "test page"
  (editor-handler/api-insert-new-block! (str "Habberdash" (js/Math.random)) {:page "test page"})
  (editor-handler/api-insert-new-block! (str "Habberdash With Props " (js/Math.random)) {:page "test page" :properties {:randomly-generated "partially"}})

  ;; Works with UTF8 chars (just had to make sure)
  (editor-handler/api-insert-new-block! "Habberdash" {:page "測試頁"})

  ;;
  )
