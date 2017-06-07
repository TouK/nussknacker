package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource

class EmptySource[T:TypeInformation] extends FlinkSource[T] {

  override def toFlinkSource: SourceFunction[T] = new EmptySourceFunction[T]

  override def typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

  override def timestampAssigner: Option[TimestampAssigner[T]] = None

}

class EmptySourceFunction[T] extends SourceFunction[T] {

  override def cancel(): Unit = {}

  override def run(ctx: SourceFunction.SourceContext[T]): Unit = {}

}