package pl.touk.nussknacker.ui.process.migrate

import argonaut.PrettyParams
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.migration.{ProcessMigration, ProcessMigrations}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.process.marshall.{ProcessConverter, UiProcessMarshaller}
import pl.touk.nussknacker.ui.process.repository.{ProcessActivityRepository, ProcessRepository}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.nussknacker.ui.security.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

object ProcessModelMigrator {

  def apply(processRepository: ProcessRepository, processActivityRepository: ProcessActivityRepository,
            modelData: Map[ProcessingType, ModelData]) : ProcessModelMigrator = {
    val migrationsMap = modelData.mapValues(_.migrations).collect {
      case (k, Some(migrations)) => (k, migrations)
    }
    new ProcessModelMigrator(processRepository, processActivityRepository, migrationsMap)
  }


}

//TODO: things to consider/change
// - use of transaction
// - where should model version be stored? Per process version or globally?
class ProcessModelMigrator(processRepository: ProcessRepository, processActivityRepository: ProcessActivityRepository,
                           migrationsMap: Map[ProcessingType, ProcessMigrations]) {

  private val marshaller = UiProcessMarshaller()

  private val migrator = new SingleProcessMigrator(migrationsMap)

  //FIXME: transactions
  def migrate(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Unit] = {

    (for {
      processes <- processRepository.fetchProcessesDetails()
      subprocess <- processRepository.fetchSubProcessesDetails()
    } yield processes ++ subprocess).flatMap { processes =>
      Future.sequence(processes.flatMap { process =>
        migrator.migrateProcess(process).toList
      }.map(updateConvertedProcess)).map(_ => ())
    }

  }

  private def updateConvertedProcess(converted: SingleMigrationResult)(implicit loggedUser: LoggedUser, ec: ExecutionContext) : Future[Unit] = {
    val deploymentData = GraphProcess(marshaller.toJson(converted.process, PrettyParams.nospace))
    processRepository.updateProcess(converted.process.metaData.id, deploymentData).flatMap {
      case Right(Some(newVersion)) => processActivityRepository.addComment(newVersion.processId, newVersion.id,
        s"Migrations applied: ${converted.migrationsApplied.map(_.description).mkString(", ")}")
      case Right(_) => Future.successful(())
      case Left(error) => Future.failed(new RuntimeException(s"Failed to save process: $error"))
    }
  }
}

private[migrate] case class SingleMigrationResult(process: CanonicalProcess, migrationsApplied: List[ProcessMigration])

private[migrate] class SingleProcessMigrator(migrationsMap: Map[ProcessingType, ProcessMigrations]) {

  def migrateProcess(processDetails: ProcessDetails) : Option[SingleMigrationResult] = {
    for {
      migrations <- migrationsMap.get(processDetails.processingType)
      displayable <- processDetails.json
    } yield migrateWithMigrations(migrations,
            ProcessConverter.fromDisplayable(displayable), processDetails.modelVersion)
  }

  private def migrateWithMigrations(migrations: ProcessMigrations, process: CanonicalProcess, modelVersion: Option[Int])
    : SingleMigrationResult = {

    val migrationsToApply = migrations.processMigrations.toList.sortBy(_._1).dropWhile {
      case (migrationNumber, _) => migrationNumber <= modelVersion.getOrElse(0)
    }.map(_._2)

    val resultProcess = migrationsToApply.foldLeft(process) {
      case (processToConvert, migration) => migration.migrateProcess(processToConvert)
    }
    SingleMigrationResult(resultProcess, migrationsToApply)
  }


}

