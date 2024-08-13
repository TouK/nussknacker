package pl.touk.nussknacker.ui.process.processingtype

import com.typesafe.config.Config
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.cache.ScenarioStateCachingConfig
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.DynamicComponentStaticDefinitionDeterminer
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters

import scala.util.control.NonFatal

final case class ProcessingTypeData private (
    name: ProcessingType,
    designerModelData: DesignerModelData,
    deploymentData: DeploymentData,
    category: String,
) {

  // TODO: We should allow to have >1 processing mode configured inside one model and return a List here
  //       But for now, we throw an error when there is >1 processing mode and use have to split such a configuration
  //       into multiple processing types with classpaths without colliding components
  def scenarioParameters: ScenarioParametersWithEngineSetupErrors =
    ScenarioParametersWithEngineSetupErrors(
      ScenarioParameters(
        designerModelData.processingMode,
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
      name: ProcessingType,
      modelData: ModelData,
      deploymentManagerProvider: DeploymentManagerProvider,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      engineSetupName: EngineSetupName,
      deploymentConfig: Config,
      category: String
  ): ProcessingTypeData = {
    try {
      val metaDataInitializer = deploymentManagerProvider.metaDataInitializer(deploymentConfig)
      val deploymentData =
        createDeploymentData(
          deploymentManagerProvider,
          deploymentManagerDependencies,
          engineSetupName,
          modelData,
          deploymentConfig,
          metaDataInitializer
        )

      val designerModelData = createDesignerModelData(modelData, metaDataInitializer, name)
      ProcessingTypeData(
        name,
        designerModelData,
        deploymentData,
        category
      )
    } catch {
      case NonFatal(ex) =>
        throw new IllegalArgumentException(
          s"Error during creation of processing type data for processing type [$name]",
          ex
        )
    }
  }

  private def createDeploymentData(
      deploymentManagerProvider: DeploymentManagerProvider,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      engineSetupName: EngineSetupName,
      modelData: ModelData,
      deploymentConfig: Config,
      metaDataInitializer: MetaDataInitializer
  ) = {
    val scenarioStateCacheTTL = ScenarioStateCachingConfig.extractScenarioStateCacheTTL(deploymentConfig)

    val validDeploymentManager =
      deploymentManagerProvider.createDeploymentManager(
        modelData,
        deploymentManagerDependencies,
        deploymentConfig,
        scenarioStateCacheTTL
      )
    val scenarioProperties =
      deploymentManagerProvider.scenarioPropertiesConfig(deploymentConfig) ++ modelData.modelConfig
        .getOrElse[Map[ProcessingType, ScenarioPropertyConfig]]("scenarioPropertiesConfig", Map.empty)
    val fragmentProperties = modelData.modelConfig
      .getOrElse[Map[ProcessingType, ScenarioPropertyConfig]]("fragmentPropertiesConfig", Map.empty)

    DeploymentData(
      validDeploymentManager,
      metaDataInitializer,
      scenarioProperties,
      fragmentProperties,
      deploymentManagerProvider.additionalValidators(deploymentConfig),
      DeploymentManagerType(deploymentManagerProvider.name),
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
