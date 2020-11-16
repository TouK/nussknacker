package pl.touk.nussknacker.engine.flink.api.serialization

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig

trait SerializersRegistrar {

  def register(modelConfig: Config, executionConfig: ExecutionConfig): Unit

}

trait BaseSerializersRegistrar extends SerializersRegistrar {

  protected def serializers: List[SerializerWithSpecifiedClass[_]]

  override def register(modelConfig: Config, executionConfig: ExecutionConfig): Unit = {
    serializers.foreach(_.registerIn(executionConfig))
  }

}
