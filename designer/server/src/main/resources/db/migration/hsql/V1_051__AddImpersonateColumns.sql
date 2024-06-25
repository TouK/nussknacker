ALTER TABLE "process_attachments" ADD COLUMN "impersonated_by_identity" VARCHAR2(255);
ALTER TABLE "process_comments" ADD COLUMN "impersonated_by_identity" VARCHAR2(255);
ALTER TABLE "process_actions" ADD COLUMN "impersonated_by_identity" VARCHAR2(255);
ALTER TABLE "processes" ADD COLUMN "impersonated_by_identity" VARCHAR2(255);

ALTER TABLE "process_attachments" ADD COLUMN "impersonated_by_username" VARCHAR2(255);
ALTER TABLE "process_comments" ADD COLUMN "impersonated_by_username" VARCHAR2(255);
ALTER TABLE "process_actions" ADD COLUMN "impersonated_by_username" VARCHAR2(255);
ALTER TABLE "processes" ADD COLUMN "impersonated_by_username" VARCHAR2(255);
