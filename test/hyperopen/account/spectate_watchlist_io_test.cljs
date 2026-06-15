(ns hyperopen.account.spectate-watchlist-io-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.account.spectate-watchlist-io :as io]))

(def ^:private addr-a
  "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

(def ^:private addr-b
  "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")

(def ^:private addr-c
  "0xcccccccccccccccccccccccccccccccccccccccc")

(deftest export-payload-builds-versioned-document-test
  (is (= {:filename "spectate-watchlist-1718452800000.json"
          :count 2
          :document {:type "hyperopen-spectate-watchlist"
                     :version 1
                     :exported-at 1718452800000
                     :entries [{:address addr-a :label "alpha"}
                               {:address addr-b :label ""}]}}
         (io/export-payload [{:address addr-a :label "alpha"}
                             {:address addr-b}]
                            1718452800000))))

(deftest extract-entries-accepts-envelope-array-and-strings-test
  (testing "full envelope with string keys"
    (is (= [{"address" addr-a}]
           (io/extract-entries {"type" "hyperopen-spectate-watchlist"
                                "entries" [{"address" addr-a}]}))))
  (testing "bare array of entries"
    (is (= [{"address" addr-a}]
           (io/extract-entries [{"address" addr-a}]))))
  (testing "unusable structures return nil"
    (is (nil? (io/extract-entries "garbage")))
    (is (nil? (io/extract-entries 42)))
    (is (nil? (io/extract-entries {"foo" "bar"})))))

(deftest merge-imported-adds-new-addresses-test
  (let [result (io/merge-imported
                [{:address addr-a :label "alpha"}]
                {"type" "hyperopen-spectate-watchlist"
                 "version" 1
                 "entries" [{"address" addr-b "label" "beta"}
                            {"address" addr-c "label" "gamma"}]})]
    (is (= :ok (:status result)))
    (is (= 2 (:new-count result)))
    (is (= 3 (:total result)))
    (is (= [addr-a addr-b addr-c]
           (mapv :address (:watchlist result))))))

(deftest merge-imported-non-blank-label-wins-and-counts-zero-new-test
  (let [result (io/merge-imported
                [{:address addr-a :label "alpha"}]
                [{"address" addr-a "label" "alpha-renamed"}])]
    (is (= :ok (:status result)))
    (is (= 0 (:new-count result)))
    (is (= 1 (:total result)))
    (is (= "alpha-renamed" (:label (first (:watchlist result)))))))

(deftest merge-imported-blank-label-preserves-existing-test
  (let [result (io/merge-imported
                [{:address addr-a :label "alpha"}]
                [{"address" addr-a "label" ""}])]
    (is (= "alpha" (:label (first (:watchlist result)))))))

(deftest merge-imported-accepts-bare-address-strings-test
  (let [result (io/merge-imported [] [addr-a addr-b])]
    (is (= :ok (:status result)))
    (is (= 2 (:new-count result)))
    (is (= #{addr-a addr-b} (set (map :address (:watchlist result)))))))

(deftest merge-imported-rejects-invalid-and-empty-test
  (is (= {:status :error :reason :invalid}
         (io/merge-imported [] "garbage")))
  (is (= {:status :error :reason :invalid}
         (io/merge-imported [] {"foo" "bar"})))
  (is (= {:status :error :reason :empty}
         (io/merge-imported [] {"entries" []})))
  (is (= {:status :error :reason :empty}
         (io/merge-imported [] ["not-an-address" "also bad"]))))

(deftest message-builders-test
  (is (= "Exported 1 address." (io/export-success-message 1)))
  (is (= "Exported 3 addresses." (io/export-success-message 3)))
  (is (= "Imported 2 addresses. 5 saved." (io/import-success-message 2 5)))
  (is (= "Imported 1 address. 5 saved." (io/import-success-message 1 5)))
  (is (= "Watchlist already up to date. 5 saved." (io/import-success-message 0 5)))
  (is (= "Import failed: no valid wallet addresses found in file."
         (io/import-error-message :empty)))
  (is (= "Import failed: file is not valid JSON."
         (io/import-error-message :invalid))))
