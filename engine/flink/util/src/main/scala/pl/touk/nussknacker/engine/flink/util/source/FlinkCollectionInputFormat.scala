package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.io.InputFormat
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.io.CollectionInputFormat
import pl.touk.nussknacker.engine.flink.api.process.batch.{FlinkInputFormat, FlinkInputFormatFactory}

class FlinkCollectionInputFormat[T: TypeInformation](config: ExecutionConfig,
                                                     list: List[T]) extends FlinkInputFormat[T] {
  override def toFlink: InputFormat[T, _] = {
    import scala.collection.JavaConverters._

    new CollectionInputFormat(list.asJava, implicitly[TypeInformation[T]].createSerializer(config))
  }
}
