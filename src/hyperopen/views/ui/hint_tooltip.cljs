(ns hyperopen.views.ui.hint-tooltip
  "Instant, app-styled hover/focus help tooltip — the styled replacement for the
  native `title` attribute (which is slow to appear and OS-styled). Follows the
  established group-hover pattern (see active_asset/outcome_tooltip and the
  optimizer segmented buttons) but uses a NAMED Tailwind group (`group/hint`) so
  a trigger nested inside another `group` (e.g. a <details> disclosure) never
  fires the tooltip from that ancestor group.

  Usage: wrap the trigger element with `attach`, choosing a `placement` that
  keeps the small tooltip inside the surrounding panel (open downward near the
  top, upward near the bottom; align away from the nearest edge)."
  (:require [clojure.string :as str]))

(def ^:private panel-classes
  ["pointer-events-none" "absolute" "z-50" "w-64" "max-w-[calc(100vw-2rem)]"
   "whitespace-normal" "rounded-lg" "border" "border-base-300" "bg-gray-800"
   "px-2.5" "py-2" "text-left" "text-xs" "font-normal" "normal-case"
   "leading-5" "tracking-normal" "text-gray-100"
   "shadow-[0_12px_32px_rgb(var(--ho-bg-deep)/0.5)]"
   "opacity-0" "transition-opacity" "duration-100"
   ;; Hover-only: a focus-within reveal would fire on the panel's auto-focus of
   ;; the (first) close button on open. Interactive triggers carry aria-labels.
   "group-hover/hint:opacity-100"])

(defn- placement-classes
  [placement]
  (case placement
    :top-start ["bottom-[calc(100%+6px)]" "left-0"]
    :top-end ["bottom-[calc(100%+6px)]" "right-0"]
    :top ["bottom-[calc(100%+6px)]" "left-1/2" "-translate-x-1/2"]
    :bottom-end ["top-[calc(100%+6px)]" "right-0"]
    :bottom ["top-[calc(100%+6px)]" "left-1/2" "-translate-x-1/2"]
    ;; default :bottom-start
    ["top-[calc(100%+6px)]" "left-0"]))

(defn hint-span
  [text placement]
  [:span {:class (into panel-classes (placement-classes placement))
          :role "tooltip"}
   text])

(defn attach
  "Return `element` ([tag attrs & children]) marked as a tooltip trigger with a
  styled `text` tooltip appended. `opts`:
    :placement — :bottom-start (default) | :bottom | :bottom-end | :top-start
                 | :top | :top-end
    :cursor?   — add `cursor-help` (default true; pass false for buttons).
  No-ops (returns the element unchanged) when `text` is blank."
  ([text element] (attach text element {}))
  ([text element {:keys [placement cursor?] :or {cursor? true}}]
   (if (and (string? text) (not (str/blank? text)) (vector? element))
     (let [[tag attrs & children] element
           extra (cond-> ["group/hint" "relative"] cursor? (conj "cursor-help"))
           attrs (update attrs :class #(into extra (or % [])))]
       (into [tag attrs] (concat children [(hint-span text placement)])))
     element)))
