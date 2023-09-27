package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, parser}
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.DisplayJson
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.{CustomActionRequest, CustomActionResponse}
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.api.EspErrorToHttp.toResponseTryPF
import pl.touk.nussknacker.ui.api.NodesResources.prepareTestFromParametersDecoder
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.ui.metrics.TimeMeasuring.measureTime
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.deployment.{CustomActionInvokerService, DeploymentManagerDispatcher, DeploymentService}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.{DeploymentComment, FetchingProcessRepository}
import pl.touk.nussknacker.ui.process.test.{RawScenarioTestData, ResultsWithCounts, ScenarioTestService}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

object ManagementResources {

  import pl.touk.nussknacker.engine.api.CirceUtil._

  implicit val resultsWithCountsEncoder: Encoder[ResultsWithCounts[Json]] = deriveConfiguredEncoder

  implicit val testResultsEncoder: Encoder[TestResults[Json]] = new Encoder[TestResults[Json]]() {

    implicit val nodeResult: Encoder[NodeResult[Json]] = deriveConfiguredEncoder
    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] = deriveConfiguredEncoder
    implicit val externalInvocationResult: Encoder[ExternalInvocationResult[Json]] = deriveConfiguredEncoder
    implicit val resultContext: Encoder[ResultContext[Json]] = deriveConfiguredEncoder
    //TODO: do we want more information here?
    implicit val throwable: Encoder[Throwable] = Encoder[Option[String]].contramap(th => Option(th.getMessage))
    implicit val exceptionResult: Encoder[ExceptionResult[Json]] = deriveConfiguredEncoder

    override def apply(a: TestResults[Json]): Json = a match {
      case TestResults(nodeResults, invocationResults, externalInvocationResults, exceptions, _) => Json.obj(
        "nodeResults" -> nodeResults.map { case (node, list) => node -> list.sortBy(_.context.id) }.asJson,
        "invocationResults" -> invocationResults.map { case (node, list) => node -> list.sortBy(_.contextId) }.asJson,
        "externalInvocationResults" -> externalInvocationResults.map { case (node, list) => node -> list.sortBy(_.contextId) }.asJson,
        "exceptions" -> exceptions.sortBy(_.context.id).asJson
      )
    }
  }

  val testResultsVariableEncoder: Any => io.circe.Json = {
    case displayable: DisplayJson =>
      def safeString(a: String) = Option(a).map(Json.fromString).getOrElse(Json.Null)

      val displayableJson = displayable.asJson
      displayable.originalDisplay match {
        case None => Json.obj("pretty" -> displayableJson)
        case Some(original) => Json.obj("original" -> safeString(original), "pretty" -> displayableJson)
      }
    case null => Json.Null
    case a => Json.obj("pretty" -> BestEffortJsonEncoder(failOnUnknown = false, a.getClass.getClassLoader).circeEncoder.apply(a))
  }

}

