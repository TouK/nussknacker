package pl.touk.nussknacker.ui.process.migrate

import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.migration.{ProcessMigration, ProcessMigrations}
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.UpdateProcessAction
import pl.touk.nussknacker.ui.process.repository.{MigrationComment, ScenarioWithDetailsEntity}

final case class MigrationResult(process: CanonicalProcess, migrationsApplied: List[ProcessMigration]) {

  def toUpdateAction(processId: ProcessId): UpdateProcessAction = UpdateProcessAction(
    processId = processId,
    canonicalProcess = process,
    comment = Option(migrationsApplied).filter(_.nonEmpty).map(MigrationComment),
    increaseVersionWhenJsonNotChanged = true,
    forwardedUserName = None
  )

}

class ProcessModelMigrator(migrations: ProcessMigrations) {

  def migrateProcess(
      processDetails: ScenarioWithDetailsEntity[ScenarioGraph],
      skipEmptyMigrations: Boolean
  ): Option[MigrationResult] = {
    migrateProcess(
      processDetails.name,
      processDetails.json,
      processDetails.modelVersion,
      processDetails.processCategory,
      skipEmptyMigrations
    )
  }

  def migrateProcess(
      scenarioName: ProcessName,
      scenarioGraph: ScenarioGraph,
      modelVersion: Option[Int],
      category: String,
      skipEmptyMigrations: Boolean
  ): Option[MigrationResult] = {
    val migrationsToApply = findMigrationsToApply(migrations, modelVersion)
    if (migrationsToApply.nonEmpty || !skipEmptyMigrations) {
      Some(
        migrateWithMigrations(
          CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, scenarioName),
          category,
          migrationsToApply
        )
      )
    } else {
      None
    }
  }

  private def findMigrationsToApply(
      migrations: ProcessMigrations,
      modelVersion: Option[Int]
  ): List[ProcessMigration] = {
    migrations.processMigrations.toList
      .sortBy(_._1)
      .dropWhile { case (migrationNumber, _) =>
        migrationNumber <= modelVersion.getOrElse(0)
      }
      .map(_._2)
  }

  private def migrateWithMigrations(
      process: CanonicalProcess,
      category: String,
      migrationsToApply: List[ProcessMigration]
  ): MigrationResult = {
    val (resultProcess, migrationsApplied) = migrationsToApply.foldLeft((process, Nil: List[ProcessMigration])) {
      case ((processToConvert, migrationsAppliedAcc), migration) =>
        val migrated = migration.migrateProcess(processToConvert, category)
        val migrationsApplied =
          if (migrated != processToConvert) migration :: migrationsAppliedAcc else migrationsAppliedAcc
        (migrated, migrationsApplied)
    }
    MigrationResult(resultProcess, migrationsApplied.reverse)
  }

}
