package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NamedServiceProvider, ScenarioSpecificData}
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import java.net.URL


trait DeploymentManagerProvider extends NamedServiceProvider {

  def createDeploymentManager(modelData: ModelData, config: Config): DeploymentManager

  def createQueryableClient(config: Config): Option[QueryableClient]

  def typeSpecificDataInitializer: TypeSpecificDataInitializer

  def supportsSignals: Boolean
}

trait TypeSpecificDataInitializer {
  def forScenario: ScenarioSpecificData
  def forFragment: FragmentSpecificData
}

case class ProcessingTypeData(deploymentManager: DeploymentManager,
                              modelData: ModelData,
                              typeSpecificDataInitializer: TypeSpecificDataInitializer,
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

  //TODO: Replace it by VO
  type ProcessingType = String

  def createProcessingTypeData(deploymentManagerProvider: DeploymentManagerProvider, modelData: ModelData, managerConfig: Config): ProcessingTypeData = {
    val manager = deploymentManagerProvider.createDeploymentManager(modelData, managerConfig)
    val queryableClient = deploymentManagerProvider.createQueryableClient(managerConfig)
    ProcessingTypeData(
      manager,
      modelData,
      deploymentManagerProvider.typeSpecificDataInitializer,
      queryableClient,
      deploymentManagerProvider.supportsSignals)
  }

  def createProcessingTypeData(deploymentManagerProvider: DeploymentManagerProvider, processTypeConfig: ProcessingTypeConfig): ProcessingTypeData = {
    val modelData = processTypeConfig.toModelData
    val managerConfig = processTypeConfig.deploymentConfig
    createProcessingTypeData(deploymentManagerProvider, modelData, managerConfig)
  }
}
