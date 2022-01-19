package pl.touk.nussknacker.ui.api

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{ExceptionResult, ExpressionInvocationResult, MockedResult, NodeResult, ResultContext, TestData, TestResults}
import pl.touk.nussknacker.engine.api.DisplayJson
import pl.touk.nussknacker.ui.process.{ProcessService, deployment => uideployment}
import pl.touk.nussknacker.engine.api.deployment.{CustomActionError, CustomActionFailure, CustomActionInvalidStatus, CustomActionNonExisting, CustomActionNotImplemented, CustomActionResult, SavepointResult}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.api.EspErrorToHttp.toResponse
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.restmodel.{CustomActionRequest, CustomActionResponse}
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.process.deployment.{Snapshot, Stop, Test}
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.engine.api.CirceUtil._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import io.circe.generic.extras.{Configuration, defaults}

object ManagementResources {

  def apply(processCounter: ProcessCounter,
            managementActor: ActorRef,
            processAuthorizator: AuthorizeProcess,
            processRepository: FetchingProcessRepository[Future],
            featuresOptions: FeatureTogglesConfig,
            processResolving: UIProcessResolving,
            processService: ProcessService)
           (implicit ec: ExecutionContext,
            mat: Materializer, system: ActorSystem): ManagementResources = {
    new ManagementResources(
      processCounter,
      managementActor,
      featuresOptions.testDataSettings,
      processAuthorizator,
      processRepository,
      featuresOptions.deploySettings,
      processResolving,
      processService
    )
  }

  implicit val testResultsEncoder: Encoder[TestResults[Json]] = new Encoder[TestResults[Json]]() {

    implicit val nodeResult: Encoder[NodeResult[Json]] = deriveConfiguredEncoder
    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] = deriveConfiguredEncoder
    implicit val mockedResult: Encoder[MockedResult[Json]] = deriveConfiguredEncoder
    implicit val resultContext: Encoder[ResultContext[Json]] = deriveConfiguredEncoder
    //TODO: do we want more information here?
    implicit val throwable: Encoder[Throwable] = Encoder[Option[String]].contramap(th => Option(th.getMessage))
    implicit val exceptionResult: Encoder[ExceptionResult[Json]] = deriveConfiguredEncoder

