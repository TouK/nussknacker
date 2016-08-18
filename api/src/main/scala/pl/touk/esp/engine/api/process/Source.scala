package pl.touk.esp.engine.api.process

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.esp.engine.api.MetaData

trait Source[T] {

  def typeInformation: TypeInformation[T]

  def toFlinkSource: SourceFunction[T]

  def timeExtractionFunction : Option[T => Long]

}

trait SourceFactory[T] {
}

object SourceFactory {

  def noParam[T](source: Source[T]): SourceFactory[T] =
    new NoParamSourceFactory[T](source)

  class NoParamSourceFactory[T](source: Source[T]) extends SourceFactory[T] {
    def create(): Source[T] = source
  }

}