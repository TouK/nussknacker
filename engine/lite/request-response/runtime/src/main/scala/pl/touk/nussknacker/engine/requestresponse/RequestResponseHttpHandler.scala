package pl.touk.nussknacker.engine.requestresponse

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import cats.Monad
import cats.data.{EitherT, NonEmptyList}
import cats.implicits.toFunctorOps
import io.circe.Json
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseGetSource, RequestResponsePostSource}
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
      input         <- tryInvoke(tryToParse(request, entity))
      rawResult     <- EitherT(invokeInterpreter(input))
      encoderResult <- tryInvoke(encoder.toJsonResponse(input, rawResult))
    } yield encoderResult
  }.value

  private def tryToParse(request: HttpRequest, entity: Array[Byte]) = {
    (source, request.method) match {
      case (source: RequestResponsePostSource[Any], HttpMethods.POST) =>
        source.parse(entity)
      case (source: RequestResponseGetSource[Any], HttpMethods.GET) =>
        val paramsMultiMap = request.getUri().query().toMultiMap.asScala.toMap.mapValuesNow(_.asScala.toList)
        source.parse(paramsMultiMap)
      case (_, method) =>
        throw new IllegalArgumentException(s"Method $method is not handled")
    }
  }

  private val source  = requestResponseInterpreter.source
  private val encoder = source.responseEncoder.getOrElse(DefaultResponseEncoder)

  private def invokeInterpreter(input: Any) = requestResponseInterpreter.invokeToOutput(input).map(_.toEither)

  private def tryInvoke[T](value: => T): EitherT[Effect, NonEmptyList[ErrorType], T] =
    EitherT.fromEither[Effect](
      Try(value).toEither.left.map(ex =>
        NonEmptyList.one(
          NuExceptionInfo(
            Some(NodeComponentInfo(requestResponseInterpreter.sourceId.value, None)),
            ex,
            Context("")
          )
        )
      )
    )

}
