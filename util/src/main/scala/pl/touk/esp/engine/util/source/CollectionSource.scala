package pl.touk.esp.engine.util.source

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.FromElementsFunction
import pl.touk.esp.engine.api.process.Source

import scala.collection.JavaConverters._

case class CollectionSource[T: TypeInformation](config: ExecutionConfig,
                                                list: List[T], timeExtraction: Option[(T => Long)]) extends Source[T] {
  override def toFlinkSource = new FromElementsFunction[T](
    typeInformation.createSerializer(config), list.asJava)

  override def typeInformation = implicitly[TypeInformation[T]]

  override def timeExtractionFunction = timeExtraction
}
