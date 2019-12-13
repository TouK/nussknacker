package pl.touk.nussknacker.ui.process.migrate

import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.migration.{ProcessMigration, ProcessMigrations}
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.WriteProcessRepository.UpdateProcessAction

case class MigrationResult(process: CanonicalProcess, migrationsApplied: List[ProcessMigration]) {

  def id : String = process.metaData.id

  def toUpdateAction(processId: ProcessId): UpdateProcessAction = UpdateProcessAction(processId,
    GraphProcess(ProcessMarshaller.toJson(process).noSpaces),
    s"Migrations applied: ${migrationsApplied.map(_.description).mkString(", ")}"
  )

}

class ProcessModelMigrator(migrationsMap: Map[ProcessingType, ProcessMigrations]) {

  def migrateProcess(processDetails: ProcessDetails) : Option[MigrationResult] = {
    for {
      migrations <- migrationsMap.get(processDetails.processingType)
      displayable <- processDetails.json
    } yield migrateWithMigrations(migrations, ProcessConverter.fromDisplayable(displayable), processDetails.modelVersion)
  }

  private def migrateWithMigrations(migrations: ProcessMigrations, process: CanonicalProcess, modelVersion: Option[Int])
    : MigrationResult = {

    val migrationsToApply = migrations.processMigrations.toList.sortBy(_._1).dropWhile {
      case (migrationNumber, _) => migrationNumber <= modelVersion.getOrElse(0)
    }.map(_._2)

    val resultProcess = migrationsToApply.foldLeft(process) {
      case (processToConvert, migration) => migration.migrateProcess(processToConvert)
    }
    MigrationResult(resultProcess, migrationsToApply)
  }


}

