CREATE TABLE "deployments" (
    "id" UUID NOT NULL PRIMARY KEY,
    "scenario_id" INTEGER NOT NULL,
    "created_at" TIMESTAMP NOT NULL,
    "created_by" VARCHAR(255) NOT NULL
);

ALTER TABLE "deployments"
    ADD CONSTRAINT "deployments_scenarios_fk" FOREIGN KEY ("scenario_id") REFERENCES "processes" ("id") ON DELETE CASCADE;
