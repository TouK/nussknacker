package pl.touk.nussknacker.ui.process.migrate

import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.migration.{ProcessMigration, ProcessMigrations}
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.UpdateProcessAction

case class MigrationResult(process: CanonicalProcess, migrationsApplied: List[ProcessMigration]) {

  def id : String = process.metaData.id

  def toUpdateAction(processId: ProcessId): UpdateProcessAction = UpdateProcessAction(processId,
    GraphProcess(ProcessMarshaller.toJson(process).noSpaces),
    s"Migrations applied: ${migrationsApplied.map(_.description).mkString(", ")}",
    true
  )

}

class ProcessModelMigrator(migrations: ProcessingTypeDataProvider[ProcessMigrations]) {

  def migrateProcess(processDetails: ProcessDetails) : Option[MigrationResult] = {
    for {
      migrations <- migrations.forType(processDetails.processingType)
      displayable <- processDetails.json
      migrated <- migrateWithMigrations(migrations, ProcessConverter.fromDisplayable(displayable), processDetails.modelVersion)
    } yield migrated
  }

  private def migrateWithMigrations(migrations: ProcessMigrations, process: CanonicalProcess, modelVersion: Option[Int])
    : Option[MigrationResult] = {

    val migrationsToApply = migrations.processMigrations.toList.sortBy(_._1).dropWhile {
      case (migrationNumber, _) => migrationNumber <= modelVersion.getOrElse(0)
    }.map(_._2)

    if(migrationsToApply.isEmpty) None
    else {
      val resultProcess = migrationsToApply.foldLeft(process) {
        case (processToConvert, migration) => migration.migrateProcess(processToConvert)
      }
      Some(MigrationResult(resultProcess, migrationsToApply))
    }

  }


}

