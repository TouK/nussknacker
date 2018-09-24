package pl.touk.nussknacker.engine

import java.net.URL

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.TypeSpecificData
import pl.touk.nussknacker.engine.api.deployment.ProcessManager


trait ProcessManagerProvider {

  def createProcessManager(modelData: ModelData, config: Config): ProcessManager

  def name: String

  def emptyProcessMetadata(isSubprocess: Boolean): TypeSpecificData

  def supportsSignals: Boolean

  def supportsQueryableState: Boolean

}


case class ProcessingTypeData(processManager: ProcessManager,
                              modelData: ModelData,
                              emptyProcessCreate: Boolean => TypeSpecificData)

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
    ProcessingTypeData(manager, modelData, processManagerProvider.emptyProcessMetadata)
  }


}