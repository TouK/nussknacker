package db.migration

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.db.migration.SlickMigration

import scala.concurrent.ExecutionContext.Implicits.global

trait V1_062__ScenarioSchedulingTablesModificationsDefinition extends SlickMigration with LazyLogging {

  import profile.api._

  override def migrateActions: DBIOAction[Any, NoStream, Effect.All] = {
    logger.info("Starting migration V1_062__ScenarioSchedulingTablesModifications")
    for {
      _ <- sqlu"""ALTER TABLE "periodic_scenarios" RENAME TO "scheduled_scenarios";"""
      _ <- sqlu"""ALTER TABLE "scheduled_scenarios" ALTER COLUMN "input_config_during_execution" DROP NOT NULL;"""
      _ <- sqlu"""ALTER TABLE "scheduled_scenarios" ADD COLUMN "resolved_scenario_json" VARCHAR(10485760);"""
      _ <- sqlu"""ALTER TABLE "periodic_scenario_deployments" RENAME TO "scheduled_scenario_deployments";"""
    } yield logger.info("Execution finished for migration V1_062__ScenarioSchedulingTablesModifications")
  }

}
