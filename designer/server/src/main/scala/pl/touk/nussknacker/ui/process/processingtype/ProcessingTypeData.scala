package pl.touk.nussknacker.ui.process.processingtype

import akka.actor.ActorSystem
import cats.implicits.catsSyntaxValidatedId
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{
  AdditionalUIConfigProvider,
  DesignerWideComponentId,
  ScenarioPropertyConfig
}
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeploymentService
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.DynamicComponentStaticDefinitionDeterminer
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, MetaDataInitializer, ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.restmodel.scenariodetails.{ScenarioParameters, ScenarioParametersWithEngineSetupErrors}
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

final case class ProcessingTypeData private (
    processingType: ProcessingType,
    designerModelData: DesignerModelData,
    deploymentData: DeploymentData,
    category: String,
    usageStatistics: ProcessingTypeUsageStatistics,
) {

  // TODO: We should allow to have >1 processing mode configured inside one model and return a List here
  //       But for now, we throw an error when there is >1 processing mode and use have to split such a configuration
  //       into multiple processing types with classpaths without colliding components
  def scenarioParameters: ScenarioParametersWithEngineSetupErrors =
    ScenarioParametersWithEngineSetupErrors(
      ScenarioParameters(
        designerModelData.singleProcessingMode,
        category,
        deploymentData.engineSetupName
      ),
      deploymentData.engineSetupErrors
    )

  def close(): Unit = {
    designerModelData.close()
    deploymentData.close()
  }

}

object ProcessingTypeData {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  def createProcessingTypeData(
      processingType: ProcessingType,
      deploymentManagerProvider: DeploymentManagerProvider,
      engineSetupName: EngineSetupName,
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
      engineSetupName,
      ModelData(
        processingTypeConfig,
        additionalConfigsFromProvider,
        DesignerWideComponentId.default(processingType, _)
      ),
      managerConfig,
      processingTypeConfig.category
    )
  }

  // It is extracted mostly for easier testing and LocalNussknackerWithSingleModel
  def createProcessingTypeData(
      processingType: ProcessingType,
      deploymentManagerProvider: DeploymentManagerProvider,
      engineSetupName: EngineSetupName,
      modelData: ModelData,
      managerConfig: Config,
      category: String
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): ProcessingTypeData = {
    val metaDataInitializer = deploymentManagerProvider.metaDataInitializer(managerConfig)
    val deploymentData =
      createDeploymentData(deploymentManagerProvider, engineSetupName, modelData, managerConfig, metaDataInitializer)

    val designerModelData = createDesignerModelData(modelData, metaDataInitializer, processingType)
    ProcessingTypeData(
      processingType,
      designerModelData,
      deploymentData,
      category,
      ProcessingTypeUsageStatistics(managerConfig),
    )
  }

  private def createDeploymentData(
      deploymentManagerProvider: DeploymentManagerProvider,
      engineSetupName: EngineSetupName,
      modelData: ModelData,
      managerConfig: Config,
      metaDataInitializer: MetaDataInitializer
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ) = {
    // FIXME (next PRs): We should catch exceptions and translate them to list of errors
    val validDeploymentManager =
      deploymentManagerProvider.createDeploymentManager(modelData, managerConfig).validNel[String]
    val scenarioProperties =
      deploymentManagerProvider.scenarioPropertiesConfig(managerConfig) ++ modelData.modelConfig
        .getOrElse[Map[ProcessingType, ScenarioPropertyConfig]]("scenarioPropertiesConfig", Map.empty)
    DeploymentData(
      validDeploymentManager,
      metaDataInitializer,
      scenarioProperties,
      deploymentManagerProvider.additionalValidators(managerConfig),
      engineSetupName
    )
  }

  private def createDesignerModelData(
      modelData: ModelData,
      metaDataInitializer: MetaDataInitializer,
      processingType: ProcessingType
  ) = {
    val staticDefinitionForDynamicComponents =
      DynamicComponentStaticDefinitionDeterminer.collectStaticDefinitionsForDynamicComponents(
        modelData,
        metaDataInitializer.create(_, Map.empty)
      )

    val singleProcessingMode =
      ScenarioParametersDeterminer.determineProcessingMode(modelData.modelDefinition.components, processingType)
    DesignerModelData(modelData, staticDefinitionForDynamicComponents, singleProcessingMode)
  }

}
