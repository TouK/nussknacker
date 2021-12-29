package pl.touk.nussknacker.engine.requestresponse.http

import akka.http.scaladsl.server.{Directive1, Directives}
import cats.data.{NonEmptyList, Validated}
import io.circe.Json
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseGetSource, RequestResponsePostSource}
import pl.touk.nussknacker.engine.requestresponse.DefaultResponseEncoder
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.requestresponse.RequestResponseEngine.RequestResponseResultType

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


//this class handles parsing, displaying and invoking interpreter. This is the only place we interact with model, hence
//only here we care about context classloaders
class RequestResponseRequestHandler(requestResponseInterpreter: InterpreterType) extends Directives  {

  private val source = requestResponseInterpreter.source

  private val encoder = source.responseEncoder.getOrElse(DefaultResponseEncoder)

  private val invocationMetrics = new InvocationMetrics(requestResponseInterpreter.context)

  private val extractInput: Directive1[Any] = source match {
    case a: RequestResponsePostSource[Any] =>
      post & entity(as[Array[Byte]]).map(a.parse)
    case a: RequestResponseGetSource[Any] =>
      get & parameterMultiMap.map(a.parse)
  }

  val invoke: Directive1[RequestResponseResultType[Json]] =
    extractExecutionContext.flatMap { implicit ec =>
      extractInput
        .map(invokeInterpreter)
        .flatMap(onSuccess(_))
    }

  private def invokeInterpreter(input: Any)(implicit ec: ExecutionContext): Future[RequestResponseResultType[Json]] = invocationMetrics.measureTime {
    requestResponseInterpreter.invokeToOutput(input).map(_.andThen { data =>
      Validated.fromTry(Try(encoder.toJsonResponse(input, data))).leftMap(ex => NonEmptyList.one(NuExceptionInfo(None, None, None, ex, Context(""))))
    })
  }

}
