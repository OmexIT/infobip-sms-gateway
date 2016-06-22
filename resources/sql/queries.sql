-- name: get-all-campaigns
-- gets all sms campaigns

SELECT *
FROM `sms-gateway`.sms_campaign
ORDER BY id DESC
LIMIT :offset, :limit;

-- name: fetch-one-campaign
-- gets one sms campaign

SELECT *
FROM `sms-gateway`.sms_campaign
WHERE id = :id;

-- name: fetch-active-campaigns
-- fetches all the active campaigns

SELECT *
FROM `sms-gateway`.sms_campaign
WHERE campaign_status = 0 AND
      CURDATE() BETWEEN start_date AND end_date
ORDER BY date_created ASC;

-- name: fetch-campaign-messages
-- fetches campaign messages

SELECT *
FROM `sms-gateway`.sms_campaign_message
WHERE sms_campaign_id = :campaign_id
ORDER BY id DESC
LIMIT :offset, :limit;

-- name: create-campaign<!
-- creates a campaign
INSERT INTO `sms-gateway`.sms_campaign (campaign_type, start_date, end_date, cron_expression,
                                        created_by, tenant_id, campaign_name, message_template,
                                        schedule_once, campaign_criteria)
VALUES
  (cast(:campaign_type AS UNSIGNED), :start_date, :end_date, :cron_expression,
   :created_by, :tenant_id, :campaign_name, :message_template, :schedule_once, :campaign_criteria);

-- name: create-sms-log<!
-- create a new sms log
INSERT INTO `sms-gateway`.sms_logs (campaign_id, sms_to, sms_from, message) VALUES
  (:campaign_id, :sms_to, :sms_from, :message);

-- name: update-message-after-sending!
-- updates the message after sending
UPDATE `sms-gateway`.sms_logs
SET bulk_id      = :bulk_id, message_id = :message_id, request_status = 1,
  status_message = :status, sent_log = :log
WHERE id = :id;

-- name: get-dirty-messages
-- gets the messages whose status hasn't changes
SELECT DISTINCT (bulk_id) AS bulk_id
FROM `sms-gateway`.sms_logs
WHERE status_changed = 0 AND bulk_id IS NOT NULL
ORDER BY id DESC
LIMIT 20;

-- name: clean-dirty-messages!
-- updates the dirty messages
UPDATE `sms-gateway`.sms_logs
SET status_changed = 1, request_status = 2, status_message = :status, sent_log = :log
WHERE message_id = :message_id;

-- name: delete-campaign!
-- deletes a campaign
DELETE FROM `sms-gateway`.sms_campaign
WHERE id = :id;

-- name: delete-campaign-messages!
-- deletes campaign messages
DELETE FROM `sms-gateway`.sms_campaign_message
WHERE sms_campaign_id = :id;

-- name: create-campaign-message<!
-- creates a campaign message
INSERT INTO `sms-gateway`.sms_campaign_message (sms_campaign_id, destination, message, sender)
VALUES
  (:sms_campaign_id, :stination, :message, :sender);


