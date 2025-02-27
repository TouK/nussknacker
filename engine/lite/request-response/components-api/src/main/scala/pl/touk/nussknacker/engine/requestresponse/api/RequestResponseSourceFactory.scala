package pl.touk.nussknacker.engine.requestresponse.api

import pl.touk.nussknacker.engine.api.{Context, VariableConstants}
import pl.touk.nussknacker.engine.api.component.RequestResponseComponent
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializingFunction, SourceFactory}
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource
import pl.touk.nussknacker.engine.requestresponse.api.openapi.OpenApiSourceDefinition

import scala.language.higherKinds

//TODO: this is a bit clumsy, we should think about:
//- responseEncoder in sourceFactory
//- passing path in request-response parameters and not through source
trait RequestResponseSourceFactory extends SourceFactory with RequestResponseComponent

trait RequestResponseGetSource[T] extends RequestResponseSource[T] {

  def parse(parameters: Map[String, List[String]]): T

}

trait RequestResponsePostSource[T] extends RequestResponseSource[T] {

  def parse(parameters: Array[Byte]): T

}

trait RequestResponseSource[T] extends BaseLiteSource[Any] {

  def responseEncoder: Option[ResponseEncoder[T]] = None

  def openApiDefinition: Option[OpenApiSourceDefinition] = None

  override def transform(record: Any): Context = {
    new BasicContextInitializingFunction[Any](contextIdGenerator, VariableConstants.InputVariableName)(record)
  }

}
