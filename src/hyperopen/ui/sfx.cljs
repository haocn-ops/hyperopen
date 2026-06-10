(ns hyperopen.ui.sfx
  "WebAudio sound effects, synthesized in code (no audio assets).

   Implements the Settings → Alerts → \"Sound on fill\" chime: a soft
   two-note ping under the default themes and a cash-register cha-ching
   under the degen voice. Callers gate on the user's setting; this
   namespace only guards against missing AudioContext (node tests,
   ancient browsers) by no-oping."
  )

(defonce ^:private audio-ctx* (atom nil))

(defn- audio-context
  []
  (when (exists? js/window)
    (when-some [ctor (or (.-AudioContext js/window)
                         (.-webkitAudioContext js/window))]
      (let [ctx (or @audio-ctx* (reset! audio-ctx* (new ctor)))]
        (when (= "suspended" (.-state ctx))
          (.resume ctx))
        ctx))))

(defn- envelope!
  [gain t0 peak dur]
  (doto (.-gain gain)
    (.setValueAtTime 0.0001 t0)
    (.exponentialRampToValueAtTime peak (+ t0 0.01))
    (.exponentialRampToValueAtTime 0.0001 (+ t0 dur))))

(defn- tone!
  [ctx {:keys [type freq dur peak delay slide-to]
        :or {type "sine" freq 440 dur 0.2 peak 0.15 delay 0}}]
  (let [t0 (+ (.-currentTime ctx) delay)
        osc (.createOscillator ctx)
        gain (.createGain ctx)]
    (set! (.-type osc) type)
    (.setValueAtTime (.-frequency osc) freq t0)
    (when slide-to
      (.exponentialRampToValueAtTime (.-frequency osc) slide-to (+ t0 dur)))
    (envelope! gain t0 peak dur)
    (.connect osc gain)
    (.connect gain (.-destination ctx))
    (.start osc t0)
    (.stop osc (+ t0 dur 0.05))))

(defn- noise!
  [ctx {:keys [dur peak delay freq]
        :or {dur 0.15 peak 0.1 delay 0 freq 1200}}]
  (let [t0 (+ (.-currentTime ctx) delay)
        len (max 1 (js/Math.floor (* (.-sampleRate ctx) dur)))
        buffer (.createBuffer ctx 1 len (.-sampleRate ctx))
        data (.getChannelData buffer 0)]
    (dotimes [i len]
      (aset data i (dec (* 2 (js/Math.random)))))
    (let [src (.createBufferSource ctx)
          filt (.createBiquadFilter ctx)
          gain (.createGain ctx)]
      (set! (.-buffer src) buffer)
      (set! (.-type filt) "bandpass")
      (set! (.-value (.-frequency filt)) freq)
      (envelope! gain t0 peak dur)
      (.connect src filt)
      (.connect filt gain)
      (.connect gain (.-destination ctx))
      (.start src t0))))

(defn- chime!
  "Default fill sound: a soft two-note ping."
  [ctx]
  (tone! ctx {:type "sine" :freq 880 :dur 0.12 :peak 0.08})
  (tone! ctx {:type "sine" :freq 1320 :dur 0.22 :peak 0.07 :delay 0.1}))

(defn- chaching!
  "Degen fill sound: cash-register cha-ching (from the HyperDegen
   prototype's sound design)."
  [ctx]
  (tone! ctx {:type "square" :freq 1244 :dur 0.07 :peak 0.08})
  (tone! ctx {:type "square" :freq 1661 :dur 0.07 :peak 0.08 :delay 0.08})
  (tone! ctx {:type "triangle" :freq 2489 :dur 0.35 :peak 0.1 :delay 0.16})
  (noise! ctx {:dur 0.08 :peak 0.05 :delay 0.16 :freq 4000}))

(defn fill!
  "Play the order-fill sound; degen? selects the cha-ching variant.
   No-ops (returns nil) when WebAudio is unavailable."
  [degen?]
  (when-some [ctx (audio-context)]
    (if degen?
      (chaching! ctx)
      (chime! ctx))
    nil))

(defn rekt!
  "Liquidation: rumble plus descending saw plus sad trombone (from the
   HyperDegen prototype). No-ops when WebAudio is unavailable."
  []
  (when-some [ctx (audio-context)]
    (noise! ctx {:dur 0.5 :peak 0.1 :freq 300})
    (tone! ctx {:type "sawtooth" :freq 110 :slide-to 40 :dur 0.8 :peak 0.12})
    (doseq [[i f] (map-indexed vector [233 220 208 196])]
      (let [last? (= i 3)]
        (tone! ctx {:type "sawtooth"
                    :freq f
                    :slide-to (* f (if last? 0.84 0.97))
                    :dur (if last? 0.9 0.28)
                    :peak 0.1
                    :delay (* i 0.34)})))
    nil))

(defn- tick!
  "Leverage slider threshold tick; pitch rises with the tier level."
  [level]
  (when-some [ctx (audio-context)]
    (tone! ctx {:type "square"
                :freq (+ 300 (* (or level 0) 60))
                :dur 0.05
                :peak 0.05})
    nil))

(defonce ^:private last-tier-levels* (atom {}))

(defn leverage-tick-on-change!
  "Play a tick when a risk tier crosses a threshold. `slider-key`
   isolates independent sliders (leverage popover vs order size) so
   they don't swallow each other's crossings. Idempotent per tier level
   (the render-side caller may fire repeatedly); never plays on the
   first observation, only on changes, and only when `enabled?`."
  [slider-key level enabled?]
  (let [prev (get @last-tier-levels* slider-key)]
    (swap! last-tier-levels* assoc slider-key level)
    (when (and enabled? (some? prev) (not= prev level))
      (tick! level))
    nil))
