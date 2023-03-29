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
import pl.touk.nussknacker.engine.api.DisplayJson
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.{CustomActionRequest, CustomActionResponse}
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.api.EspErrorToHttp.toResponseTryPF
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.ui.metrics.TimeMeasuring.measureTime
import pl.touk.nussknacker.ui.process.deployment.{CustomActionInvokerService, DeploymentManagerDispatcher, DeploymentService}
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
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
    case a => Json.obj("pretty" -> BestEffortJsonEncoder(failOnUnkown = false, a.getClass.getClassLoader).circeEncoder.apply(a))
  }


}

class ManagementResources(val processAuthorizer: AuthorizeProcess,
                          val processRepository: FetchingProcessRepository[Future],
                          deploymentCommentSettings: Option[DeploymentCommentSettings],
                          deploymentService: DeploymentService,
                          dispatcher: DeploymentManagerDispatcher,
                          customActionInvokerService: CustomActionInvokerService,
                          metricRegistry: MetricRegistry,
                          scenarioTestService: ScenarioTestService)
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

  case class ValidationError(message: String) extends Exception(message) with BadRequestError

  private def withDeploymentComment: Directive1[Option[DeploymentComment]] = {
    entity(as[Option[String]]).flatMap{ comment =>
      DeploymentComment.createDeploymentComment(comment, deploymentCommentSettings) match {
        case Valid(deploymentComment) => provide(deploymentComment)
        case Invalid(exc) => complete(EspErrorToHttp.espErrorToHttp(ValidationError(exc.getMessage)))
      }
    }
  }

  def securedRoute(implicit user: LoggedUser): Route = {
    path("adminProcessManagement" / "snapshot" / Segment) { processName =>
      (post & processId(processName) & parameters(Symbol("savepointDir").?)) { (processId, savepointDir) =>
        canDeploy(processId) {
          complete {
            convertSavepointResultToResponse(
              dispatcher.deploymentManagerUnsafe(processId.id)(ec, user).flatMap(_.savepoint(processId.name, savepointDir)))
          }
        }
      }
    } ~
      path("adminProcessManagement" / "stop" / Segment) { processName =>
        (post & processId(processName) & parameters(Symbol("savepointDir").?)) { (processId, savepointDir) =>
          canDeploy(processId) {
            complete {
              convertSavepointResultToResponse(
                dispatcher.deploymentManagerUnsafe(processId.id)(ec, user).flatMap(_.stop(processId.name, savepointDir, user.toManagerUser)))
            }
          }
        }
      } ~
      path("adminProcessManagement" / "deploy" / Segment ) { processName =>
        (post & processId(processName) & parameters(Symbol("savepointPath"))) { (processId, savepointPath) =>
          canDeploy(processId) {
            withDeploymentComment { deploymentComment =>
              complete {
                deploymentService
                  .deployProcessAsync(processId, Some(savepointPath), deploymentComment).map(_ => ())
                  .andThen(toResponseTryPF(StatusCodes.OK))
              }
            }
          }
        }
      } ~
      path("processManagement" / "deploy" / Segment) { processName =>
        (post & processId(processName)) { processId =>
          canDeploy(processId) {
            withDeploymentComment { deploymentComment =>
              complete {
                measureTime("deployment", metricRegistry) {
                  deploymentService
                    .deployProcessAsync(processId, None, deploymentComment).map(_ => ())
                    .andThen(toResponseTryPF(StatusCodes.OK))
                }
              }
            }
          }
        }
      } ~
      path("processManagement" / "cancel" / Segment) { processName =>
        (post & processId(processName)) { processId =>
          canDeploy(processId) {
            withDeploymentComment { deploymentComment =>
              complete {
                measureTime("cancel", metricRegistry) {
                  deploymentService
                    .cancelProcess(processId, deploymentComment)
                    .andThen(toResponseTryPF(StatusCodes.OK))
                }
              }
            }
          }
        }
      } ~
      //TODO: maybe Write permission is enough here?
      path("processManagement" / "test" / Segment) { processName =>
        (post & processId(processName)) { idWithName =>
          canDeploy(idWithName.id) {
            formFields(Symbol("testData"), Symbol("processJson")) { (testDataContent, displayableProcessJson) =>
              complete {
                measureTime("test", metricRegistry) {
                  parser.parse(displayableProcessJson).flatMap(Decoder[DisplayableProcess].decodeJson) match {
                    case Right(displayableProcess) =>
                      scenarioTestService.performTest(idWithName, displayableProcess, RawScenarioTestData(testDataContent), testResultsVariableEncoder).flatMap { results =>
                        Marshal(results).to[MessageEntity].map(en => HttpResponse(entity = en))
                      }.recover(EspErrorToHttp.errorToHttp)
                    case Left(error) =>
                      Future.failed(UnmarshallError(error.toString))
                  }
                }
              }
            }
          }
        }
      } ~
      path("processManagement" / "generateAndTest" / IntNumber ) {
        testSampleSize => {
          (post & entity(as[DisplayableProcess])) { displayableProcess => {
            processId(displayableProcess.id) { idWithName =>
              canDeploy(idWithName) {
                complete {
                  measureTime("generateAndTest", metricRegistry) {
                    scenarioTestService.generateData(displayableProcess, testSampleSize) match {
                      case Left(error) => Future.failed(UnmarshallError(error))
                      case Right(rawScenarioTestData) => {
                        scenarioTestService.performTest(idWithName, displayableProcess, rawScenarioTestData, testResultsVariableEncoder)
                          .flatMap { results => Marshal(results).to[MessageEntity].map(en => HttpResponse(entity = en)) }
                          .recover(EspErrorToHttp.errorToHttp)
                      }
                    }
                  }
                }
              }
            }
          }}
        }
      } ~
      path("processManagement" / "customAction" / Segment) { processName =>
        (post & processId(processName) & entity(as[CustomActionRequest])) { (process, req) =>
          val params = req.params.getOrElse(Map.empty)
          complete {
            customActionInvokerService.invokeCustomAction(req.actionName, process, params)
              .flatMap {
                case res@Right(_) =>
                  toHttpResponse(CustomActionResponse(res))(StatusCodes.OK)
                case res@Left(err) =>
                  val response = toHttpResponse(CustomActionResponse(res)) _
                  err match {
                    case _: CustomActionFailure => response(StatusCodes.InternalServerError)
                    case _: CustomActionInvalidStatus => response(StatusCodes.Forbidden)
                    case _: CustomActionNotImplemented => response(StatusCodes.NotImplemented)
                    case _: CustomActionNonExisting => response(StatusCodes.NotFound)
                  }
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
