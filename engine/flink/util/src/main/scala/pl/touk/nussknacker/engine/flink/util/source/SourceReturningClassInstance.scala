package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSource
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

case class SourceReturningClassInstance[T:TypeInformation](parameter :  TypingResult ) extends BasicFlinkSource[T] with ReturningType {

  override def flinkSourceFunction: SourceFunction[T] = new EmptySourceFunction[T]

  override def typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

  override def timestampAssigner: Option[TimestampWatermarkHandler[T]] = None

  override def returnType: TypingResult = ???
}
