package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser
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
        .finalizeScenarioProperties(scenarioPropertiesConfig, processingType)
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
      componentsConfig = prepareComponentConfig(modelDefinitionWithBuiltInComponentsAndFragments, modelData),
      scenarioPropertiesConfig =
        (if (forFragment) FragmentPropertiesConfig.properties else finalizedScenarioPropertiesConfig)
          .mapValuesNow(createUIScenarioPropertyConfig),
      edgesForNodes =
        EdgeTypesPreparer.prepareEdgeTypes(definitions = modelDefinitionWithBuiltInComponentsAndFragments),
      customActions = deploymentManager.customActions.map(UICustomAction(_))
    )
  }

  private def prepareComponentConfig(
      modelDefinitionWithBuiltInComponentsAndFragments: ModelDefinition[ComponentStaticDefinition],
      modelData: ModelData
  ) = {
    modelDefinitionWithBuiltInComponentsAndFragments.components.map { case (info, value) =>
      info.name -> value.componentConfig
    } ++ preparePropertiesConfig(modelData)
  }

  // TODO - Extract to the separate, named field in UIDefinitions which would hold also scenarioPropertiesConfig
  //      - Stop treating properties as a node on FE side
  //      - Other way to configure it - it should be somewhere around scenarioPropertiesConfig
  //      - Documentation
  // TODO (alternative): get rid of support fot that, we can configure only icon and docsUrl thanks to that?
  private def preparePropertiesConfig(modelData: ModelData) = {
    val componentsUiConfig          = ComponentsUiConfigParser.parse(modelData.modelConfig)
    val propertiesFakeComponentName = "$properties"
    componentsUiConfig.componentsConfig.get(propertiesFakeComponentName).map(propertiesFakeComponentName -> _)
  }

  private def createUIComponentDefinition(
      componentDefinition: ComponentStaticDefinition
  ): UIComponentDefinition = {
    UIComponentDefinition(
      parameters = componentDefinition.parameters.map(createUIParameter),
      returnType = componentDefinition.returnType,
      outputParameters = None
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
    val defaultValue = parameter.defaultValue.getOrElse(Expression.spel(""))
    UIParameter(
      name = parameter.name,
      typ = parameter.typ,
      editor = parameter.editor.getOrElse(RawParameterEditor),
      defaultValue = defaultValue,
      additionalVariables = parameter.additionalVariables.mapValuesNow(_.typingResult),
      variablesToHide = parameter.variablesToHide,
      branchParam = parameter.branchParam,
      hintText = parameter.hintText
    )
  }

  def createUIScenarioPropertyConfig(config: ScenarioPropertyConfig): UiScenarioPropertyConfig = {
    val editor = UiScenarioPropertyEditorDeterminer.determine(config)
    UiScenarioPropertyConfig(config.defaultValue, editor, config.label)
  }

}
