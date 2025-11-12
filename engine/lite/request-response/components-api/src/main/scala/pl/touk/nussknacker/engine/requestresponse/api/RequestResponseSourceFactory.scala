package pl.touk.nussknacker.engine.requestresponse.api

import pl.touk.nussknacker.engine.api.TraceId
import pl.touk.nussknacker.engine.api.component.RequestResponseComponent
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, ContextVariables, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition

import scala.language.higherKinds

//TODO: this is a bit clumsy, we should think about:
//- responseEncoder in sourceFactory
//- passing path in request-response parameters and not through source
trait RequestResponseSourceFactory extends SourceFactory with RequestResponseComponent

trait RequestResponseGetSource[T] extends RequestResponseSource[T] {

  def parse(parameters: Map[String, List[String]], headers: Map[String, String]): Request[Any]

}

trait RequestResponsePostSource[T] extends RequestResponseSource[T] {

  def parse(parameters: Array[Byte], headers: Map[String, String]): Request[T]

}

object Request {
  def apply[T](value: T): Request[T] =
    Request(value, Map.empty[String, String])
}

final case class Request[T](value: T, headers: Map[String, String])

trait RequestResponseSource[T] extends BaseLiteSource[Request[T]] {

  def responseEncoder: Option[ResponseEncoder[T]] = None

  def openApiDefinition: Option[OpenApiSourceDefinition] = None

  private val contextInitializer = new BasicContextInitializer[T](Unknown)

  override def transform(record: Request[T]): ContextVariables =
    contextInitializer.convertToInitialVariables(record.value)

  override protected def extractTraceId(record: Request[T]): Option[TraceId] =
    record.headers.get(BaseLiteSource.DefaultTraceIdHeader).map(TraceId(_))

}
