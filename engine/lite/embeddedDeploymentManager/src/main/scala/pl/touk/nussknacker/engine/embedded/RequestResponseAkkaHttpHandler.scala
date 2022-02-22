package pl.touk.nussknacker.engine.embedded

import akka.event.Logging
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, ResponseEntity, StatusCodes}
import akka.http.scaladsl.server.{Directive0, Directive1, Directives, Route}
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.Materializer
import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Encoder, Json}
import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.requestresponse.DefaultResponseEncoder
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.RequestResponseEngine.RequestResponseResultType
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseGetSource, RequestResponsePostSource}
import pl.touk.nussknacker.engine.requestresponse.metrics.InvocationMetrics

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ProcessRoute(processInterpreters: scala.collection.Map[String, InterpreterType]) extends Directives with LazyLogging {

  protected def logDirective(scenarioName: String): Directive0 = DebuggingDirectives.logRequestResult((s"request-response-$scenarioName", Logging.DebugLevel))

  def route(implicit ec: ExecutionContext, mat: Materializer): Route =
    path(Segment) { processPath =>
      logDirective(processPath) {
        processInterpreters.get(processPath) match {
          case None =>
            complete {
              HttpResponse(status = StatusCodes.NotFound)
            }
          case Some(processInterpreter) => new RequestResponseAkkaHttpHandler(processInterpreter).invoke {
            case Invalid(errors) => complete {
              logErrors(processPath, errors)
              HttpResponse(status = StatusCodes.InternalServerError, entity = toEntity(errors.toList.map(info => EspError(info.nodeComponentInfo.map(_.nodeId), Option(info.throwable.getMessage)))))
            }
            case Valid(results) => complete {
              HttpResponse(status = StatusCodes.OK, entity = toEntity(results))
            }
          }
        }
      }
      //TODO place openApi endpoint
    } ~ pathEndOrSingleSlash {
      //healthcheck endpoint
      get {
        complete {
          HttpResponse(status = StatusCodes.OK)
        }
      }
    }

  private def toEntity[T: Encoder](value: T): ResponseEntity = HttpEntity(contentType = `application/json`, string = value.asJson.noSpacesSortKeys)

  private def logErrors(processPath: String, errors: NonEmptyList[NuExceptionInfo[_ <: Throwable]]): Unit = {
    logger.warn(s"Failed to invoke: $processPath with errors: ${errors.map(_.throwable.getMessage)}")
    errors.toList.foreach { error =>
      logger.info(s"Invocation failed $processPath, error in ${error.nodeComponentInfo.map(_.nodeId)}: ${error.throwable.getMessage}", error.throwable)
    }
  }

  @JsonCodec case class EspError(nodeId: Option[String], message: Option[String])

}

//this class handles parsing, displaying and invoking interpreter. This is the only place we interact with model, hence
//only here we care about context classloaders
class RequestResponseAkkaHttpHandler(requestResponseInterpreter: InterpreterType) extends Directives {

  val invoke: Directive1[RequestResponseResultType[Json]] =
    extractExecutionContext.flatMap { implicit ec =>
      extractInput
        .map(invokeInterpreter)
        .flatMap(onSuccess(_))
    }
  private val source = requestResponseInterpreter.source
  private val encoder = source.responseEncoder.getOrElse(DefaultResponseEncoder)
  private val invocationMetrics = new InvocationMetrics(requestResponseInterpreter.context)
  private val extractInput: Directive1[Any] = source match {
    case a: RequestResponsePostSource[Any] =>
      post & entity(as[Array[Byte]]).map(a.parse)
    case a: RequestResponseGetSource[Any] =>
      get & parameterMultiMap.map(a.parse)
  }

  private def invokeInterpreter(input: Any)(implicit ec: ExecutionContext): Future[RequestResponseResultType[Json]] = invocationMetrics.measureTime {
    requestResponseInterpreter.invokeToOutput(input).map(_.andThen { data =>
      Validated.fromTry(Try(encoder.toJsonResponse(input, data))).leftMap(ex => NonEmptyList.one(NuExceptionInfo(None, ex, Context(""))))
    })
  }

}