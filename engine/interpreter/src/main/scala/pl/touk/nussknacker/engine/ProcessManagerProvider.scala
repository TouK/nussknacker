package pl.touk.nussknacker.engine

import java.net.URL
import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.{NamedServiceProvider, TypeSpecificData}
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader


trait ProcessManagerProvider extends NamedServiceProvider {

  def createProcessManager(modelData: ModelData, config: Config): ProcessManager

  def createQueryableClient(config: Config): Option[QueryableClient]

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

  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  implicit val reader: ValueReader[ProcessingTypeConfig] = ValueReader.relative(read)

  def read(config: Config): ProcessingTypeConfig =
    ProcessingTypeConfig(
      config.getString("deploymentConfig.type"),
      config.as[List[URL]]("modelConfig.classPath"),
      config.getConfig("deploymentConfig"),
      config.getConfig("modelConfig")
    )
}

case class ProcessingTypeConfig(engineType: String,
                                classPath: List[URL],
                                deploymentConfig: Config,
                                modelConfig: Config) {

  def toModelData: ModelData = ModelData(modelConfig, ModelClassLoader(classPath))

}

object ProcessingTypeData {

  type ProcessingType = String

  def createProcessingTypeData(processManagerProvider: ProcessManagerProvider, modelData: ModelData, managerConfig: Config): ProcessingTypeData = {
    val manager = processManagerProvider.createProcessManager(modelData, managerConfig)
    val queryableClient = processManagerProvider.createQueryableClient(managerConfig)
    ProcessingTypeData(
      manager,
      modelData,
      processManagerProvider.emptyProcessMetadata,
      queryableClient,
      processManagerProvider.supportsSignals)
  }

  def createProcessingTypeData(processManagerProvider: ProcessManagerProvider, processTypeConfig: ProcessingTypeConfig): ProcessingTypeData = {
    val modelData = processTypeConfig.toModelData
    val managerConfig = processTypeConfig.deploymentConfig
    createProcessingTypeData(processManagerProvider, modelData, managerConfig)
  }
}
