(ns com.omexit.infobip.phoneNumberUtil
  (:import (com.google.i18n.phonenumbers PhoneNumberUtil PhoneNumberUtil$PhoneNumberFormat)))


(defn internationalize
  [msisdn default-region]
  (let [phoneUtil (PhoneNumberUtil/getInstance)]
    (try
      (let [proto (-> phoneUtil (.parse msisdn default-region))]
        (-> phoneUtil (.format proto (PhoneNumberUtil$PhoneNumberFormat/INTERNATIONAL))))
      (catch Exception e
        (.printStackTrace e)))))

(defn nationalize
  [msisdn default-region]
  (let [phoneUtil (PhoneNumberUtil/getInstance)]
    (try
      (let [proto (-> phoneUtil (.parse msisdn default-region))]
        (-> phoneUtil (.format proto (PhoneNumberUtil$PhoneNumberFormat/NATIONAL))))
      (catch Exception e
        (.printStackTrace e)))))

(defn e164-format
  [msisdn default-region]
  (let [phoneUtil (PhoneNumberUtil/getInstance)]
    (try
      (let [proto (-> phoneUtil (.parse msisdn default-region))]
        (-> phoneUtil (.format proto (PhoneNumberUtil$PhoneNumberFormat/E164))))
      (catch Exception e
        (.printStackTrace e)))))

(defn rfc3966-format
  [msisdn default-region]
  (let [phoneUtil (PhoneNumberUtil/getInstance)]
    (try
      (let [proto (-> phoneUtil (.parse msisdn default-region))]
        (-> phoneUtil (.format proto (PhoneNumberUtil$PhoneNumberFormat/RFC3966))))
      (catch Exception e
        (.printStackTrace e)))))

(defn is-valid?
  [msisdn default-region]
  (let [phoneUtil (PhoneNumberUtil/getInstance)]
    (try
      (let [proto (-> phoneUtil (.parse msisdn default-region))]
        (-> phoneUtil (-> phoneUtil (.isValidNumber proto))))
      (catch Exception e
        (.printStackTrace e)))))

(defn strip+
  [^String msisdn]
  (-> msisdn (.substring 1)))