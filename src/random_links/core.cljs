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
                          :movie-info {}
                          :url-list []}))

(def random-word-url "http://randomword.setgetgo.com/get.php")

(defn search-url [term]
  (str "http://www.omdbapi.com/?s=" term))


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

(defn update-movie-info [movie-info]
  (.log js/console movie-info)
  )

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
          (<! (timeout 1000)))
        (reset! button-disabled false))))

;; components

(defn movie-item [movie]
  [:div (:Title movie)])

(defn movie-info []
  (let [movie (:movie-info app-state)]
    [:div (map movie movie-item)]))

(defn random-word-display []
  [:div (str "Random word: " (:random-word @app-state))])

(defn get-new-word-button []
  (let [disabled (atom false)]
    (fn []
      [:input {:type "button"
               :disabled @disabled
               :on-click #(get-word-and-update disabled)
               :value "new random word"}])))

(defn main-app []
  [:div
   [:h1 (:text @app-state)]
   [random-word-display]
   [get-new-word-button] ])
;   [movie-info]])

(reagent/render-component [main-app]
                          (. js/document (getElementById "app")))


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
