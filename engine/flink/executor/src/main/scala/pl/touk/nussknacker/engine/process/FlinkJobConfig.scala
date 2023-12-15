package pl.touk.nussknacker.engine.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig

case class FlinkJobConfig(checkpointConfig: Option[CheckpointConfig], rocksDB: Option[RocksDBStateBackendConfig])

object FlinkJobConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def parse(modelConfig: Config): FlinkJobConfig = {
    modelConfig.as[FlinkJobConfig]
  }

}
