(ns com.omexit.infobip.data
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:require [clj-time.jdbc]
            [com.omexit.infobip.configutil :refer [*props-map*]]
            [clojure.java.jdbc :as jdbc])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

(defn pooled-spec
  "return pooled conn spec.
   Usage:
     (def pooled-db (pooled-spec db-spec))
     (with-connection pooled-db ...)"
  []
  (let [cpds (doto
               (ComboPooledDataSource.)
               (.setDriverClass (:jdbc-driver @*props-map*))
               (.setJdbcUrl (:jdbc-url @*props-map*))
               (.setUser (:jdbc-user @*props-map*))
               (.setPassword (:jdbc-password @*props-map*)))]
    {:datasource cpds}))

(def pooled-db (delay (pooled-spec)))

(defn db-connection [] @pooled-db)

(defn create-campaign
  [params-vec]
  (jdbc/with-db-transaction
    [conn (db-connection)]
    (apply clojure.java.jdbc/insert!
           (apply conj [conn :sms_campaign] params-vec))))

(defn create-campaign-message-log
  [params-vec]
  (jdbc/with-db-transaction
    [conn (db-connection)]
    (apply clojure.java.jdbc/insert!
           (apply conj [conn :sms_logs] params-vec))))

(defn get-all-campaigns
  [offset limit]
  (jdbc/with-db-connection
    [conn (db-connection)]
    (jdbc/query (db-connection) ["SELECT * FROM `sms-gateway`.sms_campaign ORDER BY id DESC LIMIT ?, ?" offset limit])))

(defn fetch-one-campaign
  [campaign-id]
  (jdbc/with-db-connection
    [conn (db-connection)]
    (jdbc/query (db-connection) ["SELECT * FROM `sms-gateway`.sms_campaign WHERE id = ?" campaign-id])))

(defn fetch-campaign-messages
  [campaign-id offset limit]
  (jdbc/with-db-connection
    [conn (db-connection)]
    (jdbc/query (db-connection) ["SELECT l.*, c.campaign_type, c.campaign_name FROM `sms-gateway`.sms_logs l INNER JOIN `sms-gateway`.sms_campaign c ON l.campaign_id = c.id WHERE l.sms_campaign_id = ? ORDER BY l.id DESC LIMIT ?, ?" campaign-id offset limit])))


(defn fetch-sent-messages
  [smsStatus startDate endDate]
  (let [query (condp = smsStatus
                "DELIVERED"
                (str "SELECT l.*, c.campaign_type, c.campaign_name FROM `sms-gateway`.sms_logs l "
                     "INNER JOIN `sms-gateway`.sms_campaign c ON l.campaign_id = c.id WHERE request_status = 'DELIVERED'"
                     "AND date_added BETWEEN ? AND DATE_ADD(?,INTERVAL 1 DAY) ORDER BY l.id DESC")
                "FAILED"
                (str "SELECT l.*, c.campaign_type, c.campaign_name FROM `sms-gateway`.sms_logs l "
                     "INNER JOIN `sms-gateway`.sms_campaign c ON l.campaign_id = c.id WHERE "
                     "request_status NOT IN ('DELIVERED', 'ACCEPTED') "
                     "AND date_added BETWEEN ? AND DATE_ADD(?,INTERVAL 1 DAY) ORDER BY l.id DESC"))]
    (jdbc/with-db-connection
      [conn (db-connection)]
      (jdbc/query (db-connection) [query startDate endDate]))))


(defn fetch-active-scheduled-campaigns
  []
  (jdbc/with-db-connection
    [conn (db-connection)]
    (jdbc/query (db-connection) ["SELECT * FROM active_sms_campaign where campaign_type =2 ORDER BY date_created ASC"])))

(defn fetch-pending-direct-campaigns
  []
  (jdbc/with-db-connection
    [conn (db-connection)]
    (jdbc/query (db-connection) ["SELECT DISTINCT bulk_id FROM sms_logs where request_status ='MESSAGE_ACCEPTED' ORDER BY date_added ASC"])))

(defn update-message-status
  [params]
  (let [{:keys [bulk_id message_id]} params
        update-data (dissoc params :bulk_id :message_id)]
    (jdbc/with-db-connection
      [conn (db-connection)]
      (jdbc/update! conn :sms_logs update-data ["bulk_id = ? and message_id = ?" bulk_id message_id]))))