package pl.touk.nussknacker.engine.requestresponse.http

import akka.http.scaladsl.server.{Directive1, Directives}
import cats.data.{NonEmptyList, Validated}
import io.circe.Json
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.requestresponse.StandaloneScenarioEngine.StandaloneResultType
import pl.touk.nussknacker.engine.requestresponse.api.{StandaloneGetSource, StandalonePostSource}
import pl.touk.nussknacker.engine.requestresponse.{DefaultResponseEncoder, StandaloneScenarioEngine}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


//this class handles parsing, displaying and invoking interpreter. This is the only place we interact with model, hence
//only here we care about context classloaders
class StandaloneRequestHandler(standaloneProcessInterpreter: StandaloneScenarioEngine.StandaloneScenarioInterpreter) extends Directives  {

  private val source = standaloneProcessInterpreter.source

  private val encoder = source.responseEncoder.getOrElse(DefaultResponseEncoder)

  private val extractInput: Directive1[Any] = source match {
    case a: StandalonePostSource[Any] =>
      post & entity(as[Array[Byte]]).map(a.parse)
    case a: StandaloneGetSource[Any] =>
      get & parameterMultiMap.map(a.parse)
  }

  val invoke: Directive1[StandaloneResultType[Json]] =
    extractExecutionContext.flatMap { implicit ec =>
      extractInput
        .map(invokeInterpreter)
        .flatMap(onSuccess(_))
    }

  private def invokeInterpreter(input: Any)(implicit ec: ExecutionContext): Future[StandaloneScenarioEngine.StandaloneResultType[Json]] =
    standaloneProcessInterpreter.invokeToOutput(input).map(_.andThen { data =>
      Validated.fromTry(Try(encoder.toJsonResponse(input, data))).leftMap(ex => NonEmptyList.one(EspExceptionInfo(None, ex, Context(""))))
    })

}
