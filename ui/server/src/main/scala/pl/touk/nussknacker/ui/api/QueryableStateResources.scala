package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import cats.data.EitherT
import db.util.DBIOActionInstances.DB
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.parser.parse
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.QueryableStateName
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.{ProcessObjectsFinder, ProcessService}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class QueryableStateResources(typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData],
                              val processRepository: FetchingProcessRepository[Future],
                              processService: ProcessService[DB],
                              val processAuthorizer: AuthorizeProcess)
                             (implicit val ec: ExecutionContext)
  extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  import pl.touk.nussknacker.engine.util.Implicits._

  def securedRoute(implicit user: LoggedUser): Route = {

    path("queryableState" / "list") {
      get {
        complete {
          prepareQueryableStates()
        }
      }
    } ~ path("queryableState" / "fetch") {
      parameters('processId, 'queryName, 'key ?) { (processName, queryName, key) =>
        (get & processId(processName)) { processId =>
          canDeploy(processId) {
            complete {
              queryState(processId, queryName, key.flatMap(_.safeValue))
            }
          }
        }
      }
    }
  }

  private def prepareQueryableStates()(implicit user: LoggedUser): Future[Map[String, List[QueryableStateName]]] = {
    processRepository.fetchAllProcessesDetails[DisplayableProcess]().map { processList =>
      ProcessObjectsFinder.findQueries(processList, typeToConfig.all.values.map(_.modelData.processDefinition))
    }
  }

  import cats.instances.future._
  import cats.syntax.either._

  private def queryState(processId: ProcessIdWithName, queryName: String, key: Option[String])(implicit user: LoggedUser): Future[Json] = {
    import QueryStateErrors._

    val fetchedJsonState = for {
      processingType <- EitherT.liftF(processRepository.fetchProcessingType(processId.id))
      state <- EitherT.liftF(processService.getProcessState(processId))
      jobId <- EitherT.fromEither(Either.fromOption(state.deploymentId, if (state.status.isDuringDeploy) deployInProgress(processId.name.value) else noJobRunning(processId.name.value)))
      jsonString <- EitherT.right(fetchState(processingType, jobId.value, queryName, key))
      json <- EitherT.fromEither(parse(jsonString).leftMap(msg => wrongJson(msg.message, jsonString)))
    } yield json
    fetchedJsonState.value.map {
      case Left(msg) => throw new RuntimeException(msg)
      case Right(json) => json
    }
  }

  private def fetchState(processingType: String, jobId: String, queryName: String, key: Option[String]): Future[String] = {
    typeToConfig.forTypeUnsafe(processingType).queryableClient match {
      case None => Future.failed(new Exception(s"Queryable client not found for processing type $processingType"))
      case Some(queryableClient) =>
        key match {
          case Some(k) => queryableClient.fetchJsonState(jobId, queryName, k)
          case None => queryableClient.fetchJsonState(jobId, queryName)
        }
    }
  }

  case class QueryableStateData(processId: String, queryNames: List[String])

  object QueryStateErrors {
    def noJob(processId: String) = s"There is no job for $processId"

    def noJobRunning(processId: String) = s"There is no running job for $processId"

    def deployInProgress(processId: String) = s"There is pending deployment for $processId, Try again later"

    def wrongJson(msg: String, json: String) = s"Unparsable json. Message: $msg, json: $json"
  }

}
