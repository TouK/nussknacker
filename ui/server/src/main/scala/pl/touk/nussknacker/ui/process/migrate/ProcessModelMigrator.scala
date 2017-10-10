package pl.touk.nussknacker.ui.process.migrate

import argonaut.PrettyParams
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.migration.{ProcessMigration, ProcessMigrations}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.process.marshall.{ProcessConverter, UiProcessMarshaller}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{ProcessDetails, ValidatedProcessDetails}
import pl.touk.nussknacker.ui.process.repository.WriteProcessRepository.UpdateProcessAction

case class MigrationResult(process: CanonicalProcess, migrationsApplied: List[ProcessMigration]) {

  def id : String = process.metaData.id

  def toUpdateAction : UpdateProcessAction = UpdateProcessAction(id,
    GraphProcess(UiProcessMarshaller.toJson(process, PrettyParams.nospace)),
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

