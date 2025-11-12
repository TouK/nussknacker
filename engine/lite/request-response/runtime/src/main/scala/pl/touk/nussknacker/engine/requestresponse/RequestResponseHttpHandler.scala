package pl.touk.nussknacker.engine.requestresponse

import cats.Monad
import cats.data.{EitherT, NonEmptyList}
import cats.implicits.toFunctorOps
import io.circe.Json
import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest}
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.requestresponse.api.{
  RequestResponseGetSource,
  RequestResponsePostSource,
  ResponseEncoder
}
import pl.touk.nussknacker.engine.requestresponse.api.Request
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.jdk.CollectionConverters._
import scala.language.higherKinds
import scala.util.Try

//this class handles parsing, displaying and invoking interpreter. This is the only place we interact with model, hence
//only here we care about context classloaders
class RequestResponseHttpHandler[Effect[_]: Monad](
    val requestResponseInterpreter: RequestResponseInterpreter.RequestResponseScenarioInterpreter[Effect]
) {

  // TODO: refactor responseEncoder/source API
  def invoke(request: HttpRequest, entity: Array[Byte]): Effect[Either[NonEmptyList[ErrorType], Json]] = {
    for {
      parsedRequest <- tryInvoke(tryToParseRequest(request, entity))
      rawResult     <- EitherT(invokeInterpreter(parsedRequest))
      encoderResult <- tryInvoke(encoder.toJsonResponse(parsedRequest, rawResult))
    } yield encoderResult
  }.value

  private def tryToParseRequest(request: HttpRequest, entity: Array[Byte]): Request[Any] = {
    val headers = request.headers.map(h => h.name() -> h.value()).toMap
    (source, request.method) match {
      case (source: RequestResponsePostSource[Any], HttpMethods.POST) =>
        source.parse(entity, headers)
      case (source: RequestResponseGetSource[Any], HttpMethods.GET) =>
        val paramsMultiMap = request.getUri().query().toMultiMap.asScala.toMap.mapValuesNow(_.asScala.toList)
        source.parse(paramsMultiMap, headers)
      case (_, method) =>
        throw new IllegalArgumentException(s"Method $method is not handled")
    }
  }

  private val source                        = requestResponseInterpreter.source
  private val encoder: ResponseEncoder[Any] = source.responseEncoder.getOrElse(DefaultResponseEncoder)

  private def invokeInterpreter(input: Request[Any]) = {
    requestResponseInterpreter.invokeToOutput(input).map(_.toEither)
  }

  private def tryInvoke[T](value: => T): EitherT[Effect, NonEmptyList[ErrorType], T] =
    EitherT.fromEither[Effect](
      Try(value).toEither.left.map(ex =>
        NonEmptyList.one(
          NuExceptionInfo(
            Some(NodeComponentInfo(NodeId(requestResponseInterpreter.sourceId.value), None)),
            ex,
            Context.dummy,
          )
        )
      )
    )

}
