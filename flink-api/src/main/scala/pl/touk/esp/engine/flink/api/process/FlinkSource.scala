package pl.touk.esp.engine.flink.api.process

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.esp.engine.api.process.{Source, SourceFactory}

trait FlinkSource[T] extends Source[T] {

  def typeInformation: TypeInformation[T]

  def toFlinkSource: SourceFunction[T]

  def timestampAssigner : Option[TimestampAssigner[T]]

}


//bez `extends Serializable` serializacja np. kafkaMocks.MockSourceFactory nie dziala...
abstract class FlinkSourceFactory[T: TypeInformation] extends SourceFactory[T] with Serializable {
  def clazz = typeInformation.getTypeClass

  def typeInformation = implicitly[TypeInformation[T]]
}

object FlinkSourceFactory {

  def noParam[T: TypeInformation](source: FlinkSource[T], testDataParser: Option[String => T] = None): FlinkSourceFactory[T] =
    new NoParamSourceFactory[T](source, testDataParser)

  class NoParamSourceFactory[T: TypeInformation](val source: FlinkSource[T], val testDataParser: Option[String => T]) extends FlinkSourceFactory[T] {
    def create(): Source[T] = source
  }

}
