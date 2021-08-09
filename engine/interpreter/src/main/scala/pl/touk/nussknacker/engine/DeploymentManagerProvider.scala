package pl.touk.nussknacker.engine

import com.typesafe.config.{Config, ConfigResolveOptions}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api.{NamedServiceProvider, TypeSpecificData}
import pl.touk.nussknacker.engine.modelconfig.LoadedConfig
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import java.net.URL


trait DeploymentManagerProvider extends NamedServiceProvider {

  def createDeploymentManager(modelData: ModelData, config: Config): DeploymentManager

  def createQueryableClient(config: Config): Option[QueryableClient]

  def emptyProcessMetadata(isSubprocess: Boolean): TypeSpecificData

  def supportsSignals: Boolean
}

case class ProcessingTypeData(deploymentManager: DeploymentManager,
                              modelData: ModelData,
                              emptyProcessCreate: Boolean => TypeSpecificData,
                              queryableClient: Option[QueryableClient],
                              supportsSignals: Boolean) extends AutoCloseable {

  def close(): Unit = {
    modelData.close()
    deploymentManager.close()
    queryableClient.foreach(_.close())
  }

}

object ProcessingTypeConfig {

  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  def read(config: LoadedConfig): ProcessingTypeConfig =
    ProcessingTypeConfig(
      config.loadedConfig.getString("deploymentConfig.type"),
      config.loadedConfig.as[List[URL]]("modelConfig.classPath"),
      config.loadedConfig.getConfig("deploymentConfig"),
      // we resolve some variables defined on the root of config, but don't resolve system variables - will be resolved at execution side
      config.unresolvedConfig.map(_.getConfig("modelConfig")).resolve(ConfigResolveOptions.noSystem().setAllowUnresolved(true))
    )
}

case class ProcessingTypeConfig(engineType: String,
                                classPath: List[URL],
                                deploymentConfig: Config,
                                unresolvedModelConfig: Config) {

  def toModelData: ModelData = ModelData(unresolvedModelConfig, ModelClassLoader(classPath))

}

object ProcessingTypeData {

  type ProcessingType = String

  def createProcessingTypeData(deploymentManagerProvider: DeploymentManagerProvider, modelData: ModelData, managerConfig: Config): ProcessingTypeData = {
    val manager = deploymentManagerProvider.createDeploymentManager(modelData, managerConfig)
    val queryableClient = deploymentManagerProvider.createQueryableClient(managerConfig)
    ProcessingTypeData(
      manager,
      modelData,
      deploymentManagerProvider.emptyProcessMetadata,
      queryableClient,
      deploymentManagerProvider.supportsSignals)
  }

  def createProcessingTypeData(deploymentManagerProvider: DeploymentManagerProvider, processTypeConfig: ProcessingTypeConfig): ProcessingTypeData = {
    val modelData = processTypeConfig.toModelData
    val managerConfig = processTypeConfig.deploymentConfig
    createProcessingTypeData(deploymentManagerProvider, modelData, managerConfig)
  }
}
