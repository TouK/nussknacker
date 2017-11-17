package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}

//TODO: this is a bit clumsy, we should think about:
//- responseEncoder in sourceFactory
//- dummy Source
//- handling source in StandaloneInterpreter
//- passing path in standalone parameters and not through source
trait StandaloneSourceFactory[T] extends SourceFactory[T] {

  @MethodToInvoke
  def create(): Source[T] = {
    new Source[T] {}
  }

  def responseEncoder: Option[ResponseEncoder[T]] = None

}

trait StandaloneGetFactory[T] extends StandaloneSourceFactory[T] {

  def parse(parameters: Map[String, List[String]]): T

}


trait StandalonePostFactory[T] extends StandaloneSourceFactory[T] {

  def parse(parameters: Array[Byte]): T

}

case class DecodingError(message: String) extends IllegalArgumentException(message)

