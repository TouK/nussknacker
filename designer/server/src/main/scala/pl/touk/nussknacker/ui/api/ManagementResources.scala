package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.{Decoder, Encoder, Json, parser}
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.restmodel.{
  CustomActionRequest,
  CustomActionResponse,
  PerformSingleExecutionRequest,
  PerformSingleExecutionResponse
}
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessUnmarshallingError
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.AdhocTestParametersRequest
import pl.touk.nussknacker.ui.metrics.TimeMeasuring.measureTime
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.{RawScenarioTestData, ResultsWithCounts, ScenarioTestService}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

object ManagementResources {

  import io.circe.syntax._

  implicit val resultsWithCountsEncoder: Encoder[ResultsWithCounts] = deriveConfiguredEncoder

  private implicit val testResultsEncoder: Encoder[TestResults[Json]] = new Encoder[TestResults[Json]]() {

    implicit val nodeResult: Encoder[ResultContext[Json]]                              = deriveConfiguredEncoder
    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] = deriveConfiguredEncoder
    implicit val externalInvocationResult: Encoder[ExternalInvocationResult[Json]]     = deriveConfiguredEncoder

    // TODO: do we want more information here?
    implicit val throwableEncoder: Encoder[Throwable] = Encoder[Option[String]].contramap(th => Option(th.getMessage))
    implicit val exceptionResultEncoder: Encoder[ExceptionResult[Json]] = deriveConfiguredEncoder

    override def apply(a: TestResults[Json]): Json = a match {
      case TestResults(nodeResults, invocationResults, externalInvocationResults, exceptions) =>
        Json.obj(
          "nodeResults"       -> nodeResults.map { case (node, list) => node -> list.sortBy(_.id) }.asJson,
          "invocationResults" -> invocationResults.map { case (node, list) => node -> list.sortBy(_.contextId) }.asJson,
          "externalInvocationResults" -> externalInvocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId)
          }.asJson,
          "exceptions" -> exceptions.sortBy(_.context.id).asJson
        )
    }

  }

}

