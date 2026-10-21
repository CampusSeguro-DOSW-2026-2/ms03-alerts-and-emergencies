ALTER TABLE alerts DROP CONSTRAINT IF EXISTS ck_alerts_type;
ALTER TABLE alerts
    ADD CONSTRAINT ck_alerts_type
    CHECK (type IN ('EARTHQUAKE', 'SIMULATION', 'FIRE', 'OTHER'));

ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS ck_audit_action;
ALTER TABLE audit_events
    ADD CONSTRAINT ck_audit_action
    CHECK (action IN ('CREATE', 'UPDATE', 'SCHEDULE', 'ACTIVATE', 'END',
                      'ARCHIVE', 'CONFIRM', 'NOTIFICATION_SENT',
                      'NOTIFICATION_FAILED', 'RECIPIENT_RESOLUTION_FAILED'));
