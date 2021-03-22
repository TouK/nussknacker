package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.{Directive0, Directives, Route}
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.migrate.{RemoteEnvironment, RemoteEnvironmentCommunicationError, TestMigrationResult}
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.util.ProcessComparator

import scala.concurrent.{ExecutionContext, Future}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{ProcessDetails}
import pl.touk.nussknacker.ui.security.api.{LoggedUser}

class RemoteEnvironmentResources(remoteEnvironment: RemoteEnvironment,
                                 val processRepository: FetchingProcessRepository[Future],
                                 val processAuthorizer:AuthorizeProcess)
                                (implicit val ec: ExecutionContext)
  extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  def securedRoute(implicit user: LoggedUser) : Route = {
      pathPrefix("remoteEnvironment") {
          path("compare") {
            get {
              complete {
                for {
                  processes <- processRepository.fetchProcessesDetails[DisplayableProcess]()
                  subprocesses <- processRepository.fetchSubProcessesDetails[DisplayableProcess]()
                  comparison <- compareProcesses(processes ++ subprocesses)
                } yield EspErrorToHttp.toResponseEither(comparison)
              }
            }
          } ~
          path(Segment / LongNumber / "compare" / LongNumber) { (processName, version, otherVersion) =>
            parameter('businessView ? false) { businessView =>
              (get & processId(processName)) { processId =>
                complete {
                  withProcess(processId.id, version, businessView, (process, _) => remoteEnvironment.compare(process, Some(otherVersion), businessView))
                }
              }
            }
          } ~
          path(Segment / LongNumber / "migrate") { (processName, version) =>
            (post & processId(processName)) { processId =>
              complete {
                withProcess(processId.id, version, businessView = false, remoteEnvironment.migrate)
              }
            }
          } ~
          path(Segment / "versions") { processName =>
            (get & processId(processName)) { processId =>
              complete {
                remoteEnvironment.processVersions(processId.name)
              }
            }
          } ~
          path("testAutomaticMigration") {
            get {
              complete {
                remoteEnvironment.testMigration()
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
      comparisonResult.sequence[XError, ProcessDifference].right
        .map(_.filterNot(_.areSame))
        .right
        .map(EnvironmentComparisonResult.apply)
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

  private def withProcess[T:Encoder](processId: ProcessId, version: Long, businessView: Boolean,
                                     fun: (DisplayableProcess, String) => Future[Either[EspError, T]])(implicit user: LoggedUser) = {
    processRepository.fetchProcessDetailsForId[DisplayableProcess](processId, version, businessView).map {
      _.flatMap { details =>
        details.json.map((_, details.processCategory))
      }
    }.flatMap {
      case Some((process, category)) => fun(process, category)
      case None => Future.successful(Left(ProcessNotFoundError(processId.value.toString)))
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

@JsonCodec case class TestMigrationSummary(message: String, testMigrationResults: List[TestMigrationResult])

//we make additional class here to be able to e.g. compare model versions...
@JsonCodec case class EnvironmentComparisonResult(processDifferences:List[ProcessDifference])

@JsonCodec case class ProcessDifference(id: String, presentOnOther: Boolean, differences: Map[String, ProcessComparator.Difference]) {
  def areSame: Boolean = presentOnOther && differences.isEmpty
}