(ns hyperopen.views.spectate-mode-modal.search
  (:require [clojure.string :as str]))

(def ^:private min-fuzzy-label-query-length
  4)

(defn normalized-query
  [search]
  (let [query (some-> search str str/trim str/lower-case)]
    (when (seq query)
      query)))

(defn- includes-query?
  [query value]
  (boolean
   (when (and (seq query) (some? value))
     (str/includes? (str/lower-case (str value)) query))))

(defn- compact-token
  [value]
  (let [token (some-> value str str/trim str/lower-case
                      (str/replace #"[^a-z0-9]+" ""))]
    (when (seq token)
      token)))

(defn- label-tokens
  [value]
  (let [text (some-> value str str/trim str/lower-case)]
    (->> (concat [(compact-token text)]
                 (when (seq text)
                   (str/split text #"[^a-z0-9]+")))
         (remove str/blank?)
         distinct
         vec)))

(defn- damerau-levenshtein-distance
  [left right]
  (let [left* (or left "")
        right* (or right "")
        left-len (count left*)
        right-len (count right*)]
    (loop [i 0
           prev-prev nil
           prev-row (vec (range (inc right-len)))]
      (if (>= i left-len)
        (nth prev-row right-len)
        (let [row (loop [j 0
                         row* [(inc i)]]
                    (if (>= j right-len)
                      row*
                      (let [cost (if (= (subs left* i (inc i))
                                        (subs right* j (inc j)))
                                   0
                                   1)
                            deletion (inc (nth prev-row (inc j)))
                            insertion (inc (peek row*))
                            substitution (+ (nth prev-row j) cost)
                            transposition (when (and prev-prev
                                                     (pos? i)
                                                     (pos? j)
                                                     (= (subs left* i (inc i))
                                                        (subs right* (dec j) j))
                                                     (= (subs left* (dec i) i)
                                                        (subs right* j (inc j))))
                                            (inc (nth prev-prev (dec j))))
                            next-distance (apply min
                                                 (cond-> [deletion insertion substitution]
                                                   transposition (conj transposition)))]
                        (recur (inc j) (conj row* next-distance)))))]
          (recur (inc i) prev-row row))))))

(defn- fuzzy-label-query?
  [query label]
  (let [query-tokens (label-tokens query)
        label-tokens* (label-tokens label)]
    (boolean
     (some (fn [query-token]
             (and (>= (count query-token) min-fuzzy-label-query-length)
                  (some (fn [label-token]
                          (and (<= (js/Math.abs (- (count query-token)
                                                   (count label-token)))
                                   1)
                               (<= (damerau-levenshtein-distance query-token label-token)
                                   1)))
                        label-tokens*)))
           query-tokens))))

(defn- label-matches-query?
  [query label]
  (or (includes-query? query label)
      (fuzzy-label-query? query label)))

(defn entry-matches-query?
  [query entry]
  (or (label-matches-query? query (:label entry))
      (includes-query? query (:address entry))))
