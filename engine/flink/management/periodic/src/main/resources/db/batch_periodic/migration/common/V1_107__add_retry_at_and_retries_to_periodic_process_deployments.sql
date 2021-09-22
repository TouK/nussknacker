ALTER TABLE "periodic_process_deployments" ADD COLUMN "retries_left" INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE "periodic_process_deployments" ADD COLUMN "next_retry_at" TIMESTAMP DEFAULT NULL;
