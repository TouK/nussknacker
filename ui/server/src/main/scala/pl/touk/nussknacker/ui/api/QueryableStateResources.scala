package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import argonaut.{Json, Parse}
import cats.data.EitherT
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.QueryableStateName
import pl.touk.nussknacker.engine.flink.queryablestate.EspQueryableClient
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.{JobStatusService, ProcessObjectsFinder}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.concurrent.{ExecutionContext, Future}

class QueryableStateResources(processDefinition: Map[ProcessingType, ModelData],
                              processRepository: FetchingProcessRepository,
                              queryableClient: () => EspQueryableClient,
                              jobStatusService: JobStatusService)
                             (implicit ec: ExecutionContext) extends Directives with Argonaut62Support  with RouteWithUser {

  import pl.touk.nussknacker.ui.codec.UiCodecs._
  import pl.touk.nussknacker.ui.util.CollectionsEnrichments._

  def route(implicit user: LoggedUser): Route = {
    authorize(user.hasPermission(Permission.Deploy)) {
      path("queryableState" / "list") {
        get {
          complete {
            prepareQueryableStates()
          }
        }
      } ~ path("queryableState" / "fetch") {
        parameters('processId, 'queryName, 'key?) { (processId, queryName, key) =>
          get {
            complete {
              queryState(processId, queryName, key.flatMap(_.safeValue))
            }
          }
        }
      }
    }
  }

  private def prepareQueryableStates()(implicit user: LoggedUser): Future[Map[String, List[QueryableStateName]]] = {
    processRepository.fetchProcessesDetails().map { processList =>
      ProcessObjectsFinder.findQueries(processList, processDefinition(ProcessingType.Streaming).processDefinition)
    }
  }

  import cats.instances.future._
  import cats.syntax.either._
  private def queryState(processId: String, queryName: String, key: Option[String])(implicit user: LoggedUser): Future[Json] = {
    import QueryStateErrors._

    val fetchedJsonState = for {
      status <- EitherT(jobStatusService.retrieveJobStatus(processId).map(Either.fromOption(_, noJob(processId))))
      jobId <- EitherT.fromEither(Either.fromOption(status.flinkJobId, if (status.isDeployInProgress) deployInProgress(processId) else noJobRunning(processId)))
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
      case Some(k) => queryableClient().fetchJsonState(jobId, queryName, k)
      case None => queryableClient().fetchJsonState(jobId, queryName)
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
