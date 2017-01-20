package pl.touk.esp.engine.process

import com.typesafe.config.Config
import org.apache.flink.contrib.streaming.state.{OptionsFactory, RocksDBStateBackend}
import org.apache.flink.runtime.state.AbstractStateBackend
import org.rocksdb.{ColumnFamilyOptions, DBOptions}


object StateConfiguration {

  def prepareRocksDBStateBackend(mainConfig: Config): Option[AbstractStateBackend] = {
    import net.ceedubs.ficus.Ficus._
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._
    import net.ceedubs.ficus.readers.ValueReader

    mainConfig.getAs[RocksDBStateBackendConfig]("rocksDB").map { config =>
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

  }

  case class RocksDBStateBackendConfig(checkpointDataUri: String,
                                       dbStoragePath: Option[String],
                                       increaseParallelism: Option[Int],
                                       maxOpenFiles: Option[Int])

}
