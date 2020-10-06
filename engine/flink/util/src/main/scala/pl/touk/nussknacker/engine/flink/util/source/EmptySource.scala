package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSource
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

case class EmptySource[T:TypeInformation](returnType: TypingResult) extends BasicFlinkSource[T] with ReturningType {

  override def flinkSourceFunction: SourceFunction[T] = new EmptySourceFunction[T]

  override val typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

  override def timestampAssigner: Option[TimestampWatermarkHandler[T]] = None

}

class EmptySourceFunction[T] extends SourceFunction[T] {

  override def cancel(): Unit = {}

  override def run(ctx: SourceFunction.SourceContext[T]): Unit = {}

}