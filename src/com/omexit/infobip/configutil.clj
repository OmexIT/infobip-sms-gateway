(ns com.omexit.infobip.configutil
  (:import (java.io FileInputStream)
           (java.util Properties))
  (:require [environ.core :refer [env]]
            [clojure.tools.logging :as log])
  (:use [clojure.walk]))

(defn- read-properties [key]
  (let [file-input-stream (new FileInputStream (env key))
        properties (Properties.)]
    (-> properties (.load file-input-stream))
    (into {} (map (fn [[k v]] {(keyword (clojure.string/replace k "." "-")) v}) properties))))

(def ^:dynamic *props-map* (atom (read-properties :infobip-service-config)))



