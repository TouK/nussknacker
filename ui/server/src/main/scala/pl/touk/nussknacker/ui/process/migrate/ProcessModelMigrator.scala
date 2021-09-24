package pl.touk.nussknacker.ui.process.migrate

import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.migration.{ProcessMigration, ProcessMigrations}
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.UpdateProcessAction

case class MigrationResult(process: CanonicalProcess, migrationsApplied: List[ProcessMigration]) {

  def id : String = process.metaData.id

  def toUpdateAction(processId: ProcessId): UpdateProcessAction = UpdateProcessAction(
    id = processId,
    deploymentData = GraphProcess(ProcessMarshaller.toJson(process).noSpaces),
    comment = s"Migrations applied: ${migrationsApplied.map(_.description).mkString(", ")}",
    increaseVersionWhenJsonNotChanged = true
  )

}

class ProcessModelMigrator(migrations: ProcessingTypeDataProvider[ProcessMigrations]) {

  def migrateProcess(processDetails: ProcessDetails, skipEmptyMigrations: Boolean) : Option[MigrationResult] = {
    for {
      migrations <- migrations.forType(processDetails.processingType)
      displayable <- processDetails.json
      migrationsToApply = findMigrationsToApply(migrations, processDetails.modelVersion) if migrationsToApply.nonEmpty || !skipEmptyMigrations
    } yield migrateWithMigrations(ProcessConverter.fromDisplayable(displayable), migrationsToApply)
  }

  private def findMigrationsToApply(migrations: ProcessMigrations, modelVersion: Option[Int]): List[ProcessMigration] = {
    migrations.processMigrations.toList.sortBy(_._1).dropWhile {
      case (migrationNumber, _) => migrationNumber <= modelVersion.getOrElse(0)
    }.map(_._2)
  }

  private def migrateWithMigrations(process: CanonicalProcess, migrationsToApply: List[ProcessMigration]): MigrationResult = {
    val resultProcess = migrationsToApply.foldLeft(process) {
      case (processToConvert, migration) => migration.migrateProcess(processToConvert)
    }
    MigrationResult(resultProcess, migrationsToApply)
  }

}

