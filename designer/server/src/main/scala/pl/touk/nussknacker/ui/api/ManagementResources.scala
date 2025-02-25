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
import pl.touk.nussknacker.engine.definition.test.TestInfoProvider.{
  ScenarioTestDataGenerationError,
  TestDataPreparationError
}
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.restmodel.{CancelRequest, DeployRequest, RunOffScheduleRequest, RunOffScheduleResponse}
import pl.touk.nussknacker.ui.OtherError
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessUnmarshallingError
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.AdhocTestParametersRequest
import pl.touk.nussknacker.ui.metrics.TimeMeasuring.measureTime
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioTestDataSerDe.{DeserializationError, SerializationError}
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.{GenerateTestDataError, PerformTestError}
import pl.touk.nussknacker.ui.process.test.{RawScenarioTestData, ResultsWithCounts, ScenarioTestService}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

object ManagementResources {

  import pl.touk.nussknacker.engine.api.CirceUtil._

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

  final case class GenerateTestDataDesignerError(message: String) extends OtherError(message)

  private object GenerateTestDataDesignerError {

    def apply(generateTestDataError: ScenarioTestService.GenerateTestDataError): GenerateTestDataDesignerError = {
      GenerateTestDataDesignerError(generateTestDataError match {
        case GenerateTestDataError.ScenarioTestDataGenerationError(cause) =>
          cause match {
            case ScenarioTestDataGenerationError.NoDataGenerated => TestingApiErrorMessages.noDataGenerated
            case ScenarioTestDataGenerationError.NoSourcesWithTestDataGeneration =>
              TestingApiErrorMessages.noSourcesWithTestDataGeneration
          }
        case GenerateTestDataError.ScenarioTestDataSerializationError(cause) =>
          cause match {
            case SerializationError.TooManyCharactersGenerated(length, limit) =>
              TestingApiErrorMessages.tooManyCharactersGenerated(length, limit)
          }
        case GenerateTestDataError.TooManySamplesRequestedError(maxSamples) =>
          TestingApiErrorMessages.tooManySamplesRequested(maxSamples)
      })
    }

  }

  final case class PerformTestDesignerError(message: String) extends OtherError(message)

  private object PerformTestDesignerError {

    def apply(performTestError: ScenarioTestService.PerformTestError): PerformTestDesignerError = {
      PerformTestDesignerError(performTestError match {
        case PerformTestError.DeserializationError(cause) =>
          cause match {
            case DeserializationError.TooManySamples(size, limit)       => "???"
            case DeserializationError.RecordParsingError(rawTestRecord) => ???
            case DeserializationError.NoRecords                         => ???
          }
        case PerformTestError.TestDataPreparationError(cause) =>
          cause match {
            case TestDataPreparationError.MissingSourceError(sourceId, recordIndex) => ???
            case TestDataPreparationError.MultipleSourcesError(recordIndex)         => ???
          }
        case PerformTestError.TestResultsSizeExceeded(approxSizeInBytes, maxBytes) => ???
      })
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

  // TODO: This (deployRequestEntity and cancelRequestEntity) is used as a transition from comment-as-plain-text-body to json.
  //  e.g. touk/nussknacker-example-scenarios-library, that is used in e2e tests, uses plain text comment.
  // https://github.com/TouK/nussknacker-scenario-examples-library/pull/7
  // To be replaced by `entity(as[DeployRequest]))` and `entity(as[CancelRequest]))`.
  private def deployRequestEntity: Directive1[DeployRequest] = {
    entity(as[Option[String]]).flatMap { optStr =>
      {
        optStr match {
          case None => provide(DeployRequest(None, None))
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
                provide(DeployRequest(Some(body), None))
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
                        nodesDeploymentData = request.nodesDeploymentData.getOrElse(NodesDeploymentData.empty),
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
        (post & processId(processName) & deployRequestEntity) { (processIdWithName, request) =>
          canDeploy(processIdWithName) {
            complete {
              measureTime("deployment", metricRegistry) {
                deploymentService
                  .processCommand(
                    RunDeploymentCommand(
                      commonData = CommonCommandData(processIdWithName, request.comment.flatMap(Comment.from), user),
                      nodesDeploymentData = request.nodesDeploymentData.getOrElse(NodesDeploymentData.empty),
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
                          .flatMap {
                            case Left(error)  => Future.failed(PerformTestDesignerError(error))
                            case Right(value) => mapResultsToHttpResponse(value)
                          }
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
                          case Left(error) => Future.failed(GenerateTestDataDesignerError(error))
                          case Right(rawScenarioTestData) =>
                            scenarioTestService
                              .performTest(
                                scenarioGraph,
                                details.processVersionUnsafe,
                                details.isFragment,
                                rawScenarioTestData
                              )
                              .flatMap {
                                case Left(error)  => Future.failed(PerformTestDesignerError(error))
                                case Right(value) => mapResultsToHttpResponse(value)
                              }
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
                        .flatMap {
                          case Left(error)  => Future.failed(PerformTestDesignerError(error))
                          case Right(value) => mapResultsToHttpResponse(value)
                        }
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
