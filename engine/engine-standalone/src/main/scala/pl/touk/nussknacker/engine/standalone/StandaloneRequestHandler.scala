package pl.touk.nussknacker.engine.standalone

import akka.http.scaladsl.server.{Directive1, Directives}
import argonaut.ArgonautShapeless._
import argonaut.Json
import cats.data.{EitherT, NonEmptyList, Validated}
import cats.instances.future._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.compile.ProcessCompilationError
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.standalone.api.types.GenericResultType
import pl.touk.nussknacker.engine.standalone.api.{StandaloneGetFactory, StandalonePostFactory}
import pl.touk.nussknacker.engine.standalone.utils.{DefaultResponseEncoder, StandaloneContextPreparer}

import scala.concurrent.{ExecutionContext, Future}

object StandaloneRequestHandler {

  def apply(process: EspProcess, contextPreparer: StandaloneContextPreparer, modelData: ModelData)
  : Validated[NonEmptyList[ProcessCompilationError], StandaloneRequestHandler] = {
    modelData.withThisAsContextClassLoader {
      StandaloneProcessInterpreter(process, contextPreparer, modelData).map(new StandaloneRequestHandler(process.id, _, modelData))
    }
  }

}

//this class handles parsing, displaying and invoking interpreter. This is the only place we interact with model, hence
//only here we care about context classloaders
class StandaloneRequestHandler(val id: String, standaloneProcessInterpreter: StandaloneProcessInterpreter, modelData: ModelData) extends Directives  {

  private val source = standaloneProcessInterpreter.source

  private val encoder = source.responseEncoder.getOrElse(DefaultResponseEncoder)

  private val extractInput: Directive1[Any] = source match {
    case a: StandalonePostFactory[Any] =>
      post & entity(as[Array[Byte]]).map(a.parse)
    case a: StandaloneGetFactory[Any] =>
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

  def close(): Unit = modelData.withThisAsContextClassLoader {
    standaloneProcessInterpreter.close()
  }

  def open(): Unit = modelData.withThisAsContextClassLoader {
    standaloneProcessInterpreter.open()
  }

}
