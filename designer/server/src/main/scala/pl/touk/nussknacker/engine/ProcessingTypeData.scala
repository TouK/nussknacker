package pl.touk.nussknacker.engine

import _root_.sttp.client3.SttpBackend
import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.{
  ComponentStaticDefinition,
  DynamicComponentToStaticDefinitionTransformer
}
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics

import scala.concurrent.{ExecutionContext, Future}

final case class ProcessingTypeData private (
    processingType: ProcessingType,
    deploymentManager: DeploymentManager,
    modelData: ModelData,
    // We hold this map as a cache - it is a quite costly operation to compute this Map (it invokes external services)
    staticDefinitionForDynamicComponents: Map[ComponentInfo, ComponentStaticDefinition],
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
      processingType: ProcessingType,
      deploymentManagerProvider: DeploymentManagerProvider,
      processingTypeConfig: ProcessingTypeConfig,
      additionalUIConfigProvider: AdditionalUIConfigProvider
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): ProcessingTypeData = {
    val managerConfig                 = processingTypeConfig.deploymentConfig
    val additionalConfigsFromProvider = additionalUIConfigProvider.getAllForProcessingType(processingType)

    createProcessingTypeData(
      processingType,
      deploymentManagerProvider,
      createModelData(processingType, processingTypeConfig, additionalConfigsFromProvider),
      managerConfig,
      processingTypeConfig.category
    )
  }

  private def createModelData(
      processingType: ProcessingType,
      processingTypeConfig: ProcessingTypeConfig,
      additionalConfigsFromProvider: Map[ComponentId, ComponentAdditionalConfig]
  ) = {
    ModelData(processingTypeConfig, additionalConfigsFromProvider, ComponentId.default(processingType, _))
  }

  def createProcessingTypeData(
      processingType: ProcessingType,
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
    createProcessingTypeData(processingType, deploymentManagerProvider, manager, modelData, managerConfig, category)
  }

  def createProcessingTypeData(
      processingType: ProcessingType,
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
    val staticDefinitionForDynamicComponents =
      DynamicComponentToStaticDefinitionTransformer.collectStaticDefinitionsForDynamicComponents(
        modelData,
        metaDataInitializer.create(_, Map.empty)
      )

    ProcessingTypeData(
      processingType,
      manager,
      modelData,
      staticDefinitionForDynamicComponents,
      metaDataInitializer,
      scenarioProperties,
      deploymentManagerProvider.additionalValidators(managerConfig),
      ProcessingTypeUsageStatistics(managerConfig),
      category
    )
  }

}
