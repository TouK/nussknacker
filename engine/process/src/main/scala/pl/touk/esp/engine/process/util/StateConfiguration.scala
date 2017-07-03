package pl.touk.esp.engine.process.util

import org.apache.flink.contrib.streaming.state.{OptionsFactory, RocksDBStateBackend}
import org.apache.flink.runtime.state.AbstractStateBackend
import org.rocksdb.{ColumnFamilyOptions, DBOptions}


object StateConfiguration {

  def prepareRocksDBStateBackend(config: RocksDBStateBackendConfig): AbstractStateBackend = {

    val rocksDBStateBackend = new RocksDBStateBackend(config.checkpointDataUri)
    config.dbStoragePath.foreach(rocksDBStateBackend.setDbStoragePath)

    rocksDBStateBackend.setOptions(new OptionsFactory {
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
    })

    rocksDBStateBackend

  }

  case class RocksDBStateBackendConfig(checkpointDataUri: String,
                                       dbStoragePath: Option[String],
                                       increaseParallelism: Option[Int],
                                       maxOpenFiles: Option[Int])

}
