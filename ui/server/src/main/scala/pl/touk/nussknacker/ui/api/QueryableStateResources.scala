package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import cats.data.EitherT
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.parser.parse
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.DeploymentId
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.QueryableStateName
import pl.touk.nussknacker.engine.graph.node.CustomNodeData
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.{JobStatusService, ProcessObjectsFinder}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class QueryableStateResources(typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData],
                              val processRepository: FetchingProcessRepository[Future],
                              jobStatusService: JobStatusService,
                              val processAuthorizer:AuthorizeProcess)
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
    /*
      The flow is:
      - we checkout process, compile it (probably we should take version that is deployed?) and get custom transformer implementation.
        We do it this way, so that transformer can have logic for determining type of state
      - queryableClient (i.e. Flink one...) retrieves state type from transformer and can invoke state using this information
      - We assume that this works *only* if model in NK UI is compatible with the one in deployed process (add check for that in the future)...
     */
    } ~ path("typedQueryableState" / "fetch") {
      parameters('processId, 'queryName, 'nodeId, 'key ?) { (processName, queryName, nodeId, key) =>
        (get & processId(processName)) { processId =>
          canDeploy(processId) {
            complete {

              import QueryStateErrors._
              import cats.instances.future._
              import cats.syntax.either._

              def fetchTypedState(deploymentId: DeploymentId, displayableProcess: BaseProcessDetails[DisplayableProcess]) = {
                val data = typeToConfig.forTypeUnsafe(displayableProcess.processingType)
                val modelData = data.modelData
                modelData.withThisAsContextClassLoader {
                  val transformer: Any = prepareTransformer(modelData, displayableProcess, nodeId)
                  data.queryableClient.get.fetchState(deploymentId, NodeId(nodeId), queryName, transformer, key.get)
                }
              }
              val encoder = BestEffortJsonEncoder(failOnUnkown = false)

              val fetchedJsonState = for {
                details <- EitherT.fromOptionF(processRepository.fetchLatestProcessDetailsForProcessId[DisplayableProcess](processId.id), noJob(processName))
                state <- EitherT.fromOptionF(jobStatusService.retrieveJobStatus(processId), noJob(processName))
                value <- EitherT.right[String](fetchTypedState(state.deploymentId.get, details))
              } yield value
              fetchedJsonState.value.map {
                case Left(msg) => throw new RuntimeException(msg)
                case Right(value) => encoder.encode(value)
              }
            }
          }
        }
      }
    }
  }

  //we compile process to get context transformation object, it's QueryableClient resposibility to handle details (e.g. type)
  private def prepareTransformer(modelData: ModelData, displayableProcess: BaseProcessDetails[DisplayableProcess], nodeId: String) = {
    val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withExpressionParsers {
      case spel: SpelExpressionParser => spel.typingDictLabels
    }
    val compiler = new NodeCompiler(modelData.processWithObjectsDefinition,
      expressionCompiler, modelData.modelClassLoader.classLoader)
    val process = displayableProcess.json.get
    val ctx = modelData
      .validator
      .validate(ProcessConverter.fromDisplayable(process)).typing(nodeId)
    val node = process.nodes.find(_.id == nodeId).get.asInstanceOf[CustomNodeData]
    compiler.compileCustomNodeObject(node, Left(ctx.inputValidationContext), false)(process.metaData, NodeId(nodeId))
      .compiledObject.toOption.get match {
      case a: ContextTransformation => a.implementation
      case b => b
    }
  }

  private def prepareQueryableStates()(implicit user: LoggedUser): Future[Map[String, List[QueryableStateName]]] = {
    processRepository.fetchAllProcessesDetails[DisplayableProcess]().map { processList =>
      ProcessObjectsFinder.findQueries(processList, typeToConfig.all.filterKeys(_ == "streaming-generic").values.map(_.modelData.processDefinition))
    }
  }

  import cats.instances.future._
  import cats.syntax.either._
  private def queryState(processId: ProcessIdWithName, queryName: String, key: Option[String])(implicit user: LoggedUser): Future[Json] = {
    import QueryStateErrors._

    val fetchedJsonState = for {
      processingType <- EitherT.liftF(processRepository.fetchProcessingType(processId.id))
      state <- EitherT(jobStatusService.retrieveJobStatus(processId).map(Either.fromOption(_, noJob(processId.name.value))))
      jobId <- EitherT.fromEither(Either.fromOption(state.deploymentId, if (state.status.isDuringDeploy) deployInProgress(processId.name.value) else noJobRunning(processId.name.value)))
      jsonString <- EitherT.right(fetchState(processingType, jobId, queryName, key))
      json <- EitherT.fromEither(parse(jsonString).leftMap(msg => wrongJson(msg.message, jsonString)))
    } yield json
    fetchedJsonState.value.map {
      case Left(msg) => throw new RuntimeException(msg)
      case Right(json) => json
    }
  }

  private def fetchState(processingType: String, jobId: DeploymentId, queryName: String, key: Option[String]): Future[String] = {
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
