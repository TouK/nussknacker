CREATE TABLE "scenario_labels" (
  "label"       VARCHAR2(254) NOT NULL,
  "scenario_id" INTEGER NOT NULL
);

ALTER TABLE "scenario_labels" ADD CONSTRAINT "pk_scenario_label" PRIMARY KEY ("label", "scenario_id");
ALTER TABLE "scenario_labels" ADD CONSTRAINT "label_scenario_fk" FOREIGN KEY ("scenario_id") REFERENCES "processes" ("id");
