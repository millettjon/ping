(ns eway.util.string)

(defn truncate [string max-length]
  "Truncates string to max-length chars."
  (apply str (take max-length string)))
