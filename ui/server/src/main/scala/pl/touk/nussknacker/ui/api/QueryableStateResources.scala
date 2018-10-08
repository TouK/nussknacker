package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import argonaut.{Json, Parse}
import cats.data.EitherT
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.QueryableStateName
import pl.touk.nussknacker.engine.flink.queryablestate.EspQueryableClient
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.{JobStatusService, ProcessIdWithName, ProcessObjectsFinder}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.concurrent.{ExecutionContext, Future}

class QueryableStateResources(processDefinition: Map[ProcessingType, ModelData],
                              val processRepository: FetchingProcessRepository,
                              queryableClient: EspQueryableClient,
                              jobStatusService: JobStatusService,
                              val processAuthorizer:AuthorizeProcess)
                             (implicit val ec: ExecutionContext)
  extends Directives
    with Argonaut62Support
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  import pl.touk.nussknacker.ui.codec.UiCodecs._
  import pl.touk.nussknacker.ui.util.CollectionsEnrichments._

  def route(implicit user: LoggedUser): Route = {

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
    processRepository.fetchAllProcessesDetails().map { processList =>
      ProcessObjectsFinder.findQueries(processList, processDefinition.values.map(_.processDefinition))
    }
  }

  import cats.instances.future._
  import cats.syntax.either._
  private def queryState(processId: ProcessIdWithName, queryName: String, key: Option[String])(implicit user: LoggedUser): Future[Json] = {
    import QueryStateErrors._

    val fetchedJsonState = for {
      status <- EitherT(jobStatusService.retrieveJobStatus(processId).map(Either.fromOption(_, noJob(processId.name.value))))
      jobId <- EitherT.fromEither(Either.fromOption(status.flinkJobId, if (status.isDeployInProgress) deployInProgress(processId.name.value) else noJobRunning(processId.name.value)))
      jsonString <- EitherT.right(fetchState(jobId, queryName, key))
      json <- EitherT.fromEither(Parse.parse(jsonString).leftMap(msg => wrongJson(msg, jsonString)))
    } yield json
    fetchedJsonState.value.map {
      case Left(msg) => throw new RuntimeException(msg)
      case Right(json) => json
    }
  }

  private def fetchState(jobId: String, queryName: String, key: Option[String]): Future[String] = {
    key match {
      case Some(k) => queryableClient.fetchJsonState(jobId, queryName, k)
      case None => queryableClient.fetchJsonState(jobId, queryName)
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
