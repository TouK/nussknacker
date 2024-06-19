ALTER TABLE "deployments" ADD COLUMN "status_name" VARCHAR(255) NOT NULL;
ALTER TABLE "deployments" ADD COLUMN "status_problem_description" VARCHAR(1022);
ALTER TABLE "deployments" ADD COLUMN "status_modified_at" TIMESTAMP NOT NULL;
