package pl.touk.nussknacker.engine

import java.net.URL

import com.typesafe.config.Config
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
                              supportsSignals: Boolean)

case class ProcessingTypeConfig(engineType: String,
                                classPath: List[URL],
                                engineConfig: Config,
                                modelConfig: Config) {

  def toModelData = ModelData(modelConfig, classPath)

}

object ProcessingTypeData {

  type ProcessingType = String

  def createProcessManager(processManagerProvider: ProcessManagerProvider, processTypeConfig: ProcessingTypeConfig): ProcessingTypeData = {
    val ProcessingTypeConfig(_, classPathConfig, managerConfig, processConfig) = processTypeConfig
    val modelData = ModelData(processConfig, classPathConfig)
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