package pl.touk.nussknacker.engine.flink.api.process.batch

import org.apache.flink.api.common.io.InputFormat
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}

import scala.reflect.ClassTag

trait FlinkBatchSource[T] extends Source[T] {

  def toFlink: InputFormat[T, _]

  def typeInformation: TypeInformation[T]

  def classTag: ClassTag[T]
}

abstract class FlinkBatchSourceFactory[T: TypeInformation] extends SourceFactory[T] with Serializable {

  override def clazz: Class[T] = typeInformation.getTypeClass

  def typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]
}

class NoParamBatchSourceFactory[T: TypeInformation](source: FlinkBatchSource[T]) extends FlinkBatchSourceFactory[T] {

  @MethodToInvoke
  def create(): Source[T] = source
}