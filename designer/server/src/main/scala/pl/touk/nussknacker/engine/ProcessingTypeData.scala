package pl.touk.nussknacker.engine

import _root_.sttp.client3.SttpBackend
import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
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
    scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
    additionalValidators: List[CustomProcessValidator],
    usageStatistics: ProcessingTypeUsageStatistics,
    categoriesConfig: CategoriesConfig
) {

  def close(): Unit = {
    modelData.close()
    deploymentManager.close()
  }

}

// TODO: remove Option after fully switch to categories inside processing types configuration format -
//       see ConfigProcessCategoryService for details
case class CategoriesConfig(categories: Option[List[String]])

object CategoriesConfig {

  def apply(processTypeConfig: ProcessingTypeConfig): CategoriesConfig = new CategoriesConfig(
    processTypeConfig.categories
  )

  def apply(categories: List[String]): CategoriesConfig = new CategoriesConfig(Some(categories))

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
      CategoriesConfig(processTypeConfig)
    )
  }

  def createProcessingTypeData(
      deploymentManagerProvider: DeploymentManagerProvider,
      modelData: ModelData,
      managerConfig: Config,
      categoriesConfig: CategoriesConfig
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): ProcessingTypeData = {
    val manager = deploymentManagerProvider.createDeploymentManager(modelData, managerConfig)
    createProcessingTypeData(deploymentManagerProvider, manager, modelData, managerConfig, categoriesConfig)
  }

  def createProcessingTypeData(
      deploymentManagerProvider: DeploymentManagerProvider,
      manager: DeploymentManager,
      modelData: ModelData,
      managerConfig: Config,
      categoriesConfig: CategoriesConfig
  ): ProcessingTypeData = {
    import net.ceedubs.ficus.Ficus._
    import pl.touk.nussknacker.engine.util.config.FicusReaders._
    val scenarioProperties =
      deploymentManagerProvider.scenarioPropertiesConfig(managerConfig) ++ modelData.processConfig
        .getOrElse[Map[String, ScenarioPropertyConfig]]("scenarioPropertiesConfig", Map.empty)

    val metaDataInitializer = deploymentManagerProvider.metaDataInitializer(managerConfig)
    val staticObjectsDefinition =
      ToStaticObjectDefinitionTransformer.transformModel(modelData, metaDataInitializer.create(_, Map.empty))

    ProcessingTypeData(
      manager,
      modelData,
      staticObjectsDefinition,
      metaDataInitializer,
      scenarioProperties,
      deploymentManagerProvider.additionalValidators(managerConfig),
      ProcessingTypeUsageStatistics(managerConfig),
      categoriesConfig
    )
  }

}
