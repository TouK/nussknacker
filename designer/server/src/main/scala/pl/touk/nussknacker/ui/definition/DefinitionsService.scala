package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, FragmentSpecificData}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.component.{ComponentGroupsPreparer, EdgeTypesPreparer}
import pl.touk.nussknacker.ui.definition.DefinitionsService.{createUIParameter, createUIScenarioPropertyConfig}
import pl.touk.nussknacker.ui.definition.scenarioproperty.{FragmentPropertiesConfig, UiScenarioPropertyEditorDeterminer}
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

// This class only combines various views on definitions for the FE. It is executed for each request, when user
// enters the scenario view. The core domain logic should be done during Model definition extraction
class DefinitionsService(
    modelData: ModelData,
    scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
    deploymentManager: DeploymentManager,
    modelDefinitionEnricher: ModelDefinitionEnricher,
    scenarioPropertiesConfigFinalizer: ScenarioPropertiesConfigFinalizer,
    fragmentRepository: FragmentRepository
)(implicit ec: ExecutionContext) {

  def prepareUIDefinitions(processingType: ProcessingType, forFragment: Boolean)(
      implicit user: LoggedUser
  ): Future[UIDefinitions] = {
    fragmentRepository.fetchLatestFragments(processingType).map { fragments =>
      val enrichedModelDefinition =
        modelDefinitionEnricher
          .modelDefinitionWithBuiltInComponentsAndFragments(forFragment, fragments)
      val finalizedScenarioPropertiesConfig = scenarioPropertiesConfigFinalizer
        .finalizeScenarioProperties(scenarioPropertiesConfig)
      prepareUIDefinitions(
        enrichedModelDefinition,
        forFragment,
        finalizedScenarioPropertiesConfig
      )
    }
  }

  private def prepareUIDefinitions(
      modelDefinitionWithBuiltInComponentsAndFragments: ModelDefinition[ComponentStaticDefinition],
      forFragment: Boolean,
      finalizedScenarioPropertiesConfig: Map[String, ScenarioPropertyConfig]
  ): UIDefinitions = {
    UIDefinitions(
      componentGroups =
        ComponentGroupsPreparer.prepareComponentGroups(modelDefinitionWithBuiltInComponentsAndFragments),
      components =
        modelDefinitionWithBuiltInComponentsAndFragments.components.mapValuesNow(createUIComponentDefinition),
      classes = modelData.modelDefinitionWithClasses.classDefinitions.all.toList.map(_.clazzName),
      scenarioPropertiesConfig =
        (if (forFragment) FragmentPropertiesConfig.properties else finalizedScenarioPropertiesConfig)
          .mapValuesNow(createUIScenarioPropertyConfig),
      edgesForNodes =
        EdgeTypesPreparer.prepareEdgeTypes(definitions = modelDefinitionWithBuiltInComponentsAndFragments),
      customActions = deploymentManager.customActions.map(UICustomAction(_))
    )
  }

  private def createUIComponentDefinition(
      componentDefinition: ComponentStaticDefinition
  ): UIComponentDefinition = {
    UIComponentDefinition(
      parameters = componentDefinition.parameters.map(createUIParameter),
      returnType = componentDefinition.returnType,
      icon = componentDefinition.iconUnsafe,
      docsUrl = componentDefinition.componentConfig.docsUrl,
      outputParameters = Option(componentDefinition.componentTypeSpecificData).collect {
        case FragmentSpecificData(outputNames) => outputNames
      }
    )
  }

}

object DefinitionsService {

  def apply(
      processingTypeData: ProcessingTypeData,
      modelDefinitionEnricher: ModelDefinitionEnricher,
      scenarioPropertiesConfigFinalizer: ScenarioPropertiesConfigFinalizer,
      fragmentRepository: FragmentRepository
  )(implicit ec: ExecutionContext) =
    new DefinitionsService(
      processingTypeData.modelData,
      processingTypeData.scenarioPropertiesConfig,
      processingTypeData.deploymentManager,
      modelDefinitionEnricher,
      scenarioPropertiesConfigFinalizer,
      fragmentRepository
    )

  def createUIParameter(parameter: Parameter): UIParameter = {
    UIParameter(
      name = parameter.name,
      typ = parameter.typ,
      editor = parameter.finalEditor,
      defaultValue = parameter.finalDefaultValue,
      additionalVariables = parameter.additionalVariables.mapValuesNow(_.typingResult),
      variablesToHide = parameter.variablesToHide,
      branchParam = parameter.branchParam,
      hintText = parameter.hintText,
      label = parameter.label
    )
  }

  def createUIScenarioPropertyConfig(config: ScenarioPropertyConfig): UiScenarioPropertyConfig = {
    val editor = UiScenarioPropertyEditorDeterminer.determine(config)
    UiScenarioPropertyConfig(config.defaultValue, editor, config.label)
  }

}
