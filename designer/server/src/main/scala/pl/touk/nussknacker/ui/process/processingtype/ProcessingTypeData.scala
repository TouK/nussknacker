package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.compile.EngineNodeDependencies
import pl.touk.nussknacker.engine.definition.component.{ComponentDefinitionWithImplementation, Components, DynamicComponentStaticDefinitionDeterminer}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.ui.process.processingtype.DesignerModelData.DynamicComponentsStaticDefinitions

final class ProcessingTypeData private (
    val processingType: ProcessingType,
    val designerModelData: DesignerModelData,
    // TODO: We should replace all usages of this method with access to DeploymentData which is created separately from model
    //       to fully split deployment managers from model
    val deploymentData: DeploymentData,
    val category: String,
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

}

object ProcessingTypeData {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  def createProcessingTypeData(
      processingType: ProcessingType,
      modelData: ModelData,
      deploymentData: DeploymentData,
      category: String,
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): ProcessingTypeData = {
    val designerModelData =
      createDesignerModelData(
        modelData,
        deploymentData.metaDataInitializer,
        deploymentData.deploymentScenarioPropertiesConfig,
        processingType,
        componentDefinitionExtractionMode
      )
    new ProcessingTypeData(
      processingType,
      designerModelData,
      deploymentData,
      category
    )
  }

  private def createDesignerModelData(
      modelData: ModelData,
      metaDataInitializer: MetaDataInitializer,
      deploymentScenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
      processingType: ProcessingType,
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ) = {
    // TODO: consider using ParameterName for property names instead of String (for scenario and fragment properties)
    val scenarioProperties = deploymentScenarioPropertiesConfig ++ modelData.modelConfig
      .getOrElse[Map[String, ScenarioPropertyConfig]](
        "scenarioPropertiesConfig",
        Map.empty
      )
    val fragmentProperties = modelData.modelConfig
      .getOrElse[Map[String, ScenarioPropertyConfig]]("fragmentPropertiesConfig", Map.empty)

    val staticDefinitionForDynamicComponents =
      createDynamicComponentsStaticDefinitions(modelData, metaDataInitializer, componentDefinitionExtractionMode)

    val singleProcessingMode =
      ScenarioParametersDeterminer.determineProcessingMode(
        modelData.modelDefinition.components.components,
        processingType
      )
    new DesignerModelData(
      modelData,
      scenarioProperties,
      fragmentProperties,
      staticDefinitionForDynamicComponents,
      singleProcessingMode
    )
  }

  private def createDynamicComponentsStaticDefinitions(
      modelData: ModelData,
      metaDataInitializer: MetaDataInitializer,
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): DynamicComponentsStaticDefinitions = {
    // We assume that this information is not important for determining initial parameters of dynamic nodes, so we pass fake values
    val scenarioName              = ProcessName("fakeScenarioName")
    val metaData                  = metaDataInitializer.create(scenarioName, Map.empty)
    val jobData = JobData(metaData, ProcessVersion.empty.copy(processName = scenarioName))
    // FIXME abr
    val jobRuntimeData = new JobRuntimeData(jobData, EngineNodeDependencies.empty)

    def createStaticDefinitions(extractComponents: Components => List[ComponentDefinitionWithImplementation]) = {
      DynamicComponentStaticDefinitionDeterminer.collectStaticDefinitionsForDynamicComponents(
        modelData,
        jobRuntimeData,
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

}
