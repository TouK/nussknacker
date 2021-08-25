package pl.touk.nussknacker.engine.process.util

import org.apache.flink.contrib.streaming.state.{EmbeddedRocksDBStateBackend, PredefinedOptions, RocksDBStateBackend}
import org.apache.flink.runtime.state.AbstractStateBackend


object StateConfiguration {

  def prepareRocksDBStateBackend(config: RocksDBStateBackendConfig): AbstractStateBackend = {

    val rocksDBStateBackend = new EmbeddedRocksDBStateBackend(config.incrementalCheckpoints)
    config.dbStoragePath.foreach(rocksDBStateBackend.setDbStoragePath)
    rocksDBStateBackend.setPredefinedOptions(PredefinedOptions.SPINNING_DISK_OPTIMIZED)
    rocksDBStateBackend

  }

  case class RocksDBStateBackendConfig(enable: Boolean,
                                       // For purpose of Flink version before 1.13
                                       @deprecated checkpointDataUri: Option[String],
                                       dbStoragePath: Option[String],
                                       incrementalCheckpoints: Boolean = true)

}
