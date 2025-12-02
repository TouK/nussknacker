package pl.touk.nussknacker.ui.api

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, Encoder, parser}
import io.dropwizard.metrics5.MetricRegistry
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCode, StatusCodes}
import org.apache.pekko.http.scaladsl.server._
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.deployment.LatestVersion
import pl.touk.nussknacker.restmodel.{CancelRequest, DeployRequest, DeployResponse, RunOffScheduleRequest, RunOffScheduleResponse}
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessUnmarshallingError
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{SkipResultsPerNode, SkipResultsPerTransition}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.ResultsWithCountsDto
import pl.touk.nussknacker.ui.metrics.TimeMeasuring.measureTime
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.testcase.{ScenarioWithTestCase, TestCase}
import pl.touk.nussknacker.ui.process.test.{ScenarioTestService, SerializedScenarioRecordsContent}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

object ManagementResources {
  final case class PerformTestDesignerError(message: String) extends BadRequestError(message)
}

class ManagementResources(
    val processAuthorizer: AuthorizeProcess,
    protected val processService: ProcessService,
    deploymentService: DeploymentService,
    dispatcher: DeploymentManagerDispatcher,
    metricRegistry: MetricRegistry,
    scenarioTestServices: ProcessingTypeDataProvider[ScenarioTestService, _],
)(implicit val ec: ExecutionContext)
    extends Directives
    with LazyLogging
    with RouteWithUser
    with FailFastCirceSupport
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  import pl.touk.nussknacker.ui.api.description.scenarioTesting.ResultsWithCountsDtoCodecs._

  import ManagementResources._

  // TODO: in the future we could use https://github.com/akka/akka-http/pull/1828 when we can bump version to 10.1.x
  private implicit final val plainBytes: FromEntityUnmarshaller[Array[Byte]] = Unmarshaller.byteArrayUnmarshaller
  private implicit final val plainString: FromEntityUnmarshaller[String]     = Unmarshaller.stringUnmarshaller

  // TODO: This (deployRequestEntity and cancelRequestEntity) is used as a transition from comment-as-plain-text-body to json.
  //  e.g. touk/nussknacker-example-scenarios-library, that is used in e2e tests, uses plain text comment.
  // https://github.com/TouK/nussknacker-scenario-examples-library/pull/7
  // To be replaced by `entity(as[DeployRequest]))` and `entity(as[CancelRequest]))`.
  private def deployRequestEntity: Directive1[DeployRequest] = {
    entity(as[Option[String]]).flatMap { optStr =>
      {
        optStr match {
          case None => provide(DeployRequest(None, None, None))
          case Some(body) =>
            io.circe.parser.parse(body) match {
              case Right(json) =>
                json.as[DeployRequest] match {
                  case Right(request) =>
                    provide(request)
                  case Left(notValidDeployRequest) =>
                    reject(MalformedRequestContentRejection("Invalid deploy request", notValidDeployRequest))
                }
              case Left(notJson) =>
                // assume deployment request contains plaintext comment only
                provide(DeployRequest(Some(body), None, None))
            }
        }
      }
    }
  }

  private def cancelRequestEntity: Directive1[CancelRequest] = {
    entity(as[Option[String]]).flatMap { optStr =>
      {
        optStr match {
          case None => provide(CancelRequest(None))
          case Some(body) =>
            io.circe.parser.parse(body) match {
              case Right(json) =>
                json.as[CancelRequest] match {
                  case Right(request) =>
                    provide(request)
                  case Left(notValidRequest) =>
                    reject(MalformedRequestContentRejection("Invalid cancel request", notValidRequest))
                }
              case Left(notJson) =>
                // assume cancel request contains plaintext comment only
                provide(CancelRequest(Some(body)))
            }
        }
      }
    }
  }

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
          (post & processId(processName) & deployRequestEntity & parameters(Symbol("savepointPath"))) {
            (processIdWithName, request, savepointPath) =>
              canDeploy(processIdWithName) {
                complete {
                  deploymentService
                    .processCommand(
                      RunDeploymentCommand(
                        // adminProcessManagement endpoint is not used by the designer client. It is a part of API for tooling purpose
                        commonData = CommonCommandData(processIdWithName, request.comment.flatMap(Comment.from), user),
                        nodesDeploymentData = request.nodesDeploymentData,
                        stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromCustomSavepoint(savepointPath),
                        scenarioSource = request.scenarioGraphSource.getOrElse(LatestVersion),
                      )
                    )
                    .map(result => toHttpResponse(DeployResponse(result.deployedScenarioVersionId))(StatusCodes.OK))
                }
              }
          }
        }
    }
  } ~
    pathPrefix("processManagement") {

      path("deploy" / ProcessNameSegment) { processName =>
        (post & processId(processName) & deployRequestEntity) { (processIdWithName, request) =>
          canDeploy(processIdWithName) {
            complete {
              measureTime("deployment", metricRegistry) {
                deploymentService
                  .processCommand(
                    RunDeploymentCommand(
                      commonData = CommonCommandData(processIdWithName, request.comment.flatMap(Comment.from), user),
                      nodesDeploymentData = request.nodesDeploymentData,
                      stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
                      scenarioSource = request.scenarioGraphSource.getOrElse(LatestVersion),
                    )
                  )
                  .map(result => toHttpResponse(DeployResponse(result.deployedScenarioVersionId))(StatusCodes.OK))
              }
            }
          }
        }
      } ~
        path("redeploy" / ProcessNameSegment) { processName =>
          (post & processId(processName) & deployRequestEntity) { (processIdWithName, request) =>
            canDeploy(processIdWithName) {
              complete {
                measureTime("deployment", metricRegistry) {
                  deploymentService
                    .processCommand(
                      RunRedeploymentCommand(
                        commonData = CommonCommandData(processIdWithName, request.comment.flatMap(Comment.from), user),
                        nodesDeploymentData = request.nodesDeploymentData,
                        stateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint,
                        scenarioSource = request.scenarioGraphSource.getOrElse(LatestVersion),
                      )
                    )
                    .map(result => toHttpResponse(DeployResponse(result.deployedScenarioVersionId))(StatusCodes.OK))
                }
              }
            }
          }
        } ~
        path("cancel" / ProcessNameSegment) { processName =>
          (post & processId(processName) & cancelRequestEntity) { (processIdWithName, request) =>
            canDeploy(processIdWithName) {
              complete {
                measureTime("cancel", metricRegistry) {
                  deploymentService.processCommand(
                    CancelScenarioCommand(commonData =
                      CommonCommandData(processIdWithName, request.comment.flatMap(Comment.from), user)
                    )
                  )
                }
              }
            }
          }
        } ~
        path("testCase" / ProcessNameSegment) { processName =>
          (post & processDetailsForName(
            processName
          ) & skipResultsPerTransitionQueryParam & skipResultsPerNodeQueryParam & entity(as[ScenarioWithTestCase])) { //todo: do skipResultsPerTransitionQueryParam and skipResultsPerNodeQueryParam parameters are needed?
            (details, skipResultsPerTransition, skipResultsPerNode, scenarioWithTestCase) =>
              canDeploy(details.idWithNameUnsafe) {
                complete {
                  measureTime("testCase", metricRegistry) {
                    scenarioTestServices
                      .forProcessingTypeUnsafe(details.processingType)
                      .performTestCase(
                        scenarioWithTestCase.scenario,
                        details.processVersionUnsafe,
                        details.isFragment,
                        scenarioWithTestCase.testCase
                      )
                      .flatMap {
                        case Left(error) =>
                          Future.failed(PerformTestDesignerError(TestingApiErrorMessages.from(error)))
                        case Right(value) =>
                          mapResultsToHttpResponse(
                            ResultsWithCountsDto.from(
                              resultsWithCounts = value,
                              skipResultsPerNode = SkipResultsPerNode(skipResultsPerNode),
                              skipResultsPerTransition = SkipResultsPerTransition(skipResultsPerTransition),
                            )
                          )
                      }
                  }
                }
              }
          }
        } ~
        // TODO: maybe Write permission is enough here?
        path("test" / ProcessNameSegment) { processName =>
          (post & processDetailsForName(
            processName
          ) & skipResultsPerTransitionQueryParam & skipResultsPerNodeQueryParam) {
            (details, skipResultsPerTransition, skipResultsPerNode) =>
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
                              SerializedScenarioRecordsContent(testDataContent)
                            )
                            .flatMap {
                              case Left(error) =>
                                Future.failed(PerformTestDesignerError(TestingApiErrorMessages.from(error)))
                              case Right(value) =>
                                mapResultsToHttpResponse(
                                  ResultsWithCountsDto.from(
                                    resultsWithCounts = value,
                                    skipResultsPerNode = SkipResultsPerNode(skipResultsPerNode),
                                    skipResultsPerTransition = SkipResultsPerTransition(skipResultsPerTransition),
                                  )
                                )
                            }
                        case Left(error) =>
                          Future.failed(ProcessUnmarshallingError(error.toString))
                      }
                    }
                  }
                }
              }
          }
        } ~ path(("runOffSchedule" | "performSingleExecution") / ProcessNameSegment) {
          processName => // backward compatibility purpose
            (post & processId(processName) & entity(as[RunOffScheduleRequest])) { (processIdWithName, req) =>
              canDeploy(processIdWithName) {
                complete {
                  measureTime("singleExecution", metricRegistry) {
                    deploymentService
                      .processCommand(
                        RunOffScheduleCommand(
                          commonData = CommonCommandData(processIdWithName, req.comment.flatMap(Comment.from), user),
                        )
                      )
                      .flatMap(actionResult =>
                        toHttpResponse(RunOffScheduleResponse(isSuccess = true, actionResult.msg))(StatusCodes.OK)
                      )
                  }
                }
              }
            }
        }
    }

  private def mapResultsToHttpResponse: ResultsWithCountsDto => Future[HttpResponse] = { results =>
    Marshal(results).to[MessageEntity].map(en => HttpResponse(entity = en))
  }

  private def toHttpResponse[A: Encoder](a: A)(code: StatusCode): Future[HttpResponse] =
    Marshal(a).to[MessageEntity].map(en => HttpResponse(entity = en, status = code))

  private def skipResultsPerNodeQueryParam =
    parameters(Symbol("skipResultsPerNode").as[Boolean].withDefault(false))

  private def skipResultsPerTransitionQueryParam =
    parameters(Symbol("skipResultsPerTransition").as[Boolean].withDefault(false))

  private def convertSavepointResultToResponse(future: Future[SavepointResult]) = {
    future
      .map { case SavepointResult(path) => HttpResponse(entity = path, status = StatusCodes.OK) }
  }

}