class ManagementResources(
    val processAuthorizer: AuthorizeProcess,
    protected val processService: ProcessService,
    deploymentService: DeploymentService,
    dispatcher: DeploymentManagerDispatcher,
    metricRegistry: MetricRegistry,
    scenarioTestServices: ProcessingTypeDataProvider[ScenarioTestService, _],
    typeToConfig: ProcessingTypeDataProvider[ModelData, _]
)(implicit val ec: ExecutionContext)
    extends Directives
    with LazyLogging
    with RouteWithUser
    with FailFastCirceSupport
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  import ManagementResources._

  // TODO: in the future we could use https://github.com/akka/akka-http/pull/1828 when we can bump version to 10.1.x
  private implicit final val plainBytes: FromEntityUnmarshaller[Array[Byte]] = Unmarshaller.byteArrayUnmarshaller
  private implicit final val plainString: FromEntityUnmarshaller[String]     = Unmarshaller.stringUnmarshaller

  def securedRoute(implicit user: LoggedUser): Route = {
    pathPrefix("adminProcessManagement") {
      path("snapshot" / ProcessNameSegment) { processName =>
        (post & processId(processName) & parameters(Symbol("savepointDir").?)) { (processId, savepointDir) =>
          canDeploy(processId) {
            complete {
              convertSavepointResultToResponse(
                dispatcher
                  .deploymentManagerUnsafe(processId)(ec, user)
                  .flatMap(_.processCommand(DMMakeScenarioSavepointCommand(processId.name, savepointDir)))
              )
            }
          }
        }
      } ~
        path("stop" / ProcessNameSegment) { processName =>
          (post & processId(processName) & parameters(Symbol("savepointDir").?)) { (processId, savepointDir) =>
            canDeploy(processId) {
              complete {
                convertSavepointResultToResponse(
                  dispatcher
                    .deploymentManagerUnsafe(processId)(ec, user)
                    .flatMap(_.processCommand(DMStopScenarioCommand(processId.name, savepointDir, user.toManagerUser)))
                )
              }
            }
          }
        } ~
        path("deploy" / ProcessNameSegment) { processName =>
          (post & processId(processName) & entity(as[Option[String]]) & parameters(Symbol("savepointPath"))) {
            (processIdWithName, comment, savepointPath) =>
              canDeploy(processIdWithName) {
                complete {
                  deploymentService
                    .processCommand(
                      RunDeploymentCommand(
                        // adminProcessManagement endpoint is not used by the designer client. It is a part of API for tooling purpose
                        commonData = CommonCommandData(processIdWithName, comment.flatMap(Comment.from), user),
                        nodesDeploymentData = NodesDeploymentData.empty,
                        stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromCustomSavepoint(savepointPath)
                      )
                    )
                    .map(_ => ())
                }
              }
          }
        }
    }
  } ~
    pathPrefix("processManagement") {

      path("deploy" / ProcessNameSegment) { processName =>
        (post & processId(processName) & entity(as[Option[String]])) { (processIdWithName, comment) =>
          canDeploy(processIdWithName) {
            complete {
              measureTime("deployment", metricRegistry) {
                deploymentService
                  .processCommand(
                    RunDeploymentCommand(
                      commonData = CommonCommandData(processIdWithName, comment.flatMap(Comment.from), user),
                      nodesDeploymentData = NodesDeploymentData.empty,
                      stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
                    )
                  )
                  .map(_ => ())
              }
            }
          }
        }
      } ~
        path("cancel" / ProcessNameSegment) { processName =>
          (post & processId(processName) & entity(as[Option[String]])) { (processIdWithName, comment) =>
            canDeploy(processIdWithName) {
              complete {
                measureTime("cancel", metricRegistry) {
                  deploymentService.processCommand(
                    CancelScenarioCommand(commonData =
                      CommonCommandData(processIdWithName, comment.flatMap(Comment.from), user)
                    )
                  )
                }
              }
            }
          }
        } ~
        // TODO: maybe Write permission is enough here?
        path("test" / ProcessNameSegment) { processName =>
          (post & processDetailsForName(processName)) { details =>
            canDeploy(details.idWithNameUnsafe) {
              formFields(Symbol("testData"), Symbol("scenarioGraph")) { (testDataContent, scenarioGraphJson) =>
                complete {
                  measureTime("test", metricRegistry) {
                    parser.parse(scenarioGraphJson).flatMap(Decoder[ScenarioGraph].decodeJson) match {
                      case Right(scenarioGraph) =>
                        scenarioTestServices
                          .forProcessingTypeUnsafe(details.processingType)
                          .performTest(
                            scenarioGraph,
                            details.processVersionUnsafe,
                            details.isFragment,
                            RawScenarioTestData(testDataContent)
                          )
                          .flatMap(mapResultsToHttpResponse)
                      case Left(error) =>
                        Future.failed(ProcessUnmarshallingError(error.toString))
                    }
                  }
                }
              }
            }
          }
        } ~
        path("generateAndTest" / ProcessNameSegment / IntNumber) { (processName, testSampleSize) =>
          {
            (post & entity(as[ScenarioGraph])) { scenarioGraph =>
              {
                processDetailsForName(processName)(user) { details =>
                  canDeploy(details.idWithNameUnsafe) {
                    complete {
                      measureTime("generateAndTest", metricRegistry) {
                        val scenarioTestService = scenarioTestServices.forProcessingTypeUnsafe(details.processingType)
                        scenarioTestService.generateData(
                          scenarioGraph,
                          details.processVersionUnsafe,
                          details.isFragment,
                          testSampleSize
                        ) match {
                          case Left(error) => Future.failed(ProcessUnmarshallingError(error))
                          case Right(rawScenarioTestData) =>
                            scenarioTestService
                              .performTest(
                                scenarioGraph,
                                details.processVersionUnsafe,
                                details.isFragment,
                                rawScenarioTestData
                              )
                              .flatMap(mapResultsToHttpResponse)
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        } ~
        path("testWithParameters" / ProcessNameSegment) { processName =>
          {
            (post & processDetailsForName(processName)) { process =>
              (post & entity(as[AdhocTestParametersRequest])) { testParametersRequest =>
                {
                  canDeploy(process.idWithNameUnsafe) {
                    complete {
                      scenarioTestServices
                        .forProcessingTypeUnsafe(process.processingType)
                        .performTest(
                          testParametersRequest.scenarioGraph,
                          process.processVersionUnsafe,
                          process.isFragment,
                          testParametersRequest.sourceParameters
                        )
                        .flatMap(mapResultsToHttpResponse)
                    }
                  }
                }
              }
            }
          }
        } ~
        path("customAction" / ProcessNameSegment) { processName =>
          (post & processId(processName) & entity(as[CustomActionRequest])) { (processIdWithName, req) =>
            complete {
              deploymentService
                .processCommand(
                  CustomActionCommand(
                    commonData = CommonCommandData(processIdWithName, req.comment.flatMap(Comment.from), user),
                    actionName = req.actionName,
                    params = req.params
                  )
                )
                .flatMap(actionResult =>
                  toHttpResponse(CustomActionResponse(isSuccess = true, actionResult.msg))(StatusCodes.OK)
                )
            }
          }
        } ~ path("performSingleExecution" / ProcessNameSegment) { processName =>
          (post & processId(processName) & entity(as[PerformSingleExecutionRequest])) { (processIdWithName, req) =>
            canDeploy(processIdWithName) {
              complete {
                measureTime("singleExecution", metricRegistry) {
                  deploymentService
                    .processCommand(
                      PerformSingleExecutionCommand(
                        commonData = CommonCommandData(processIdWithName, req.comment.flatMap(Comment.from), user),
                      )
                    )
                    .flatMap(actionResult =>
                      toHttpResponse(PerformSingleExecutionResponse(isSuccess = true, actionResult.msg))(StatusCodes.OK)
                    )
                }
              }
            }
          }
        }
    }

  private def mapResultsToHttpResponse: ResultsWithCounts => Future[HttpResponse] = { results =>
    Marshal(results).to[MessageEntity].map(en => HttpResponse(entity = en))
  }

  private def toHttpResponse[A: Encoder](a: A)(code: StatusCode): Future[HttpResponse] =
    Marshal(a).to[MessageEntity].map(en => HttpResponse(entity = en, status = code))

  private def convertSavepointResultToResponse(future: Future[SavepointResult]) = {
    future
      .map { case SavepointResult(path) => HttpResponse(entity = path, status = StatusCodes.OK) }
  }

}
