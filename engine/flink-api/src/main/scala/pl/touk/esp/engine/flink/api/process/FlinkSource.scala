package pl.touk.esp.engine.flink.api.process

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.esp.engine.api.MethodToInvoke
import pl.touk.esp.engine.api.process.{Source, SourceFactory}
import pl.touk.esp.engine.api.test.TestDataParser

trait FlinkSource[T] extends Source[T] {

  def typeInformation: TypeInformation[T]

  def toFlinkSource: SourceFunction[T]

  def timestampAssigner : Option[TimestampAssigner[T]]

}


//Serializable to make Flink happy, e.g. kafkaMocks.MockSourceFactory won't work properly otherwise
abstract class FlinkSourceFactory[T: TypeInformation] extends SourceFactory[T] with Serializable {
  def clazz = typeInformation.getTypeClass

  def typeInformation = implicitly[TypeInformation[T]]
}

object FlinkSourceFactory {

  def noParam[T: TypeInformation](source: FlinkSource[T], testDataParser: Option[TestDataParser[T]] = None): FlinkSourceFactory[T] =
    new NoParamSourceFactory[T](source, testDataParser)

  class NoParamSourceFactory[T: TypeInformation](val source: FlinkSource[T], val testDataParser: Option[TestDataParser[T]]) extends FlinkSourceFactory[T] {
    @MethodToInvoke
    def create(): Source[T] = source
  }

}
