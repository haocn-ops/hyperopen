(ns hyperopen.asset-selector.outcome-fixtures)

(def live-outcome-meta
  {:outcomes [{:outcome 100
               :name "Fallback"
               :description ""
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 101
               :name "Below 4.3%"
               :description "This outcome resolves to Yes if May CPI is below 4.3%."
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 102
               :name "Exactly 4.3%"
               :description "This outcome resolves to Yes if May CPI is exactly 4.3%."
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 103
               :name "Above 4.3%"
               :description "This outcome resolves to Yes if May CPI is above 4.3%."
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 104
               :name "June Fed rate change"
               :description "The market resolves to Change if the FOMC target range changes."
               :sideSpecs [{:name "Change"} {:name "No Change"}]
               :quoteToken "USDC"}
              {:outcome 141
               :name "NBA Finals Game 2"
               :description "The market resolves to the winner of Game 2. metadata=category:sports|subCategory:basketball"
               :sideSpecs [{:name "San Antonio"} {:name "New York"}]
               :quoteToken "USDC"}
              {:outcome 142
               :name "2026 NBA Finals champion"
               :description "The market resolves to the NBA Finals champion. metadata=category:sports|subCategory:basketball"
               :sideSpecs [{:name "San Antonio"} {:name "New York"}]
               :quoteToken "USDC"}
              {:outcome 171
               :name "Fallback"
               :description ""
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 172
               :name "Algeria"
               :description "This outcome resolves to Yes if Algeria is officially declared the 2026 FIFA World Cup champion."
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 173
               :name "Argentina"
               :description "This outcome resolves to Yes if Argentina is officially declared the 2026 FIFA World Cup champion."
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 178
               :name "Brazil"
               :description "This outcome resolves to Yes if Brazil is officially declared the 2026 FIFA World Cup champion."
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 189
               :name "France"
               :description "This outcome resolves to Yes if France is officially declared the 2026 FIFA World Cup champion."
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 212
               :name "Spain"
               :description "This outcome resolves to Yes if Spain is officially declared the 2026 FIFA World Cup champion."
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 159
               :name "Recurring"
               :description "class:priceBinary|underlying:BTC|expiry:20260606-0600|targetPrice:62290|period:1d"
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 160
               :name "Recurring Fallback"
               :description "other"
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 161
               :name "Recurring Named Outcome"
               :description "index:0"
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 162
               :name "Recurring Named Outcome"
               :description "index:1"
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}
              {:outcome 163
               :name "Recurring Named Outcome"
               :description "index:2"
               :sideSpecs [{:name "Yes"} {:name "No"}]
               :quoteToken "USDC"}]
   :questions [{:question 19
                :name "May CPI year-over-year"
                :description "The question resolves by assigning Yes to exactly one CPI bucket."
                :fallbackOutcome 100
                :namedOutcomes [101 102 103]
                :settledNamedOutcomes []}
               {:question 30
               :name "Recurring"
               :description "class:priceBucket|underlying:BTC|expiry:20260606-0600|priceThresholds:61044,63535|period:1d"
               :fallbackOutcome 160
               :namedOutcomes [161 162 163]
                :settledNamedOutcomes []}
               {:question 32
                :name "2026 World Cup Champion"
                :description "Each associated outcome corresponds to a team confirmed to be participating in the 2026 FIFA World Cup. metadata=category:sports|subCategory:football"
                :fallbackOutcome 171
                :namedOutcomes [172 173 178 189 212]
                :settledNamedOutcomes []}]})

