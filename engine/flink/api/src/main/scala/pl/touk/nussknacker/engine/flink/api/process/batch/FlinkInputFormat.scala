package pl.touk.nussknacker.engine.flink.api.process.batch

import org.apache.flink.api.common.io.InputFormat
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}

trait FlinkInputFormat[T] extends Source[T] {

  def toFlink: InputFormat[T, _]
}

abstract class FlinkInputFormatFactory[T: TypeInformation] extends SourceFactory[T] with Serializable {

  override def clazz: Class[T] = typeInformation.getTypeClass

  def typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]
}

class NoParamInputFormatFactory[T: TypeInformation](inputFormat: FlinkInputFormat[T]) extends FlinkInputFormatFactory[T] {

  @MethodToInvoke
  def create(): Source[T] = inputFormat
}