package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.FromElementsFunction
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSource
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

import scala.collection.JavaConverters._

case class CollectionSource[T: TypeInformation](config: ExecutionConfig,
                                                list: List[T],
                                                timestampAssigner: Option[TimestampWatermarkHandler[T]], returnType: TypingResult) extends BasicFlinkSource[T] with ReturningType {

  override def flinkSourceFunction = new FromElementsFunction[T](
    typeInformation.createSerializer(config), list.filterNot(_ == null).asJava)

  override val typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

}
