package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.FromElementsFunction
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSource
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

import scala.jdk.CollectionConverters._

case class CollectionSource[T: TypeInformation](
    list: List[T],
    timestampAssigner: Option[TimestampWatermarkHandler[Context]],
    returnType: TypingResult
) extends BasicFlinkSource[T]
    with ReturningType {
  override def flinkSourceFunction = new FromElementsFunction[T](list.filterNot(_ == null).asJava)

  override val typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

}
