package pl.touk.nussknacker.engine

import _root_.sttp.client3.SttpBackend
import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.definition.component.{
  ComponentStaticDefinition,
  ToStaticComponentDefinitionTransformer
}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics

import scala.concurrent.{ExecutionContext, Future}

final case class ProcessingTypeData private (
    deploymentManager: DeploymentManager,
    modelData: ModelData,
    staticModelDefinition: ModelDefinition[ComponentStaticDefinition],
    metaDataInitializer: MetaDataInitializer,
    scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
    additionalValidators: List[CustomProcessValidator],
    usageStatistics: ProcessingTypeUsageStatistics,
    category: String
) {

  def close(): Unit = {
    modelData.close()
    deploymentManager.close()
  }

}

object ProcessingTypeData {

  def createProcessingTypeData(
      deploymentManagerProvider: DeploymentManagerProvider,
      processTypeConfig: ProcessingTypeConfig
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): ProcessingTypeData = {
    val managerConfig = processTypeConfig.deploymentConfig
    createProcessingTypeData(
      deploymentManagerProvider,
      ModelData(processTypeConfig),
      managerConfig,
      processTypeConfig.category
    )
  }

  def createProcessingTypeData(
      deploymentManagerProvider: DeploymentManagerProvider,
      modelData: ModelData,
      managerConfig: Config,
      category: String
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): ProcessingTypeData = {
    val manager = deploymentManagerProvider.createDeploymentManager(modelData, managerConfig)
    createProcessingTypeData(deploymentManagerProvider, manager, modelData, managerConfig, category)
  }

  def createProcessingTypeData(
      deploymentManagerProvider: DeploymentManagerProvider,
      manager: DeploymentManager,
      modelData: ModelData,
      managerConfig: Config,
      category: String
  ): ProcessingTypeData = {
    import net.ceedubs.ficus.Ficus._
    import pl.touk.nussknacker.engine.util.config.FicusReaders._
    val scenarioProperties =
      deploymentManagerProvider.scenarioPropertiesConfig(managerConfig) ++ modelData.modelConfig
        .getOrElse[Map[String, ScenarioPropertyConfig]]("scenarioPropertiesConfig", Map.empty)

    val metaDataInitializer = deploymentManagerProvider.metaDataInitializer(managerConfig)
    val staticModelDefinition =
      ToStaticComponentDefinitionTransformer.transformModel(modelData, metaDataInitializer.create(_, Map.empty))

    ProcessingTypeData(
      manager,
      modelData,
      staticModelDefinition,
      metaDataInitializer,
      scenarioProperties,
      deploymentManagerProvider.additionalValidators(managerConfig),
      ProcessingTypeUsageStatistics(managerConfig),
      category
    )
  }

}
