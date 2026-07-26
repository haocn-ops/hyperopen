(ns hyperopen.domain.trading.indicators.math.patterns)

(defn zigzag-pivots
  [close-values threshold]
  (let [size (count close-values)]
    (if (zero? size)
      []
      (loop [idx 1
             trend nil
             last-pivot-idx 0
             last-pivot-price (nth close-values 0)
             candidate-idx 0
             candidate-price (nth close-values 0)
             pivots [{:idx 0 :price (nth close-values 0)}]]
        (if (= idx size)
          (let [last-candidate {:idx candidate-idx :price candidate-price}]
            (if (= (:idx (last pivots)) (:idx last-candidate))
              pivots
              (conj pivots last-candidate)))
          (let [price (nth close-values idx)]
            (cond
              (nil? trend)
              (cond
                (>= price (* last-pivot-price (+ 1 threshold)))
                (recur (inc idx) :up last-pivot-idx last-pivot-price idx price pivots)

                (<= price (* last-pivot-price (- 1 threshold)))
                (recur (inc idx) :down last-pivot-idx last-pivot-price idx price pivots)

                (or (> price candidate-price)
                    (< price candidate-price))
                (recur (inc idx)
                       nil
                       last-pivot-idx
                       last-pivot-price
                       idx
                       price
                       pivots)

                :else
                (recur (inc idx) trend last-pivot-idx last-pivot-price candidate-idx candidate-price pivots))

              (= trend :up)
              (cond
                (> price candidate-price)
                (recur (inc idx) :up last-pivot-idx last-pivot-price idx price pivots)

                (<= price (* candidate-price (- 1 threshold)))
                (let [pivot {:idx candidate-idx :price candidate-price}]
                  (recur (inc idx)
                         :down
                         (:idx pivot)
                         (:price pivot)
                         idx
                         price
                         (if (= (:idx (last pivots)) (:idx pivot))
                           pivots
                           (conj pivots pivot))))

                :else
                (recur (inc idx) :up last-pivot-idx last-pivot-price candidate-idx candidate-price pivots))

              :else
              (cond
                (< price candidate-price)
                (recur (inc idx) :down last-pivot-idx last-pivot-price idx price pivots)

                (>= price (* candidate-price (+ 1 threshold)))
                (let [pivot {:idx candidate-idx :price candidate-price}]
                  (recur (inc idx)
                         :up
                         (:idx pivot)
                         (:price pivot)
                         idx
                         price
                         (if (= (:idx (last pivots)) (:idx pivot))
                           pivots
                           (conj pivots pivot))))

                :else
                (recur (inc idx) :down last-pivot-idx last-pivot-price candidate-idx candidate-price pivots)))))))))

(defn interpolate-zigzag
  [size pivots]
  (let [initial (vec (repeat size nil))
        with-segments (reduce (fn [acc [a b]]
                                (let [i1 (:idx a)
                                      p1 (:price a)
                                      i2 (:idx b)
                                      p2 (:price b)
                                      length (max 1 (- i2 i1))]
                                  (reduce (fn [inner idx]
                                            (let [ratio (/ (- idx i1) length)
                                                  value (+ p1 (* ratio (- p2 p1)))]
                                              (assoc inner idx value)))
                                          acc
                                          (range i1 (inc i2)))))
                              initial
                              (partition 2 1 pivots))]
    with-segments))
