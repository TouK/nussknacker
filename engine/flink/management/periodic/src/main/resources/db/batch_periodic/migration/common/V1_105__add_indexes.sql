CREATE INDEX "periodic_processes_process_name_active_idx" ON "periodic_processes" ("process_name", "active");
CREATE INDEX "periodic_process_deployments_periodic_process_id_idx" ON "periodic_process_deployments" ("periodic_process_id");
