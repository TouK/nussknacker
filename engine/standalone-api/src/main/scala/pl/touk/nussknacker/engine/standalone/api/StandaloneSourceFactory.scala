package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}


trait StandaloneSourceFactory[T] extends SourceFactory[T] {

  //TODO: consider moving path from properties to source??
  @MethodToInvoke
  def create(): Source[T] = {
    new Source[T] {}
  }

}

trait StandaloneGetFactory[T] extends StandaloneSourceFactory[T] {

  def parse(parameters: Map[String, List[String]]): T

}


trait StandalonePostFactory[T] extends StandaloneSourceFactory[T] {

  def parse(parameters: Array[Byte]): T

}

case class DecodingError(message: String) extends IllegalArgumentException(message)

