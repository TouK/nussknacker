package pl.touk.nussknacker.engine.requestresponse.api

import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition

//TODO: this is a bit clumsy, we should think about:
//- responseEncoder in sourceFactory
//- passing path in request-response parameters and not through source
trait RequestResponseSourceFactory extends SourceFactory

trait RequestResponseGetSource[T] extends RequestResponseSource[T] {

  def parse(parameters: Map[String, List[String]]): T

}

trait RequestResponsePostSource[T] extends RequestResponseSource[T] {

  def parse(parameters: Array[Byte]): T

}

trait RequestResponseSource[T] extends Source {

  def responseEncoder: Option[ResponseEncoder[T]] = None

  def openApiDefinition: Option[OpenApiSourceDefinition] = None

}
