package pl.touk.nussknacker.engine.lite.requestresponse

import akka.event.Logging
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.server.{Directive0, Directive1, Directives, Route}
import akka.stream.Materializer
import cats.data.Validated.{Invalid, Valid}
import cats.data.{EitherT, NonEmptyList, Validated}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FatalUnknownError
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.{Context, MetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.requestresponse.DefaultResponseEncoder
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseGetSource, RequestResponsePostSource}
import pl.touk.nussknacker.engine.requestresponse.metrics.InvocationMetrics

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ScenarioRoute(singleScenarioRoutes: scala.collection.Map[String, SingleScenarioRoute]) extends Directives with LazyLogging {

  protected def logDirective(scenarioName: String): Directive0 = DebuggingDirectives.logRequestResult((s"request-response-$scenarioName", Logging.DebugLevel))

  def route(implicit ec: ExecutionContext, mat: Materializer): Route =
    path("scenario" / Segment) { scenarioPath =>
      handle(scenarioPath)(_.invocationRoute)
    } ~
      path("scenario" / Segment / "definition") { scenarioPath =>
        handle(scenarioPath)(_.definitionRoute)
      }

  private def handle(scenarioPath: String)(callback: SingleScenarioRoute => Route): Route = singleScenarioRoutes.get(scenarioPath) match {
    case None => complete {
      HttpResponse(status = StatusCodes.NotFound)
    }
    case Some(processInterpreter) => callback(processInterpreter)
  }

}

object ScenarioRoute {

  def pathForScenario(metaData: MetaData): Validated[NonEmptyList[FatalUnknownError], String] = metaData.typeSpecificData match {
    case RequestResponseMetaData(slug) => Valid(slug.getOrElse(metaData.id))
    case _ => Invalid(NonEmptyList.of(FatalUnknownError(s"Wrong scenario metadata: ${metaData.typeSpecificData}")))
  }

}

//this class handles parsing, displaying and invoking interpreter. This is the only place we interact with model, hence
//only here we care about context classloaders
class RequestResponseAkkaHttpHandler(val requestResponseInterpreter: InterpreterType) extends Directives {

  val invoke: Directive1[Either[NonEmptyList[ErrorType], Json]] =
    extractExecutionContext.flatMap { implicit ec =>
      extractInput.map(invokeWithEncoding).flatMap(onSuccess(_))
    }

  private val source = requestResponseInterpreter.source
  private val encoder = source.responseEncoder.getOrElse(DefaultResponseEncoder)
  private val invocationMetrics = new InvocationMetrics(requestResponseInterpreter.context)

  private val extractInput: Directive1[() => Any] = source match {
    case a: RequestResponsePostSource[Any] =>
      post & entity(as[Array[Byte]]).map(k => () => a.parse(k))
    case a: RequestResponseGetSource[Any] =>
      get & parameterMultiMap.map(k => () => a.parse(k))
  }

  //TODO: refactor responseEncoder/source API
  private def invokeWithEncoding(inputParse: () => Any)(implicit ec: ExecutionContext) = {
    (for {
      input <- tryInvoke(inputParse())
      rawResult <- EitherT(invokeInterpreter(input))
      encoderResult <- tryInvoke(encoder.toJsonResponse(input, rawResult))
    } yield encoderResult).value
  }

  private def invokeInterpreter(input: Any)(implicit ec: ExecutionContext) = invocationMetrics.measureTime {
    requestResponseInterpreter.invokeToOutput(input)
  }.map(_.toEither)

  private def tryInvoke[T](value: =>T)(implicit ec: ExecutionContext): EitherT[Future, NonEmptyList[ErrorType], T] =
    EitherT.fromEither[Future](Try(value).toEither.left.map(ex => NonEmptyList.one(
      NuExceptionInfo(Some(NodeComponentInfo(requestResponseInterpreter.sourceId.value, None)), ex, Context("")))))

}