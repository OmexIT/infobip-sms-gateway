(ns com.omexit.infobip.constants
  (:require [clj-time.format :as f]))

(def default-date-format "dd-MM-yyyy")
(def app-exception :sms_app_exception)
(def http-error :http_error)
(def default-formatter (f/formatter "yyyy-MM-dd"))
(def cheshire-formatter (f/formatter "yyyy-MM-dd'T'HH:mm:ss'Z'"))
(def mifos-formatter (f/formatter "dd MMMM yyyy"))
(def infobip-formatter (f/formatter "yyyy-MM-dd'T'HH:mm:ss.SSSZ"))