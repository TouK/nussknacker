package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}

//TODO: this is a bit clumsy, we should think about:
//- responseEncoder in sourceFactory
//- passing path in standalone parameters and not through source
trait StandaloneSourceFactory[T] extends SourceFactory[T]

trait StandaloneGetSource[T] extends StandaloneSource[T] {

  def parse(parameters: Map[String, List[String]]): T

}

trait StandalonePostSource[T] extends StandaloneSource[T] {

  def parse(parameters: Array[Byte]): T

}

trait StandaloneSource[T] extends Source[T] {

  def responseEncoder: Option[ResponseEncoder[T]] = None

}
