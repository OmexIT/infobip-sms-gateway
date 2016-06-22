(ns com.omexit.infobip.service
  (:use [slingshot.slingshot :only [throw+ try+]]
        [com.omexit.infobip.constants]
        [com.omexit.infobip.configutil]
        [clostache.parser])
  (:require [com.omexit.infobip.data :as data]
            [clojure.tools.logging :as log]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [cheshire.core :as json]
            [com.omexit.infobip.mifos :as mifos]
            [com.omexit.infobip.sms :as sms]
            [jobesque.core :as jobs]
            [com.omexit.infobip.phoneNumberUtil :as phone])
  (:import (java.util Calendar)))

(def date-formatter (f/formatter default-date-format))

(defn numeric? [s]
  (if-let [s (seq s)]
    (let [s (if (= (first s) \-) (next s) s)
          s (drop-while #(Character/isDigit %) s)
          s (if (= (first s) \.) (next s) s)
          s (drop-while #(Character/isDigit %) s)]
      (empty? s))))

;; we start by initializing jobs and starting them
(defn init
  "this initializes the scheduler"
  ^{:author "Jijo Lemaiyan"
    :added  "1.0"
    :date   "Sat, May 21 2016"}
  []
  (try
    (jobs/initialize)
    (jobs/start)
    (catch Exception e)))


;;Store active jobs IDs
(def active-crons (atom (vector)))

(defn is-campaign-scheduled?
  [campaign-id]
  (some #(= campaign-id (:id %)) @active-crons))

(defn handle-campaign
  "Creates campaign messages and sends them to SMS queue"
  [params]
  (let [{:keys [id campaign_type cron_expression tenant_id message_template campaign_criteria]} params
        _ (log/infof "handleCampaign(%s)" params)
        payload-data (into []
                           (condp = (-> :id campaign_criteria str)
                             "1"
                             (let [criteria-clients (mifos/fetch-mifos-active-clients tenant_id)]
                               (doall
                                 (map (fn [c]
                                        (let [{:keys [mobileNo lastname displayName middlename firstname externalId active]} c
                                              msg-params {:mobile_number mobileNo
                                                          :last_name     lastname
                                                          :full_name     displayName
                                                          :middle_name   middlename
                                                          :first_name    firstname
                                                          :external_id   externalId}]
                                          (if active
                                            (let [msg (render message_template msg-params)
                                                  time (t/now)
                                                  msg-obj {:campaign_id id,
                                                           :sms_to      (:mobileNo c),
                                                           :sms_from    (:infobip-message-sender @*props-map*),
                                                           :message     msg}
                                                  ;msg-data {:sms_campaign_id id,
                                                  ;          :date_created    time
                                                  ;          :last_modified   time
                                                  ;          :destination     (:mobileNo c),
                                                  ;          :sender          (:infobip-message-sender @*props-map*),
                                                  ;          :message         msg}
                                                  ]
                                              ;(log/infof "created sms payload: %s" msg)
                                              msg-obj)))) (:pageItems criteria-clients))))))]
    (log/infof "sendSMSPayload -> %s" payload-data)
    (sms/send-sms payload-data)))

(defn load-active-campaigns
  "Loads active campaigns that are in the database to cron"
  ^{:author "Jijo Lemaiyan"
    :added  "1.0"
    :date   "Sun, May 22 2016"}
  []
  (if-not (jobs/initialized?)
    (init))
  (let [campaigns (data/fetch-active-scheduled-campaigns)]
    (doseq [campaign campaigns]
      (try
        (if-not (is-campaign-scheduled? (:id campaign))
          (do
            (log/infof "doSchedule(%s)" campaign)
            (let [cron_id (jobs/schedule (:cron_expression campaign) (fn [] (handle-campaign campaign)))]
              (log/infof "exp %s" cron_id)
              (swap! active-crons conj (assoc campaign :cron_id cron_id)))))
        (catch Exception e
          (log/errorf e "!doSchedule(%s)" campaign))))))

(defn add-cron-job
  [campaign]
  (try
    (if-not (jobs/initialized?)
      (init))
    (if-not (is-campaign-scheduled? (:id campaign))
      (log/infof "doSchedule(%s)" campaign)
      (let [cron_id (jobs/schedule (:cron_expression campaign) (fn [] (handle-campaign campaign)))]
        (swap! active-crons conj (assoc campaign :cron_id cron_id))))
    (catch Exception e
      (log/errorf e "!doSchedule(%s)" campaign))))

(defn create-campaign
  "Create SMS campaign in the DB. Recieves map parameter. Keys include:
      :campaign_name - name of the campaign
      :campaign_type - either [1 - direct] or [2 - scheduled]
      :start_date - end  date of this campaign if it is schedules
      :end_date - start date of this campaign if it is schedules
      :cron_expression - cron expression for scheduled messages
      :created_by - MIFOS user id of the person who created the campaign
      :tenant_id - mifos tenant
      :message_template - Template of the sms campaign
     "
  [req & [status]]
  (log/infof "%s" req)
  (let [campaign (atom {})
        request (json/parse-string req true)
        {:keys [campaign_name campaign_type start_date end_date cron_expression created_by tenant_id message_template
                schedule_once campaign_criteria]} request]
    (log/infof "doCreateCampaign(%s)" request)
    (log/infof "campaign_criteria(%s)" campaign_criteria)
    (if (nil? campaign_name)
      (throw+ {:type              :validation-exception
               :message           "Campaign name is required"
               :developer-message "Expects campaign_name parameter"})
      (swap! campaign assoc :campaign_name campaign_name))
    (if (nil? tenant_id)
      (throw+ {:type              :validation-exception
               :message           "Tenant identifier is required"
               :developer-message "Expects tenant_id parameter"})
      (swap! campaign assoc :tenant_id tenant_id))
    (if (nil? created_by)
      (throw+ {:type              :validation-exception
               :message           "Created by is required"
               :developer-message "Expects created_by parameter"})
      (swap! campaign assoc :created_by created_by))
    (if (nil? campaign_criteria)
      (throw+ {:type              :validation-exception
               :message           "Campaign criteria is required"
               :developer-message "Expects campaign_criteria parameter"})
      (swap! campaign assoc :campaign_criteria (json/generate-string campaign_criteria)))
    (if (nil? message_template)
      (throw+ {:type              :validation-exception
               :message           "Message template is required"
               :developer-message "Expects message_template parameter"})
      (swap! campaign assoc :message_template message_template))
    (log/infof "campaign_type(%s)" campaign_type)
    (cond
      (= "1" (str campaign_type))
      (swap! campaign assoc :campaign_type campaign_type)
      (= "2" (str campaign_type))
      (do
        (swap! campaign assoc :campaign_type campaign_type)
        (if (nil? cron_expression)
          (throw+ {:type              :validation-exception
                   :message           "Crontab expression is required"
                   :developer-message "Expects cron_expression parameter"})
          (swap! campaign assoc :cron_expression cron_expression))
        (if (nil? schedule_once)
          (throw+ {:type              :validation-exception
                   :message           "Schedule type is required"
                   :developer-message "Expects schedule_once parameter"})
          (if schedule_once
            (do
              (swap! campaign assoc :schedule_once schedule_once)
              (if (nil? cron_expression)
                (throw+ {:type              :validation-exception
                         :message           "Cron start date is required"
                         :developer-message "Expects start_date parameter"})
                (swap! campaign assoc :start_date start_date))
              (if (nil? cron_expression)
                (throw+ {:type              :validation-exception
                         :message           "Cron end date is required"
                         :developer-message "Expects end_date parameter"})
                (swap! campaign assoc :end_date end_date)))))))
    (let [rs (first (data/create-campaign (vector @campaign)))]
      (cond
        (= "1" (str campaign_type))
        (let [campaign-params (assoc @campaign :id (:generated_key rs) :campaign_criteria campaign_criteria)]
          (log/infof "campaign-params: %s" campaign-params)
          (log/infof "Created direct campaign with id: %s" (:generated_key rs))
          (future (handle-campaign campaign-params)))
        (= "2" (str campaign_type))
        (do
          (log/infof "Created scheduled campaign with id: %s" (:generated_key rs))
          (add-cron-job (assoc @campaign :id (:generated_key rs)))))
      {:status  (or status 201)
       :headers {"Content-Type" "application/json"}
       :body    (json/generate-string {:status 1 :message "Campaign created!"})})))

;(create-campaign
;  {:campaign_type    1,
;   :start_date       "04-06-2016",
;   :end_date         "04-06-2016",
;   :cron_expression  "0 0 1 * *",
;   :created_by       1,
;   :tenant_id        "default",
;   :campaign_name    "Test Campaign",
;   :message_template "Dear {{first_name}}"})

(defn find-all-campaign
  [req & [status]]
  (let [offset (:offset req)
        limit (:limit req)
        offset (cond
                 (nil? offset) 0
                 (numeric? (str offset)) (Integer/parseInt offset)
                 :else
                 (throw+ {:type              :validation-exception
                          :message           "offset must be numeric"
                          :developer-message "Expects numeric value for URL parameter offset"}))
        limit (cond
                (nil? limit) 50
                (numeric? (str limit)) (Integer/parseInt limit)
                :else
                (throw+ {:type              :validation-exception
                         :message           "limit must be numeric"
                         :developer-message "Expects numeric value for URL parameter limit"}))
        rs (data/get-all-campaigns offset limit)]
    {:status  (or status 200)
     :headers {"Content-Type" "application/json"}
     :body    (json/generate-string {:status         1
                                     :offset         offset
                                     :limit          limit
                                     :pageItemsCount (count rs)
                                     :totalCount     (count rs)
                                     :data           rs})}))

(defn find-one-campaign
  [req & [status]]
  (let [{:keys [id]} req
        rs (first (data/fetch-one-campaign id))]
    {:status  (or status 200)
     :headers {"Content-Type" "application/json"}
     :body    (json/generate-string rs)}))

(defn fetch-campaign-messages
  [req & [status]]
  (let [{:keys [campaign_id offset limit]} req

        rs (data/fetch-campaign-messages (if-not (nil? campaign_id)
                                           campaign_id
                                           (throw+ {:type              :validation-exception
                                                    :message           "Campaign ID is required"
                                                    :developer-message "Provide a value for campaign id"}))

                                         (cond
                                           (nil? offset) 0
                                           (numeric? (str offset)) (Integer/parseInt offset)
                                           :else
                                           (throw+ {:type              :validation-exception
                                                    :message           "offset must be numeric"
                                                    :developer-message "Expects numeric value for URL parameter offset"}))

                                         (cond
                                           (nil? limit) 50
                                           (numeric? (str limit)) (Integer/parseInt limit)
                                           :else
                                           (throw+ {:type              :validation-exception
                                                    :message           "limit must be numeric"
                                                    :developer-message "Expects numeric value for URL parameter limit"})))]
    {:status  (or status 200)
     :headers {"Content-Type" "application/json"}
     :body    (json/generate-string {:status         1
                                     :offset         offset
                                     :limit          limit
                                     :pageItemsCount (count rs)
                                     :data           rs})}))


(defn fetch-sent-messages
  [req & [status]]
  (log/infof "%s" req)
  (let [{:keys [smsStatus fromDate toDate]} req
        smsStatus (if (nil? smsStatus)
                    "DELIVERED"
                    smsStatus)
        fromDate (if (nil? fromDate)
                    (f/unparse default-formatter (t/now))
                    fromDate)
        toDate (if (nil? toDate)
                  (f/unparse default-formatter (t/now))
                  toDate)

        rs (data/fetch-sent-messages smsStatus fromDate toDate)]
    {:status  (or status 200)
     :headers {"Content-Type" "application/json"}
     :body    (json/generate-string {:status         1
                                     :pageItemsCount (count rs)
                                     :data           rs})}))

(defn fetch-active-campaign
  [req & [status]]
  (let [rs (data/fetch-active-scheduled-campaigns)]
    {:status  (or status 200)
     :headers {"Content-Type" "application/json"}
     :body    (json/generate-string {:status         1
                                     :pageItemsCount (count rs)
                                     :totalCount     (count rs)
                                     :data           rs})}))

(defn fetch-scheduled-campaign
  [req & [status]]
  {:status  (or status 200)
   :headers {"Content-Type" "application/json"}
   :body    (json/generate-string {:status     1
                                   :totalCount (count @active-crons)
                                   :data       @active-crons})})



(defn handle-cron-commands
  "load"
  [req & [status]]
  (let [{:keys [command]} req]
    (condp = command
      "load"
      (do
        (future (load-active-campaigns))
        {:status  (or status 200)
         :headers {"Content-Type" "application/json"}
         :body    (json/generate-string {:status 1 :message "Loaded active campaigns"})})
      "stop"
      (do
        (if (jobs/initialized?) (jobs/stop))
        {:status  (or status 200)
         :headers {"Content-Type" "application/json"}
         :body    (json/generate-string {:status 1 :message "Stopped scheduler"})})
      "clear"
      (do
        (if (jobs/initialized?)
          (do
            (jobs/clear-jobs)
            (reset! active-crons (vector))))
        {:status  (or status 200)
         :headers {"Content-Type" "application/json"}
         :body    (json/generate-string {:status 1 :message "Cleared scheduler jobs"})})
      "start"
      (do
        (if-not (jobs/initialized?) (jobs/start))
        {:status  (or status 200)
         :headers {"Content-Type" "application/json"}
         :body    (json/generate-string {:status 1 :message "Start scheduler"})})
      {:status  (or status 404)
       :headers {"Content-Type" "application/json"}
       :body    (json/generate-string {:status -1 :message "Unknown command"})})))





