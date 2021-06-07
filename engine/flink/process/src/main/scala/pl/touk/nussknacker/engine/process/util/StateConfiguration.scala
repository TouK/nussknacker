package pl.touk.nussknacker.engine.process.util

import org.apache.flink.contrib.streaming.state.{PredefinedOptions, RocksDBStateBackend}
import org.apache.flink.runtime.state.AbstractStateBackend


object StateConfiguration {

  def prepareRocksDBStateBackend(config: RocksDBStateBackendConfig): AbstractStateBackend = {

    val rocksDBStateBackend = new RocksDBStateBackend(config.checkpointDataUri, config.incrementalCheckpoints)
    config.dbStoragePath.foreach(rocksDBStateBackend.setDbStoragePath)

    rocksDBStateBackend.setPredefinedOptions(PredefinedOptions.SPINNING_DISK_OPTIMIZED)
    //it seems that this class cannot be deserialized properly - it uses flink classloader, not the user one
    /*rocksDBStateBackend.setOptions(new OptionsFactory {
      override def createColumnOptions(columnFamilyOptions: ColumnFamilyOptions) = {
        columnFamilyOptions
      }

      override def createDBOptions(dbOptions: DBOptions) = {
        val options = dbOptions
          .setUseFsync(false)
          .setDisableDataSync(true)
        config.increaseParallelism.foreach(options.setIncreaseParallelism)
        config.maxOpenFiles.foreach(options.setMaxOpenFiles)
        options
      }
    })*/

    rocksDBStateBackend

  }

  case class RocksDBStateBackendConfig(checkpointDataUri: String,
                                       dbStoragePath: Option[String],
                                       increaseParallelism: Option[Int],
                                       maxOpenFiles: Option[Int], incrementalCheckpoints: Boolean = true)

}
