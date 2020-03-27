package pl.touk.nussknacker.engine

import java.io.Closeable
import java.net.URL

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.TypeSpecificData
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.queryablestate.QueryableClient


trait ProcessManagerProvider {
  def createProcessManager(modelData: ModelData, config: Config): ProcessManager

  def createQueryableClient(config: Config): Option[QueryableClient]

  def name: String

  def emptyProcessMetadata(isSubprocess: Boolean): TypeSpecificData

  def supportsSignals: Boolean
}

case class ProcessingTypeData(processManager: ProcessManager,
                              modelData: ModelData,
                              emptyProcessCreate: Boolean => TypeSpecificData,
                              queryableClient: Option[QueryableClient],
                              supportsSignals: Boolean) extends AutoCloseable {

  def close(): Unit = {
    modelData.close()
    processManager.close()
    queryableClient.foreach(_.close())
  }

}

object ProcessingTypeConfig {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  implicit val reader: ValueReader[ProcessingTypeConfig] = ValueReader.relative(read)

  def read(config: Config): ProcessingTypeConfig =
    ProcessingTypeConfig(
      config.getString("engineConfig.type"),
      config.as[List[URL]]("modelConfig.classPath"),
      config.getConfig("engineConfig"),
      config.getConfig("modelConfig")
    )
}

case class ProcessingTypeConfig(engineType: String,
                                classPath: List[URL],
                                engineConfig: Config,
                                modelConfig: Config) {

  def toModelData = ModelData(modelConfig, classPath)

}

object ProcessingTypeData {

  type ProcessingType = String


  def createProcessingTypeData(processManagerProvider: ProcessManagerProvider, processTypeConfig: ProcessingTypeConfig): ProcessingTypeData = {
    val modelData = processTypeConfig.toModelData
    val managerConfig = processTypeConfig.engineConfig
    val manager = processManagerProvider.createProcessManager(modelData, managerConfig)
    val queryableClient = processManagerProvider.createQueryableClient(managerConfig)
    ProcessingTypeData(
      manager,
      modelData,
      processManagerProvider.emptyProcessMetadata,
      queryableClient,
      processManagerProvider.supportsSignals)
  }
}