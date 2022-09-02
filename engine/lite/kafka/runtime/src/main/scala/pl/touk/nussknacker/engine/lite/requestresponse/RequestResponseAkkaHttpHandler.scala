package pl.touk.nussknacker.engine.lite.requestresponse

import akka.http.scaladsl.server.{Directive1, Directives}
import cats.data.{EitherT, NonEmptyList}
import io.circe.Json
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.requestresponse.DefaultResponseEncoder
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseGetSource, RequestResponsePostSource}
import pl.touk.nussknacker.engine.requestresponse.metrics.InvocationMetrics

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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