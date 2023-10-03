package pl.touk.nussknacker.engine

import _root_.sttp.client3.SttpBackend
import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.ToStaticObjectDefinitionTransformer
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics

import scala.concurrent.{ExecutionContext, Future}

final case class ProcessingTypeData private (
    deploymentManager: DeploymentManager,
    modelData: ModelData,
    staticObjectsDefinition: ProcessDefinition[ObjectDefinition],
    metaDataInitializer: MetaDataInitializer,
    additionalPropertiesConfig: Map[String, AdditionalPropertyConfig],
    additionalValidators: List[CustomProcessValidator],
    usageStatistics: ProcessingTypeUsageStatistics
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
    createProcessingTypeData(deploymentManagerProvider, ModelData(processTypeConfig), managerConfig)
  }

  def createProcessingTypeData(
      deploymentManagerProvider: DeploymentManagerProvider,
      modelData: ModelData,
      managerConfig: Config
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): ProcessingTypeData = {
    val manager = deploymentManagerProvider.createDeploymentManager(modelData, managerConfig)
    createProcessingTypeData(deploymentManagerProvider, manager, modelData, managerConfig)
  }

  def createProcessingTypeData(
      deploymentManagerProvider: DeploymentManagerProvider,
      manager: DeploymentManager,
      modelData: ModelData,
      managerConfig: Config
  ): ProcessingTypeData = {
    import net.ceedubs.ficus.Ficus._
    import pl.touk.nussknacker.engine.util.config.FicusReaders._
    val additionalProperties =
      deploymentManagerProvider.additionalPropertiesConfig(managerConfig) ++ modelData.processConfig
        .getOrElse[Map[String, AdditionalPropertyConfig]]("additionalPropertiesConfig", Map.empty)

    val metaDataInitializer = deploymentManagerProvider.metaDataInitializer(managerConfig)
    val staticObjectsDefinition =
      ToStaticObjectDefinitionTransformer.transformModel(modelData, metaDataInitializer.create(_, Map.empty))

    ProcessingTypeData(
      manager,
      modelData,
      staticObjectsDefinition,
      metaDataInitializer,
      additionalProperties,
      deploymentManagerProvider.additionalValidators(managerConfig),
      ProcessingTypeUsageStatistics(managerConfig)
    )
  }
}
