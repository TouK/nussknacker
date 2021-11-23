package pl.touk.nussknacker.engine.requestresponse.api

import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition

//TODO: this is a bit clumsy, we should think about:
//- responseEncoder in sourceFactory
//- passing path in standalone parameters and not through source
trait StandaloneSourceFactory extends SourceFactory

trait StandaloneGetSource[T] extends StandaloneSource[T] {

  def parse(parameters: Map[String, List[String]]): T

}

trait StandalonePostSource[T] extends StandaloneSource[T] {

  def parse(parameters: Array[Byte]): T

}

trait StandaloneSource[T] extends Source {

  def responseEncoder: Option[ResponseEncoder[T]] = None

  def openApiDefinition: Option[OpenApiSourceDefinition] = None

}