    override def apply(a: TestResults[Json]): Json = a match {
      case TestResults(nodeResults, invocationResults, mockedResults, exceptions, _) => Json.obj(
        "nodeResults" -> nodeResults.asJson,
        "invocationResults" -> invocationResults.asJson,
        
        "mockedResults" -> mockedResults.asJson,
        "exceptions" -> exceptions.asJson
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

class ManagementResources(processCounter: ProcessCounter,
                          val managementActor: ActorRef,
                          testDataSettings: TestDataSettings,
                          val processAuthorizer: AuthorizeProcess,
                          val processRepository: FetchingProcessRepository[Future],
                          deploySettings: Option[DeploySettings],
                          processResolving: UIProcessResolving,
                          processService: ProcessService)
                         (implicit val ec: ExecutionContext, mat: Materializer, system: ActorSystem)
  extends Directives
    with LazyLogging
    with RouteWithUser
    with FailFastCirceSupport
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  //TODO: in the future we could use https://github.com/akka/akka-http/pull/1828 when we can bump version to 10.1.x
  private val durationFromConfig = system.settings.config.getDuration("akka.http.server.request-timeout")
  private implicit val timeout: Timeout = Timeout(durationFromConfig.toMillis millis)
  private implicit final val plainBytes: FromEntityUnmarshaller[Array[Byte]] = Unmarshaller.byteArrayUnmarshaller
  private implicit final val plainString: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller

  private def withComment: Directive1[Option[String]] =
    entity(as[Option[String]]).map(_.filterNot(_.isEmpty)).flatMap {
      case None if deploySettings.exists(_.requireComment) => reject(ValidationRejection("Comment is required", None))
      case comment => provide(comment)
    }

  def securedRoute(implicit user: LoggedUser): Route = {
    path("adminProcessManagement" / "snapshot" / Segment) { processName =>
      (post & processId(processName) & parameters('savepointDir.?)) { (processId, savepointDir) =>
        canDeploy(processId) {
          complete {
            convertSavepointResultToResponse(managementActor ? Snapshot(processId, user, savepointDir))
          }
        }
      }
    } ~
      path("adminProcessManagement" / "stop" / Segment) { processName =>
        (post & processId(processName) & parameters('savepointDir.?)) { (processId, savepointDir) =>
          canDeploy(processId) {
            complete {
              convertSavepointResultToResponse(managementActor ? Stop(processId, user, savepointDir))
            }
          }
        }
      } ~
      path("adminProcessManagement" / "deploy" / Segment ) { processName =>
        (post & processId(processName) & parameters('savepointPath)) { (processId, savepointPath) =>
          canDeploy(processId) {
            withComment { comment =>
              complete {
                processService
                  .deployProcess(processId, Some(savepointPath), comment)
                  .map(toResponse(StatusCodes.OK))
              }
            }
          }
        }
      } ~
      path("processManagement" / "deploy" / Segment) { processName =>
        (post & processId(processName)) { processId =>
          canDeploy(processId) {
            withComment { comment =>
              complete {
                processService
                  .deployProcess(processId, None, comment)
                  .map(toResponse(StatusCodes.OK))
              }
            }
          }
        }
      } ~
      path("processManagement" / "cancel" / Segment) { processName =>
        (post & processId(processName)) { processId =>
          canDeploy(processId) {
            withComment { comment =>
              complete {
                processService
                  .cancelProcess(processId, comment)
                  .map(toResponse(StatusCodes.OK))
              }
            }
          }
        }
      } ~
      //TODO: maybe Write permission is enough here?
      path("processManagement" / "test" / Segment) { processName =>
        (post & processId(processName)) { processId =>
          canDeploy(processId) {
            formFields('testData.as[Array[Byte]], 'processJson) { (testData, displayableProcessJson) =>
              complete {
                if (testData.length > testDataSettings.testDataMaxBytes) {
                  HttpResponse(StatusCodes.BadRequest, entity = "Too large test request")
                } else {
                  performTest(processId, testData, displayableProcessJson).flatMap { results =>
                    Marshal(results).to[MessageEntity].map(en => HttpResponse(entity = en))
                  }.recover(EspErrorToHttp.errorToHttp)
                }
              }
            }
          }
        }
      } ~
      path("processManagement" / "customAction" / Segment) { processName =>
        (post & processId(processName) & entity(as[CustomActionRequest])) { (process, req) =>
          val params = req.params.getOrElse(Map.empty)
          val customAction = uideployment.CustomAction(req.actionName, process, user, params)
          complete {
            (managementActor ? customAction)
              .mapTo[Either[CustomActionError, CustomActionResult]]
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

  private def performTest(id: ProcessIdWithName, testData: Array[Byte], displayableProcessJson: String)(implicit user: LoggedUser): Future[ResultsWithCounts] = {
    parse(displayableProcessJson).right.flatMap(Decoder[DisplayableProcess].decodeJson) match {
      case Right(process) =>
        val validationResult = processResolving.validateBeforeUiResolving(process)
        val canonical = processResolving.resolveExpressions(process, validationResult.typingInfo)
        val canonicalJson = ProcessMarshaller.toGraphProcess(canonical)
        (managementActor ? Test(id, canonicalJson, TestData(testData, testDataSettings.maxSamplesCount), user, ManagementResources.testResultsVariableEncoder)).mapTo[TestResults[Json]].flatMap { results =>
          assertTestResultsAreNotTooBig(results)
        }.map { results =>
          ResultsWithCounts(ManagementResources.testResultsEncoder(results), computeCounts(canonical, results))
        }
      case Left(error) =>
        Future.failed(UnmarshallError(error.toString))
    }
  }

  private def assertTestResultsAreNotTooBig(testResults: TestResults[Json]): Future[TestResults[Json]] = {
    val resultsMaxBytes = testDataSettings.resultsMaxBytes
    val testDataResultApproxByteSize = RamUsageEstimator.sizeOf(testResults)
    if (testDataResultApproxByteSize > resultsMaxBytes) {
      logger.info(s"Test data limit exceeded. Approximate test data size: $testDataResultApproxByteSize, but limit is: $resultsMaxBytes")
      Future.failed(new RuntimeException("Too much test data. Please decrease test input data size."))
    } else {
      Future.successful(testResults)
    }
  }

  private def computeCounts(canonical: CanonicalProcess, results: TestResults[_]): Map[String, NodeCount] = {
    val counts = results.nodeResults.map { case (key, nresults) =>
      key -> RawCount(nresults.size.toLong, results.exceptions.find(_.nodeId.contains(key)).size.toLong)
    }
    processCounter.computeCounts(canonical, counts.get)
  }

  private def convertSavepointResultToResponse(future: Future[Any]) = {
    future
      .mapTo[SavepointResult]
      .map { case SavepointResult(path) => HttpResponse(entity = path, status = StatusCodes.OK) }
      .recover(EspErrorToHttp.errorToHttp)
  }
}

@JsonCodec case class ResultsWithCounts(results: Json, counts: Map[String, NodeCount])
