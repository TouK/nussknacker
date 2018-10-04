package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.{Directive0, Directives, Route}
import argonaut.ArgonautShapeless._
import argonaut.EncodeJson
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.migrate.{RemoteEnvironment, RemoteEnvironmentCommunicationError, TestMigrationResult}
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{ProcessDetails, ProcessNotFoundError}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.util.{Argonaut62Support, ProcessComparator}

import scala.concurrent.{ExecutionContext, Future}
import ProcessComparator._
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

class RemoteEnvironmentResources(remoteEnvironment: RemoteEnvironment,
                                 val processRepository: FetchingProcessRepository,
                                 val processAuthorizer:AuthorizeProcess)(implicit ec: ExecutionContext)
  extends Directives
    with Argonaut62Support
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  import argonaut.Argonaut._
  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  private implicit val differenceCodec = ProcessComparator.codec

  private implicit val map = EncodeJson.derive[TestMigrationResult]
  private implicit val encodeResults = EncodeJson.derive[TestMigrationSummary]
  private implicit val encodeDifference = EncodeJson.derive[ProcessDifference]
  private implicit val encodeDifference2 = EncodeJson.derive[EnvironmentComparisonResult]

  def route(implicit user: LoggedUser) : Route = {
      pathPrefix("remoteEnvironment") {
          path("compare") {
            get {
              complete {
                for {
                  processes <- processRepository.fetchProcessesDetails()
                  subprocesses <- processRepository.fetchSubProcessesDetails()
                  comparison <- compareProcesses(processes ++ subprocesses)
                } yield EspErrorToHttp.toResponseEither(comparison)
              }
            }
          } ~
          path(Segment / LongNumber / "compare" / LongNumber) { (processName, version, otherVersion) =>
            parameter('businessView ? false) { businessView =>
              (get & processId(processName)) { processId =>
                complete {
                  withProcess(processId, version, businessView, (process, _) => remoteEnvironment.compare(process, Some(otherVersion), businessView))
                }
              }
            }
          } ~
          path(Segment / LongNumber / "migrate") { (processName, version) =>
            (post & processId(processName)) { processId =>
              complete {
                withProcess(processId, version, businessView = false, remoteEnvironment.migrate)
              }
            }
          } ~
          path(Segment / "versions") { processName =>
            (get & processId(processName)) { processId =>
              complete {
                remoteEnvironment.processVersions(processId)
              }
            }
          } ~
          path("testAutomaticMigration") {
            get {
              complete {
                remoteEnvironment.testMigration
                  .flatMap(_.fold((Future.successful[HttpResponse] _)
                    .compose(EspErrorToHttp.espErrorToHttp), testMigrationResponse))
              }
            }
          }
      }
  }


  private def compareProcesses(processes: List[ProcessDetails])(implicit ec: ExecutionContext, user: LoggedUser)
    : Future[Either[EspError, EnvironmentComparisonResult]] = {
    val results = Future.sequence(processes.flatMap(_.json).map(compareOneProcess))
    results.map { comparisonResult =>
      comparisonResult.sequence.right
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

  private def withProcess[T:EncodeJson](processId: String, version: Long, businessView: Boolean,
                                        fun: (DisplayableProcess, String) => Future[Either[EspError, T]])(implicit user: LoggedUser) = {
    processRepository.fetchProcessDetailsForId(processId, version, businessView).map {
      _.flatMap { details =>
        details.json.map((_, details.processCategory))
      }
    }.flatMap {
      case Some((process, category)) => fun(process, category)
      case None => Future.successful(Left(ProcessNotFoundError(processId)))
    }.map(EspErrorToHttp.toResponseEither[T])
  }

  private def compareOneProcess(process: DisplayableProcess)(implicit ec: ExecutionContext, user: LoggedUser)
    : Future[Either[EspError, ProcessDifference]]= {
    remoteEnvironment.compare(process, None).map {
      case Right(differences) => Right(ProcessDifference(process.id, true, differences))
      case Left(RemoteEnvironmentCommunicationError(StatusCodes.NotFound, _)) => Right(ProcessDifference(process.id, false, Map()))
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