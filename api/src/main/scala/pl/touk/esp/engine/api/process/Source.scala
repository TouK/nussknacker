package pl.touk.esp.engine.api.process

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction

trait Source[T] {

  def typeInformation: TypeInformation[T]

  def toFlinkSource: SourceFunction[T]

  def timestampAssigner : Option[TimestampAssigner[T]]

}

//bez `extends Serializable` serializacja np. kafkaMocks.MockSourceFactory nie dziala...
abstract class SourceFactory[T: TypeInformation] extends Serializable {
  def clazz = implicitly[TypeInformation[T]].getTypeClass
}

object SourceFactory {

  def noParam[T: TypeInformation](source: Source[T]): SourceFactory[T] =
    new NoParamSourceFactory[T](source)

  class NoParamSourceFactory[T: TypeInformation](val source: Source[T]) extends SourceFactory[T] {
    def create(): Source[T] = source
  }

}