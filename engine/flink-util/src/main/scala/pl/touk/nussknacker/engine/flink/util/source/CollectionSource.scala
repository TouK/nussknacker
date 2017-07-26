package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.FromElementsFunction
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource

import scala.collection.JavaConverters._

case class CollectionSource[T: TypeInformation](config: ExecutionConfig,
                                                list: List[T],
                                                timestampAssigner: Option[TimestampAssigner[T]]) extends FlinkSource[T] {

  override def toFlinkSource = new FromElementsFunction[T](
    typeInformation.createSerializer(config), list.asJava)

  override def typeInformation = implicitly[TypeInformation[T]]

}
