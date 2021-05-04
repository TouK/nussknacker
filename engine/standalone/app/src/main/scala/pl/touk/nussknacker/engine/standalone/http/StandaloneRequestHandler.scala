package pl.touk.nussknacker.engine.standalone.http

import akka.http.scaladsl.server.{Directive1, Directives}
import cats.data.EitherT
import cats.instances.future._
import io.circe.Json
import pl.touk.nussknacker.engine.standalone.{DefaultResponseEncoder, StandaloneProcessInterpreter}
import pl.touk.nussknacker.engine.standalone.api.types.GenericResultType
import pl.touk.nussknacker.engine.standalone.api.{StandaloneGetSource, StandalonePostSource}

import scala.concurrent.{ExecutionContext, Future}


//this class handles parsing, displaying and invoking interpreter. This is the only place we interact with model, hence
//only here we care about context classloaders
class StandaloneRequestHandler(standaloneProcessInterpreter: StandaloneProcessInterpreter) extends Directives  {

  private val source = standaloneProcessInterpreter.source

  private val encoder = source.responseEncoder.getOrElse(DefaultResponseEncoder)

  private val extractInput: Directive1[Any] = source match {
    case a: StandalonePostSource[Any] =>
      post & entity(as[Array[Byte]]).map(a.parse)
    case a: StandaloneGetSource[Any] =>
      get & parameterMultiMap.map(a.parse)
  }

  val invoke: Directive1[GenericResultType[Json]] =
    extractExecutionContext.flatMap { implicit ec =>
      extractInput
        .map(invokeInterpreter)
        .flatMap(onSuccess(_))
    }

  private def invokeInterpreter(input: Any)(implicit ec: ExecutionContext): Future[GenericResultType[Json]] = (for {
    invocationResult <- EitherT(standaloneProcessInterpreter.invoke(input))
    encodedResult <- EitherT.fromEither(encoder.toJsonResponse(input, invocationResult))
  } yield encodedResult).value

}