(def live-outcome-ctxs
  [{:coin "#1590" :markPx "0.081775" :prevDayPx "0.48068" :dayNtlVlm "633002.41697" :circulatingSupply "251625.0"}
   {:coin "#1591" :markPx "0.918225" :prevDayPx "0.51932" :dayNtlVlm "1287228.58303" :circulatingSupply "251625.0"}
   {:coin "#1010" :markPx "0.60796" :prevDayPx "0.5853" :dayNtlVlm "248.78635" :circulatingSupply "17998.0"}
   {:coin "#1011" :markPx "0.39204" :prevDayPx "0.4147" :dayNtlVlm "177.21365" :circulatingSupply "10917.0"}
   {:coin "#1020" :markPx "0.37254" :prevDayPx "0.36035" :dayNtlVlm "603.43905" :circulatingSupply "16293.0"}
   {:coin "#1021" :markPx "0.62746" :prevDayPx "0.63965" :dayNtlVlm "1074.56095" :circulatingSupply "9212.0"}
   {:coin "#1030" :markPx "0.05905" :prevDayPx "0.01747" :dayNtlVlm "3494.89221" :circulatingSupply "10984.0"}
   {:coin "#1031" :markPx "0.94095" :prevDayPx "0.98253" :dayNtlVlm "17655.10779" :circulatingSupply "3903.0"}
   {:coin "#1040" :markPx "0.015585" :prevDayPx "0.01767" :dayNtlVlm "1026.36211" :circulatingSupply "227942.0"}
   {:coin "#1041" :markPx "0.984415" :prevDayPx "0.98233" :dayNtlVlm "60294.63789" :circulatingSupply "227942.0"}
   {:coin "#1410" :markPx "0.66984" :prevDayPx "0.665" :dayNtlVlm "35010.86851" :circulatingSupply "58514.0"}
   {:coin "#1411" :markPx "0.33016" :prevDayPx "0.335" :dayNtlVlm "17505.13149" :circulatingSupply "58514.0"}
   {:coin "#1420" :markPx "0.46908" :prevDayPx "0.46936" :dayNtlVlm "97226.7167" :circulatingSupply "247203.0"}
   {:coin "#1421" :markPx "0.53092" :prevDayPx "0.53064" :dayNtlVlm "109575.2833" :circulatingSupply "247203.0"}
   {:coin "#1720" :markPx "0.015" :prevDayPx "0.005" :dayNtlVlm "10.0" :circulatingSupply "8000.0"}
   {:coin "#1721" :markPx "0.985" :prevDayPx "0.995" :dayNtlVlm "900.0" :circulatingSupply "200.0"}
   {:coin "#1730" :markPx "0.14" :prevDayPx "0.10" :dayNtlVlm "1995.0" :circulatingSupply "33616.0"}
   {:coin "#1731" :markPx "0.86" :prevDayPx "0.90" :dayNtlVlm "18080.0" :circulatingSupply "25579.0"}
   {:coin "#1780" :markPx "0.12" :prevDayPx "0.13" :dayNtlVlm "1500.0" :circulatingSupply "25000.0"}
   {:coin "#1781" :markPx "0.88" :prevDayPx "0.87" :dayNtlVlm "17000.0" :circulatingSupply "25000.0"}
   {:coin "#1890" :markPx "0.17" :prevDayPx "0.16" :dayNtlVlm "3000.0" :circulatingSupply "33000.0"}
   {:coin "#1891" :markPx "0.83" :prevDayPx "0.84" :dayNtlVlm "16000.0" :circulatingSupply "24000.0"}
   {:coin "#2120" :markPx "0.17" :prevDayPx "0.15" :dayNtlVlm "2500.0" :circulatingSupply "32000.0"}
   {:coin "#2121" :markPx "0.83" :prevDayPx "0.85" :dayNtlVlm "16500.0" :circulatingSupply "24500.0"}
   {:coin "#1610" :markPx "0.612275" :prevDayPx "0.0515" :dayNtlVlm "14347.8397" :circulatingSupply "6267.0"}
   {:coin "#1611" :markPx "0.387725" :prevDayPx "0.9485" :dayNtlVlm "10898.1603" :circulatingSupply "2427.0"}
   {:coin "#1620" :markPx "0.3003" :prevDayPx "0.91748" :dayNtlVlm "15402.54552" :circulatingSupply "30433.0"}
   {:coin "#1621" :markPx "0.6997" :prevDayPx "0.08252" :dayNtlVlm "22046.45448" :circulatingSupply "26593.0"}
   {:coin "#1630" :markPx "0.01181" :prevDayPx "0.051" :dayNtlVlm "5388.85032" :circulatingSupply "22214.0"}
   {:coin "#1631" :markPx "0.98819" :prevDayPx "0.949" :dayNtlVlm "42430.14968" :circulatingSupply "18374.0"}])
