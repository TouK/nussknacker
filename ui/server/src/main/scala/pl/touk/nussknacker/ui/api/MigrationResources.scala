package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import argonaut.EncodeJson
import argonaut.ArgonautShapeless._
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.migrate.{MigratorCommunicationError, ProcessMigrator, TestMigrationResult}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{ProcessDetails, ProcessNotFoundError}
import pl.touk.nussknacker.ui.security.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.util.ProcessComparator
import pl.touk.http.argonaut.Argonaut62Support

import scala.concurrent.{ExecutionContext, Future}

class MigrationResources(migrator: ProcessMigrator,
                         processRepository: ProcessRepository)(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with RouteWithUser {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._
  import argonaut.Argonaut._

  private implicit val differenceCodec = ProcessComparator.codec

  private implicit val map = EncodeJson.derive[TestMigrationResult]
  private implicit val encodeResults = EncodeJson.derive[TestMigrationSummary]
  private implicit val encodeDifference = EncodeJson.derive[ProcessDifference]
  private implicit val encodeDifference2 = EncodeJson.derive[EnvironmentComparisonResult]

  def route(implicit user: LoggedUser) : Route = {
    authorize(user.hasPermission(Permission.Deploy)) {

      pathPrefix("migration") {
          path("compare") {
            get {
              complete {
                for {
                  processes <- processRepository.fetchProcessesDetails()
                  comparison <- compareProcesses(processes)
                } yield EspErrorToHttp.toResponseEither(comparison)
              }
            }
          } ~
          path("compare" / Segment) { processId =>
            get {
              complete {
                withProcess(processId, (process) => migrator.compare(process))
              }
            }
          } ~
          path("migrate" / Segment) { processId =>
            post {
              complete {
                withProcess(processId, (process) => migrator.migrate(process))
              }
            }
          } ~
          path("testAutomaticMigration") {
            get {
              complete {
                migrator.testMigration
                  .flatMap(_.fold((Future.successful[HttpResponse] _)
                    .compose(EspErrorToHttp.espErrorToHttp), testMigrationResponse))
              }
            }
          }
      }
    }
  }


  private def compareProcesses(processes: List[ProcessDetails])(implicit ec: ExecutionContext, user: LoggedUser)
    : Future[Either[EspError, EnvironmentComparisonResult]] = {
    val results = Future.sequence(processes.flatMap(_.json).map(compareOneProcess))
    results.map { comparisonResult =>
      comparisonResult.sequenceU.right
        .map(_.filterNot(_.areSame))
        .right
        .map(EnvironmentComparisonResult)
    }
  }

  private def testMigrationResponse(testMigrationResults: List[TestMigrationResult]) : Future[HttpResponse] = {
    val failedMigrations = testMigrationResults.filter(_.shouldFail).map(_.converted.id)
    val (status, message) = failedMigrations match {
      case Nil => (StatusCodes.OK, "Migrations successful")
      case _ => (StatusCodes.InternalServerError,
        s"Migration failed, following processes have new errors: ${failedMigrations.mkString(", ")}")
    }
    val summary = TestMigrationSummary(message, testMigrationResults)

    Marshal(summary).to[MessageEntity].map(e => HttpResponse(status = status, entity = e))
  }

  private def withProcess[T:EncodeJson](processId: String, fun: (DisplayableProcess) => Future[Either[EspError, T]])(implicit user: LoggedUser) = {
    processRepository.fetchLatestProcessDetailsForProcessId(processId).map {
      _.flatMap(_.json)
    }.flatMap {
      case Some(dispProcess) => fun(dispProcess)
      case None => Future.successful(Left(ProcessNotFoundError(processId)))
    }.map(EspErrorToHttp.toResponseEither[T])
  }

  private def compareOneProcess(process: DisplayableProcess)(implicit ec: ExecutionContext, user: LoggedUser)
    : Future[Either[EspError, ProcessDifference]]= {
    migrator.compare(process).map {
      case Right(differences) => Right(ProcessDifference(process.id, true, differences))
      case Left(MigratorCommunicationError(StatusCodes.NotFound, _)) => Right(ProcessDifference(process.id, false, Map()))
      case Left(error) => Left(error)
    }
  }

}

case class TestMigrationSummary(message: String, testMigrationResults: List[TestMigrationResult])

//we make additional class here to be able to e.g. compare model versions...
case class EnvironmentComparisonResult(processDifferences:List[ProcessDifference])

case class ProcessDifference(id: String, presentOnOther: Boolean, differences: Map[String, ProcessComparator.Difference]) {
  def areSame: Boolean = presentOnOther && differences.isEmpty
}