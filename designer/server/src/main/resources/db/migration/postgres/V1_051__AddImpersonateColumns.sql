ALTER TABLE "process_attachments" ADD COLUMN "impersonated_by_identity" VARCHAR(255);
ALTER TABLE "process_comments" ADD COLUMN "impersonated_by_identity" VARCHAR(255);
ALTER TABLE "process_actions" ADD COLUMN "impersonated_by_identity" VARCHAR(255);
ALTER TABLE "processes" ADD COLUMN "impersonated_by_identity" VARCHAR(255);

ALTER TABLE "process_attachments" ADD COLUMN "impersonated_by_username" VARCHAR(255);
ALTER TABLE "process_comments" ADD COLUMN "impersonated_by_username" VARCHAR(255);
ALTER TABLE "process_actions" ADD COLUMN "impersonated_by_username" VARCHAR(255);
ALTER TABLE "processes" ADD COLUMN "impersonated_by_username" VARCHAR(255);