class ManagementResources(val processAuthorizer: AuthorizeProcess,
                          val processRepository: FetchingProcessRepository[Future],
                          deploymentService: DeploymentService,
                          dispatcher: DeploymentManagerDispatcher,
                          customActionInvokerService: CustomActionInvokerService,
                          metricRegistry: MetricRegistry,
                          scenarioTestService: ScenarioTestService,
                          typeToConfig: ProcessingTypeDataProvider[ModelData, _])
                         (implicit val ec: ExecutionContext)
  extends Directives
    with LazyLogging
    with RouteWithUser
    with FailFastCirceSupport
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  import ManagementResources._

  //TODO: in the future we could use https://github.com/akka/akka-http/pull/1828 when we can bump version to 10.1.x
  private implicit final val plainBytes: FromEntityUnmarshaller[Array[Byte]] = Unmarshaller.byteArrayUnmarshaller
  private implicit final val plainString: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller

  def securedRoute(implicit user: LoggedUser): Route = {
    pathPrefix("adminProcessManagement") {
      path("snapshot" / Segment) { processName =>
        (post & processId(processName) & parameters(Symbol("savepointDir").?)) { (processId, savepointDir) =>
          canDeploy(processId) {
            complete {
              convertSavepointResultToResponse(
                dispatcher.deploymentManagerUnsafe(processId.id)(ec, user).flatMap(_.savepoint(processId.name, savepointDir)))
            }
          }
        }
      } ~
        path("stop" / Segment) { processName =>
          (post & processId(processName) & parameters(Symbol("savepointDir").?)) { (processId, savepointDir) =>
            canDeploy(processId) {
              complete {
                convertSavepointResultToResponse(
                  dispatcher.deploymentManagerUnsafe(processId.id)(ec, user).flatMap(_.stop(processId.name, savepointDir, user.toManagerUser)))
              }
            }
          }
        } ~
        path("deploy" / Segment) { processName =>
          (post & processId(processName) & entity(as[Option[String]]) & parameters(Symbol("savepointPath"))) { (processId, comment, savepointPath) =>
            canDeploy(processId) {
              complete {
                deploymentService
                  .deployProcessAsync(processId, Some(savepointPath), comment).map(_ => ())
                  .andThen(toResponseTryPF(StatusCodes.OK))
              }
            }
          }
        }
    } ~
      pathPrefix("processManagement") {
        path("deploy" / Segment) { processName =>
          (post & processId(processName) & entity(as[Option[String]])) { (processId, comment) =>
            canDeploy(processId) {
              complete {
                measureTime("deployment", metricRegistry) {
                  deploymentService
                    .deployProcessAsync(processId, None, comment).map(_ => ())
                    .andThen(toResponseTryPF(StatusCodes.OK))
                }
              }
            }
          }
        } ~
          path("cancel" / Segment) { processName =>
            (post & processId(processName) & entity(as[Option[String]])) { (processId, comment) =>
              canDeploy(processId) {
                complete {
                  measureTime("cancel", metricRegistry) {
                    deploymentService
                      .cancelProcess(processId, comment)
                      .andThen(toResponseTryPF(StatusCodes.OK))
                  }
                }
              }
            }
          } ~
          //TODO: maybe Write permission is enough here?
          path("test" / Segment) { processName =>
            (post & processId(processName)) { idWithName =>
              canDeploy(idWithName.id) {
                formFields(Symbol("testData"), Symbol("processJson")) { (testDataContent, displayableProcessJson) =>
                  complete {
                    measureTime("test", metricRegistry) {
                      parser.parse(displayableProcessJson).flatMap(Decoder[DisplayableProcess].decodeJson) match {
                        case Right(displayableProcess) =>
                          scenarioTestService
                            .performTest(idWithName, displayableProcess, RawScenarioTestData(testDataContent), testResultsVariableEncoder)
                            .flatMap { results =>
                              Marshal(results).to[MessageEntity].map(en => HttpResponse(entity = en))
                            }
                            .recover(EspErrorToHttp.errorToHttp)
                        case Left(error) =>
                          Future.failed(UnmarshallError(error.toString))
                      }
                    }
                  }
                }
              }
            }
          } ~
          path("generateAndTest" / IntNumber) {
            testSampleSize => {
              (post & entity(as[DisplayableProcess])) { displayableProcess => {
                processId(displayableProcess.id) { idWithName =>
                  canDeploy(idWithName) {
                    complete {
                      measureTime("generateAndTest", metricRegistry) {
                        scenarioTestService.generateData(displayableProcess, testSampleSize) match {
                          case Left(error) => Future.failed(UnmarshallError(error))
                          case Right(rawScenarioTestData) =>
                            scenarioTestService.performTest(idWithName, displayableProcess, rawScenarioTestData, testResultsVariableEncoder)
                              .flatMap { results => Marshal(results).to[MessageEntity].map(en => HttpResponse(entity = en)) }
                              .recover(EspErrorToHttp.errorToHttp)
                        }
                      }
                    }
                  }
                }
              }
              }
            }
          } ~
          path("testWithParameters" / Segment) {
            processName => {
              (post & processDetailsForName[Unit](processName)) { process =>
                val modelData = typeToConfig.forTypeUnsafe(process.processingType)
                implicit val requestDecoder: Decoder[TestFromParametersRequest] = prepareTestFromParametersDecoder(modelData)
                (post & entity(as[TestFromParametersRequest])) { testParametersRequest => {
                  processId(testParametersRequest.displayableProcess.id) { idWithName =>
                    canDeploy(idWithName) {
                      complete {
                        scenarioTestService.performTest(idWithName, testParametersRequest.displayableProcess,
                          testParametersRequest.sourceParameters, testResultsVariableEncoder)
                          .flatMap { results => Marshal(results).to[MessageEntity].map(en => HttpResponse(entity = en)) }
                          .recover(EspErrorToHttp.errorToHttp)
                      }
                    }
                  }
                }
                }
              }
            }
          } ~
          path("customAction" / Segment) { processName =>
            (post & processId(processName) & entity(as[CustomActionRequest])) { (process, req) =>
              val params = req.params.getOrElse(Map.empty)
              complete {
                customActionInvokerService
                  .invokeCustomAction(req.actionName, process, params)
                  .flatMap(actionResult => toHttpResponse(CustomActionResponse(actionResult))(StatusCodes.OK))
                  .recover(EspErrorToHttp.errorToHttp)
              }
            }
          }
      }
  }

  private def toHttpResponse[A: Encoder](a: A)(code: StatusCode): Future[HttpResponse] =
    Marshal(a).to[MessageEntity].map(en => HttpResponse(entity = en, status = code))

  private def convertSavepointResultToResponse(future: Future[SavepointResult]) = {
    future
      .map { case SavepointResult(path) => HttpResponse(entity = path, status = StatusCodes.OK) }
      .recover(EspErrorToHttp.errorToHttp)
  }
}
