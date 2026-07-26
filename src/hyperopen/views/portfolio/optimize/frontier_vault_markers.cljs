(ns hyperopen.views.portfolio.optimize.frontier-vault-markers
  (:require ["lucide/dist/esm/icons/layers-2.js" :default lucide-layers-2-node]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.view-model.frontier :as overlay-model]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def accent "#35d7c7")
(def border "rgba(53, 215, 199, 0.72)")
(def text "#8ffcf1")
(def icon-size 22)
(def label-height 22)

(def ^:private gap 4)
(def ^:private leader-length 10)
(def ^:private label-padding-x 9)
(def ^:private label-min-width 48)
(def ^:private non-blank-text coercion/non-blank-text)

(defn- lucide-node->hiccup
  [node]
  (let [tag-name (aget node 0)
        attrs (js->clj (aget node 1) :keywordize-keys true)]
    [(keyword tag-name) attrs]))

(defn layers-icon
  []
  (into [:svg {:x -11
               :y -11
               :width 22
               :height 22
               :viewBox "0 0 24 24"
               :fill "none"
               :stroke "currentColor"
               :stroke-width 1.65
               :stroke-linecap "round"
               :stroke-linejoin "round"
               :aria-hidden true
               :class "portfolio-frontier-vault-glyph"
               :style {:color text
                       :filter (str "drop-shadow(0 0 2px rgba(143, 252, 241, 0.78)) "
                                    "drop-shadow(0 0 8px rgba(53, 215, 199, 0.36))")}}]
        (map lucide-node->hiccup
             (array-seq lucide-layers-2-node))))

(defn- ticker-like-token?
  [token]
  (boolean (re-matches #"[A-Z0-9]{2,6}" token)))

(def ^:private generic-vault-words
  #{"VAULT" "POOL" "FUND" "STRATEGY"})

(def ^:private known-short-codes
  {"hyperliquidity provider" "HLP"
   "hyperliquidity provider (hlp)" "HLP"})

(def ^:private vowels
  #{\A \E \I \O \U})

(defn- short-code-value
  [value]
  (let [text (non-blank-text value)]
    (when (and text
               (re-matches #"[A-Za-z0-9]{2,6}" text))
      (str/upper-case text))))

(defn- first-abbreviation-match
  [pattern text]
  (some->> (re-seq pattern text)
           (map second)
           (filter seq)
           first
           str/upper-case))

(defn- explicit-three-letter-abbreviation
  [value]
  (when-let [text (non-blank-text value)]
    (first-abbreviation-match #"\(([A-Za-z]{3})\)" text)))

(defn- known-short-code
  [value]
  (when-let [text (non-blank-text value)]
    (get known-short-codes (str/lower-case text))))

(defn- padded-code
  [code]
  (let [code* (str/upper-case (or code ""))]
    (subs (str code* "VLT") 0 3)))

(defn- compact-token-code
  [token]
  (let [token* (str/upper-case (or token ""))
        first-char (first token*)
        consonants (->> (rest token*)
                        (remove vowels)
                        (apply str))
        skeleton (str first-char consonants)]
    (padded-code
     (if (>= (count skeleton) 3)
       skeleton
       token*))))

(defn short-code
  [point]
  (or (some->> [(:abbreviation point)
                (:short-name point)
                (:ticker point)
                (:symbol point)]
               (keep short-code-value)
               first)
      (explicit-three-letter-abbreviation (overlay-model/overlay-label point))
      (known-short-code (overlay-model/overlay-label point))
      (let [tokens (->> (or (overlay-model/overlay-label point) "")
                        (re-seq #"[A-Za-z0-9]+")
                        (map str/upper-case)
                        vec)
            tokens* (if (and (> (count tokens) 3)
                             (ticker-like-token? (first tokens)))
                      (subvec tokens 1)
                      tokens)
            code (cond
                   (>= (count tokens*) 3)
                   (->> tokens*
                        (take 3)
                        (map #(subs % 0 1))
                        (apply str))

                   (= 2 (count tokens*))
                   (let [[first-token second-token] tokens*]
                     (if (generic-vault-words second-token)
                       (compact-token-code first-token)
                       (str (subs first-token 0 1)
                            (subs (compact-token-code second-token) 0 2))))

                   (= 1 (count tokens*))
                   (compact-token-code (first tokens*))

                   :else "VAULT")]
        (padded-code code))))

(defn marker-layout
  [point]
  (let [code (short-code point)
        label-width (max label-min-width
                         (+ (* 8 (count code))
                            (* 2 label-padding-x)))
        icon-half (/ icon-size 2)
        label-half (/ label-height 2)
        leader-x1 (+ icon-half gap)
        leader-x2 (+ leader-x1 leader-length)
        label-x (+ leader-x2 gap)]
    {:code code
     :icon-half icon-half
     :label-half label-half
     :leader-x1 leader-x1
     :leader-x2 leader-x2
     :label-x label-x
     :label-width label-width
     :full-width (+ icon-size gap leader-length gap label-width)}))
