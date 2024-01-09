package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.component.{ComponentGroupsPreparer, EdgeTypesPreparer}
import pl.touk.nussknacker.ui.config.ComponentsGroupMappingConfigExtractor
import pl.touk.nussknacker.ui.definition.scenarioproperty.{FragmentPropertiesConfig, UiScenarioPropertyEditorDeterminer}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.security.api.LoggedUser

object UIProcessObjectsFactory {

  def prepareUIProcessObjects(
      modelDefinitionWithBuiltInComponentsAndFragments: ModelDefinition[ComponentStaticDefinition],
      modelData: ModelData,
      deploymentManager: DeploymentManager,
      user: LoggedUser,
      forFragment: Boolean,
      processCategoryService: ProcessCategoryService,
      scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
      processingType: ProcessingType
  ): UIProcessObjects = {
    val componentGroupsPreparer = new ComponentGroupsPreparer(
      ComponentsGroupMappingConfigExtractor.extract(modelData.modelConfig)
    )
    UIProcessObjects(
      componentGroups = componentGroupsPreparer.prepareComponentGroups(
        user = user,
        definitions = modelDefinitionWithBuiltInComponentsAndFragments,
        processCategoryService = processCategoryService,
        processingType
      ),
      components =
        modelDefinitionWithBuiltInComponentsAndFragments.components.mapValuesNow(createUIComponentDefinition),
      classes = modelData.modelDefinitionWithClasses.classDefinitions.all.toList.map(_.clazzName),
      componentsConfig = prepareComponentConfig(modelDefinitionWithBuiltInComponentsAndFragments, modelData),
      scenarioPropertiesConfig = (if (forFragment) FragmentPropertiesConfig.properties else scenarioPropertiesConfig)
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

  // TODO - Extract to the separate, named field in UIProcessObjects
  //      - Stop treating properties as a node on FE side
  //      - Other way to configure it - it should be somewhere around scenarioPropertiesConfig
  //      - Documentation
  // TODO (alternative): get rid of support fot that, we can configure only icon and docsUrl thanks to that?
  private def preparePropertiesConfig(modelData: ModelData) = {
    val componentsUiConfig          = ComponentsUiConfigParser.parse(modelData.modelConfig)
    val propertiesFakeComponentName = "$properties"
    componentsUiConfig.config.get(propertiesFakeComponentName).map(propertiesFakeComponentName -> _)
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
