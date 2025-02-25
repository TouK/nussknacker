package pl.touk.nussknacker.ui.process.processingtype

import com.typesafe.config.Config
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{NoSchedulingSupport, SchedulingSupported}
import pl.touk.nussknacker.engine.api.deployment.cache.ScenarioStateCachingConfig
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  Components,
  DynamicComponentStaticDefinitionDeterminer
}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.DeploymentManagersClassLoader
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.periodic.{PeriodicDeploymentManagerDecorator, SchedulingConfig}
import pl.touk.nussknacker.ui.process.processingtype.DesignerModelData.DynamicComponentsStaticDefinitions

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
      schedulingForProcessingType: SchedulingForProcessingType,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      engineSetupName: EngineSetupName,
      deploymentConfig: Config,
      category: String,
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): ProcessingTypeData = {
    try {
      val metaDataInitializer = deploymentManagerProvider.metaDataInitializer(deploymentConfig)
      val deploymentData =
        createDeploymentData(
          deploymentManagerProvider,
          schedulingForProcessingType,
          deploymentManagerDependencies,
          deploymentManagersClassLoader,
          engineSetupName,
          modelData,
          deploymentConfig,
          metaDataInitializer,
        )

      val designerModelData =
        createDesignerModelData(modelData, metaDataInitializer, name, componentDefinitionExtractionMode)
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
      schedulingForProcessingType: SchedulingForProcessingType,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      engineSetupName: EngineSetupName,
      modelData: ModelData,
      deploymentConfig: Config,
      metaDataInitializer: MetaDataInitializer
  ) = {
    val scenarioStateCacheTTL = ScenarioStateCachingConfig.extractScenarioStateCacheTTL(deploymentConfig)

    val validDeploymentManager = for {
      deploymentManager <-
        ThreadUtils.withThisAsContextClassLoader(deploymentManagersClassLoader) {
          deploymentManagerProvider.createDeploymentManager(
            modelData,
            deploymentManagerDependencies,
            deploymentConfig,
            scenarioStateCacheTTL
          )
        }
      decoratedDeploymentManager = schedulingForProcessingType match {
        case SchedulingForProcessingType.Available(dbRef) =>
          deploymentManager.schedulingSupport match {
            case supported: SchedulingSupported =>
              PeriodicDeploymentManagerDecorator.decorate(
                underlying = deploymentManager,
                schedulingSupported = supported,
                deploymentConfig = deploymentConfig,
                dependencies = deploymentManagerDependencies,
                dbRef = dbRef,
              )
            case NoSchedulingSupport =>
              throw new IllegalStateException(
                s"DeploymentManager ${deploymentManagerProvider.name} does not support periodic execution"
              )
          }

        case SchedulingForProcessingType.NotAvailable =>
          deploymentManager
      }
    } yield decoratedDeploymentManager

    val additionalScenarioProperties = schedulingForProcessingType match {
      case SchedulingForProcessingType.Available(_) =>
        PeriodicDeploymentManagerDecorator.additionalScenarioProperties
      case SchedulingForProcessingType.NotAvailable =>
        Map.empty[String, ScenarioPropertyConfig]
    }
    val scenarioProperties = additionalScenarioProperties ++
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
      processingType: ProcessingType,
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ) = {

    val staticDefinitionForDynamicComponents =
      createDynamicComponentsStaticDefinitions(modelData, metaDataInitializer, componentDefinitionExtractionMode)

    val singleProcessingMode =
      ScenarioParametersDeterminer.determineProcessingMode(
        modelData.modelDefinition.components.components,
        processingType
      )
    DesignerModelData(modelData, staticDefinitionForDynamicComponents, singleProcessingMode)
  }

  private def createDynamicComponentsStaticDefinitions(
      modelData: ModelData,
      metaDataInitializer: MetaDataInitializer,
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): DynamicComponentsStaticDefinitions = {
    def createStaticDefinitions(extractComponents: Components => List[ComponentDefinitionWithImplementation]) = {
      DynamicComponentStaticDefinitionDeterminer.collectStaticDefinitionsForDynamicComponents(
        modelData,
        metaDataInitializer.create(_, Map.empty),
        extractComponents
      )
    }

    DynamicComponentsStaticDefinitions(
      finalDefinitions = createStaticDefinitions(_.components),
      basicDefinitions = componentDefinitionExtractionMode match {
        case ComponentDefinitionExtractionMode.FinalDefinition => None
        case ComponentDefinitionExtractionMode.FinalAndBasicDefinitions =>
          Some(createStaticDefinitions(_.basicComponentsUnsafe))
      }
    )
  }

  sealed trait SchedulingForProcessingType

  object SchedulingForProcessingType {

    case object NotAvailable extends SchedulingForProcessingType

    final case class Available(dbRef: DbRef) extends SchedulingForProcessingType

  }

}
