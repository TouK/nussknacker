package pl.touk.nussknacker.engine.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode.ExecutionMode
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig

case class FlinkJobConfig(
    checkpointConfig: Option[CheckpointConfig],
    rocksDB: Option[RocksDBStateBackendConfig],
    executionMode: Option[ExecutionMode]
)

object FlinkJobConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  def parse(modelConfig: Config): FlinkJobConfig = {
    modelConfig.as[FlinkJobConfig]
  }

  object ExecutionMode extends Enumeration {
    type ExecutionMode = Value
    val Streaming = Value("streaming")
    val Batch     = Value("batch")
    val default   = Streaming
  }

}
