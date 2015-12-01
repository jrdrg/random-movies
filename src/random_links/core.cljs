(ns random-links.core
  (:require [reagent.core :as reagent :refer [atom]]
            [goog.net.XhrIo :as xhr]
            [cljs.core.async :as async :refer [chan close! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:import [goog.net Jsonp]
           [goog Uri]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Movies containing random words"
                          :random-word nil
                          :movies-list []
                          :url-list []}))

(def random-word-url "http://randomword.setgetgo.com/get.php")

(defn search-url [term]
  (str "http://www.omdbapi.com/?s=" term))

; normalize the data from the api since it has different structure depending on error or not
(defmulti movies-list (fn [result] [(.hasOwnProperty result "Search") (.hasOwnProperty result "Error")]))
(defmethod movies-list [true false] [result] (map (fn [i] {:type :result :item (js->clj i)}) (.-Search result)))
(defmethod movies-list [false true] [result] [{:type :error :item (.-Error result)}])

(defn GET [url]
  "Sends a GET request to the specified URL"
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                (let [response (-> event .-target .getResponseText)]
                  (go (>! ch response)
                      (close! ch)))))
    ch))

(defn get-jsonp [url]
  "Sends JSONP request and returns a channel where the result will be sent"
  (let [ch (chan)
        jsonp (Jsonp. (Uri. url))
        on-success #(go (>! ch %))
        on-failure #(go (>! ch %))]
    (.send jsonp nil on-success on-failure)
    ch))


(defn update-movie-info [results]
  (.log js/console results)
  (.log js/console (.hasOwnProperty results "Error"))
  (.log js/console (.hasOwnProperty results "Search"))
  (swap! app-state assoc :movies-list (into [] (movies-list results))))

(defn update-random-word [word]
  "Updates the random-text value in app-state"
  (swap! app-state assoc :random-word word))

(defn get-search-results-and-update [word]
  "Sends request to search engine"
  (let [ch (get-jsonp (search-url word))]
    (go (update-movie-info (<! ch)))))

(defn get-word-and-update [button-disabled]
  "Toggles the state to disabled, updates the random word, and re-enables the button"
  (let [ch (GET random-word-url)]
    (go (reset! button-disabled true)
        (let [word (<! ch)]
          (update-random-word word)
          (get-search-results-and-update word)
          (<! (timeout 1000))) ;this timeout is just here to play with core.async stuff
        (reset! button-disabled false))))

;; components

(defmulti movie-item (fn [movie] (:type movie)))

(defmethod movie-item :error [movie]
  [:div "Error, movie not found"])

(defmethod movie-item :result [movie]
  (let [item (:item movie)]
    [:li.movie-item
     [:div.image-container
      (let [src (get item "Poster")]
        (if (= src "N/A")
          [:div.no-image "No image"]
          [:img.poster {:src (get item "Poster")}]))]
     [:div.contents
      [:div.title (get item "Title")]
      [:div.year (get item "Year")]]]))

(defn movie-info []
  (let [movies (:movies-list @app-state)]
    [:ul.movies-list (map #(movie-item %) movies)]))

(defn random-word-display []
  [:div.random-word (str "Random word: " (:random-word @app-state))])

(defn get-new-word-button []
  (let [disabled (atom false)]
    (fn []
      [:input.new-random-word {:type "button"
                               :disabled @disabled
                               :on-click #(get-word-and-update disabled)
                               :value "new random word"}])))

(defn main-app []
  [:div
   [:h1 (:text @app-state)]
   [random-word-display]
   [get-new-word-button]
   [movie-info]])

(reagent/render-component [main-app]
                          (. js/document (getElementById "app")))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
