package pl.touk.nussknacker.ui.process.processingtype

import com.typesafe.config.Config
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.cache.ScenarioStateCachingConfig
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  Components,
  DynamicComponentStaticDefinitionDeterminer
}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.periodic.PeriodicDeploymentManagerProvider
import pl.touk.nussknacker.ui.process.periodic.flink.FlinkPeriodicDeploymentHandler
import pl.touk.nussknacker.ui.process.processingtype.DesignerModelData.DynamicComponentsStaticDefinitions

import java.time.Clock
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
      periodicExecutionSupportForManager: PeriodicExecutionSupportForManager,
      deploymentManagerDependencies: DeploymentManagerDependencies,
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
          periodicExecutionSupportForManager,
          deploymentManagerDependencies,
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
      periodicExecutionSupportForManager: PeriodicExecutionSupportForManager,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      engineSetupName: EngineSetupName,
      modelData: ModelData,
      deploymentConfig: Config,
      metaDataInitializer: MetaDataInitializer,
  ) = {
    val scenarioStateCacheTTL = ScenarioStateCachingConfig.extractScenarioStateCacheTTL(deploymentConfig)

    val validDeploymentManager = for {
      deploymentManager <- deploymentManagerProvider.createDeploymentManager(
        modelData,
        deploymentManagerDependencies,
        deploymentConfig,
        scenarioStateCacheTTL
      )
      decoratedDeploymentManager = periodicExecutionSupportForManager match {
        case PeriodicExecutionSupportForManager.Available(dbRef, clock) =>
          val deploymentManagerTypesWithPeriodicSupport = Map(
            "flinkStreaming" ->
              (() => FlinkPeriodicDeploymentHandler.create(modelData, deploymentManagerDependencies, deploymentConfig))
          )
          val handlerProvider = deploymentManagerTypesWithPeriodicSupport.getOrElse(
            deploymentManagerProvider.name,
            throw new IllegalStateException(
              s"Nussknacker does not support periodic execution for ${deploymentManagerProvider.name}"
            )
          )
          new PeriodicDeploymentManagerProvider(
            underlying = deploymentManager,
            handler = handlerProvider(),
            deploymentConfig = deploymentConfig,
            dependencies = deploymentManagerDependencies,
            dbRef = dbRef,
            clock = clock,
          ).provide()
        case PeriodicExecutionSupportForManager.NotAvailable =>
          deploymentManager
      }
    } yield decoratedDeploymentManager

    val additionalScenarioProperties = periodicExecutionSupportForManager match {
      case PeriodicExecutionSupportForManager.Available(_, _) =>
        PeriodicDeploymentManagerProvider.additionalScenarioProperties(deploymentConfig)
      case PeriodicExecutionSupportForManager.NotAvailable =>
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

  sealed trait PeriodicExecutionSupportForManager

  object PeriodicExecutionSupportForManager {
    final case class Available(dbRef: DbRef, clock: Clock) extends PeriodicExecutionSupportForManager
    case object NotAvailable                               extends PeriodicExecutionSupportForManager
  }

}
