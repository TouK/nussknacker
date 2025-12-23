package pl.touk.nussknacker.engine.flink.api.serialization

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig

// FIXME abr remove it
trait SerializersRegistrar {

  def register(modelConfig: Config, executionConfig: ExecutionConfig): Unit

}
