UPDATE "scenario_activities"
SET "additional_properties" = REPLACE("additional_properties", CHAR(10), '\n')
WHERE "additional_properties" LIKE '%' || CHAR(10) || '%';
