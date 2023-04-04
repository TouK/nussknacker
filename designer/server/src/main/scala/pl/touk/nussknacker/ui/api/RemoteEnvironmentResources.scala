package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.migrate.{RemoteEnvironment, RemoteEnvironmentCommunicationError, TestMigrationResult}
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.util.{EspPathMatchers, ProcessComparator}

import scala.concurrent.{ExecutionContext, Future}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.security.api.LoggedUser

class RemoteEnvironmentResources(remoteEnvironment: RemoteEnvironment,
                                 val processRepository: FetchingProcessRepository[Future],
                                 val processAuthorizer:AuthorizeProcess)
                                (implicit val ec: ExecutionContext)
  extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives
    with EspPathMatchers {

  def securedRoute(implicit user: LoggedUser) : Route = {
      pathPrefix("remoteEnvironment") {
          path("compare") {
            get {
              complete {
                for {
                  processes <- processRepository.fetchProcessesDetails[DisplayableProcess](FetchProcessesDetailsQuery.unarchived)
                  comparison <- compareProcesses(processes)
                } yield EspErrorToHttp.toResponseEither(comparison)
              }
            }
          } ~
          path(Segment / VersionIdSegment / "compare" / VersionIdSegment) { (processName, version, otherVersion) =>
            (get & processId(processName)) { processId =>
              complete {
                withProcess(processId.id, version, (process, _) => remoteEnvironment.compare(process, Some(otherVersion)))
              }
            }
          } ~
          path(Segment / VersionIdSegment / "migrate") { (processName, version) =>
            (post & processId(processName)) { processId =>
              complete {
                withProcess(processId.id, version, remoteEnvironment.migrate)
              }
            }
          } ~
          path(Segment / "versions") { processName =>
            (get & processId(processName)) { processId =>
              complete {
                remoteEnvironment.processVersions(processId.name)
              }
            }
          }
      }
  }


  private def compareProcesses(processes: List[ProcessDetails])(implicit ec: ExecutionContext, user: LoggedUser)
    : Future[Either[EspError, EnvironmentComparisonResult]] = {
    val results = Future.sequence(processes.map(p => compareOneProcess(p.json)))
    results.map { comparisonResult =>
      comparisonResult.sequence[XError, ProcessDifference]
        .map(_.filterNot(_.areSame))
        .map(EnvironmentComparisonResult.apply)
    }
  }

  private def withProcess[T:Encoder](processId: ProcessId, version: VersionId,
                                     fun: (DisplayableProcess, String) => Future[Either[EspError, T]])(implicit user: LoggedUser) = {
    processRepository.fetchProcessDetailsForId[DisplayableProcess](processId, version).map {
      _.map{ details => (details.json, details.processCategory)}
    }.flatMap {
      case Some((process, category)) => fun(process, category)
      case None => Future.successful(Left(ProcessNotFoundError(processId.value.toString)))
    }.map(EspErrorToHttp.toResponseEither[T])
  }

  private def compareOneProcess(process: DisplayableProcess)(implicit ec: ExecutionContext, user: LoggedUser)
    : Future[Either[EspError, ProcessDifference]]= {
    remoteEnvironment.compare(process, None).map {
      case Right(differences) => Right(ProcessDifference(process.id, presentOnOther = true, differences))
      case Left(RemoteEnvironmentCommunicationError(StatusCodes.NotFound, _)) => Right(ProcessDifference(process.id, presentOnOther = false, Map()))
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
