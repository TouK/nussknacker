UPDATE scenario_activities
SET additional_properties = REPLACE(additional_properties, CHR(10), '\n')
WHERE additional_properties LIKE '%' || CHR(10) || '%';
