(ns hyperopen.views.ui.toggle)

(defn props?
  [props]
  (and (map? props)
       (boolean? (:on? props))
       (string? (:aria-label props))
       (some? (:on-change props))
       (or (nil? (:aria-describedby props))
           (string? (:aria-describedby props)))
       (or (nil? (:data-role props))
           (string? (:data-role props)))
       (or (nil? (:disabled? props))
           (boolean? (:disabled? props)))))

(defn toggle
  [{:keys [aria-describedby aria-label data-role disabled? on-change on?]}]
  [:button {:type "button"
            :class ["hx-toggle" (when on? "on")]
            :role "switch"
            :aria-checked (if on? "true" "false")
            :aria-label aria-label
            :aria-describedby aria-describedby
            :data-role data-role
            :disabled disabled?
            :on {:click on-change}}
   [:span {:class ["hx-toggle-knob"]}]])
