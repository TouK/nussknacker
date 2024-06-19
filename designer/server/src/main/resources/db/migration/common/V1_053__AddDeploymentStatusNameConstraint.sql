ALTER TABLE "deployments" ADD CONSTRAINT "deployments_status_name_check" CHECK (
    "status_name" in ('DURING_DEPLOY', 'RUNNING', 'FINISHED', 'RESTARTING', 'DURING_CANCEL', 'CANCELED', 'PROBLEM' ));
