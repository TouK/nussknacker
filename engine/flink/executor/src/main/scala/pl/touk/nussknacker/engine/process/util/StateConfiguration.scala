package pl.touk.nussknacker.engine.process.util

import org.apache.flink.configuration.{Configuration, StateBackendOptions}
import org.apache.flink.state.rocksdb.PredefinedOptions.SPINNING_DISK_OPTIMIZED
import org.apache.flink.state.rocksdb.RocksDBOptions

object StateConfiguration {

  def prepareRocksDBStateBackendFlinkConfiguration(config: RocksDBStateBackendConfig): Configuration = {
    val configuration = new Configuration()
    configuration.set(StateBackendOptions.STATE_BACKEND, "rocksdb")
    configuration.set(RocksDBOptions.PREDEFINED_OPTIONS, SPINNING_DISK_OPTIMIZED.name)
    config.dbStoragePath.foreach { path =>
      configuration.set(RocksDBOptions.LOCAL_DIRECTORIES, path)
    }
    configuration
  }

  case class RocksDBStateBackendConfig(
      enable: Boolean,
      dbStoragePath: Option[String]
  )

}
