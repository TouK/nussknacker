package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder, parser}
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.restmodel.{CustomActionRequest, CustomActionResponse}
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.{TestFromParametersRequest, prepareTestFromParametersDecoder}
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessUnmarshallingError
import pl.touk.nussknacker.ui.metrics.TimeMeasuring.measureTime
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.deployment.{DeploymentManagerDispatcher, DeploymentService}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.{RawScenarioTestData, ScenarioTestService, TestResultsJsonEncoder}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

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
                  .flatMap(_.processCommand(MakeScenarioSavepointCommand(processId.name, savepointDir)))
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
                    .flatMap(_.processCommand(StopScenarioCommand(processId.name, savepointDir, user.toManagerUser)))
                )
              }
            }
          }
        } ~
        path("deploy" / ProcessNameSegment) { processName =>
          (post & processId(processName) & entity(as[Option[String]]) & parameters(Symbol("savepointPath"))) {
            (processId, comment, savepointPath) =>
              canDeploy(processId) {
                complete {
                  deploymentService
                    .deployProcessAsync(processId, Some(savepointPath), comment)
                    .map(_ => ())
                }
              }
          }
        }
    }
  } ~
    pathPrefix("processManagement") {

      path("deploy" / ProcessNameSegment) { processName =>
        (post & processId(processName) & entity(as[Option[String]])) { (processId, comment) =>
          canDeploy(processId) {
            complete {
              measureTime("deployment", metricRegistry) {
                deploymentService
                  .deployProcessAsync(processId, None, comment)
                  .map(_ => ())
              }
            }
          }
        }
      } ~
        path("cancel" / ProcessNameSegment) { processName =>
          (post & processId(processName) & entity(as[Option[String]])) { (processId, comment) =>
            canDeploy(processId) {
              complete {
                measureTime("cancel", metricRegistry) {
                  deploymentService
                    .cancelProcess(processId, comment)
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
                        val modelData          = typeToConfig.forTypeUnsafe(details.processingType)
                        val testResultsEncoder = TestResultsJsonEncoder(modelData)

                        scenarioTestServices
                          .forTypeUnsafe(details.processingType)
                          .performTest(
                            details.idWithNameUnsafe,
                            scenarioGraph,
                            details.isFragment,
                            RawScenarioTestData(testDataContent)
                          )
                          .map(testResultsEncoder.encode)
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
                        val scenarioTestService = scenarioTestServices.forTypeUnsafe(details.processingType)
                        val modelData           = typeToConfig.forTypeUnsafe(details.processingType)
                        val testResultsEncoder  = TestResultsJsonEncoder(modelData)

                        scenarioTestService.generateData(
                          scenarioGraph,
                          processName,
                          details.isFragment,
                          testSampleSize
                        ) match {
                          case Left(error) => Future.failed(ProcessUnmarshallingError(error))
                          case Right(rawScenarioTestData) =>
                            scenarioTestService
                              .performTest(
                                details.idWithNameUnsafe,
                                scenarioGraph,
                                details.isFragment,
                                rawScenarioTestData
                              )
                              .map(testResultsEncoder.encode)
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
              val modelData     = typeToConfig.forTypeUnsafe(process.processingType)
              val jsonConverter = TestResultsJsonEncoder(modelData)

              implicit val requestDecoder: Decoder[TestFromParametersRequest] =
                prepareTestFromParametersDecoder(modelData)

              (post & entity(as[TestFromParametersRequest])) { testParametersRequest =>
                {
                  canDeploy(process.idWithNameUnsafe) {
                    complete {
                      scenarioTestServices
                        .forTypeUnsafe(process.processingType)
                        .performTest(
                          process.idWithNameUnsafe,
                          testParametersRequest.scenarioGraph,
                          process.isFragment,
                          testParametersRequest.sourceParameters
                        )
                        .map(jsonConverter.encode)
                    }
                  }
                }
              }
            }
          }
        } ~
        path("customAction" / ProcessNameSegment) { processName =>
          (post & processId(processName) & entity(as[CustomActionRequest])) { (process, req) =>
            val params = req.params.getOrElse(Map.empty)
            complete {
              deploymentService
                .invokeCustomAction(req.actionName, process, params)
                .flatMap(actionResult =>
                  toHttpResponse(CustomActionResponse(isSuccess = true, actionResult.msg))(StatusCodes.OK)
                )
            }
          }
        }
    }

  private def toHttpResponse[A: Encoder](a: A)(code: StatusCode): Future[HttpResponse] =
    Marshal(a).to[MessageEntity].map(en => HttpResponse(entity = en, status = code))

  private def convertSavepointResultToResponse(future: Future[SavepointResult]): Future[HttpResponse] = {
    future
      .map { case SavepointResult(path) => HttpResponse(entity = path, status = StatusCodes.OK) }
  }

}
