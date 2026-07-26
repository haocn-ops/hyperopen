(ns hyperopen.account.history.position-identity)

(defn position-unique-key [position-data]
  (str (get-in position-data [:position :coin]) "|" (or (:dex position-data) "default")))
