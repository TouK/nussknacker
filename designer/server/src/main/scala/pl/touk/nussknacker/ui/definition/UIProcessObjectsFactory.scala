package pl.touk.nussknacker.ui.definition

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
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
      modelDataForType: ModelData,
      deploymentManager: DeploymentManager,
      user: LoggedUser,
      forFragment: Boolean,
      processCategoryService: ProcessCategoryService,
      scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
      processingType: ProcessingType
  ): UIProcessObjects = {
    val componentGroupsPreparer = new ComponentGroupsPreparer(
      ComponentsGroupMappingConfigExtractor.extract(modelDataForType.modelConfig)
    )
    UIProcessObjects(
      componentGroups = componentGroupsPreparer.prepareComponentGroups(
        user = user,
        definitions = modelDefinitionWithBuiltInComponentsAndFragments,
        processCategoryService = processCategoryService,
        processingType
      ),
      components = modelDefinitionWithBuiltInComponentsAndFragments.components.mapValuesNow(
        createUIComponentDefinition(_, processCategoryService)
      ),
      classes = modelDataForType.modelDefinitionWithClasses.classDefinitions.all.toList.map(_.clazzName),
      componentsConfig = toComponentsUiConfig(modelDefinitionWithBuiltInComponentsAndFragments).config,
      scenarioPropertiesConfig = (if (forFragment) FragmentPropertiesConfig.properties else scenarioPropertiesConfig)
        .mapValuesNow(createUIScenarioPropertyConfig),
      edgesForNodes =
        EdgeTypesPreparer.prepareEdgeTypes(definitions = modelDefinitionWithBuiltInComponentsAndFragments),
      customActions = deploymentManager.customActions.map(UICustomAction(_))
    )
  }

  private def toComponentsUiConfig(
      modelDefinition: ModelDefinition[ComponentStaticDefinition]
  ): ComponentsUiConfig = {
    ComponentsUiConfig(
      modelDefinition.components.map { case (info, value) => info.name -> value.componentConfig }.toMap
    )
  }

  private def createUIComponentDefinition(
      componentDefinition: ComponentStaticDefinition,
      processCategoryService: ProcessCategoryService
  ): UIComponentDefinition = {
    UIComponentDefinition(
      parameters = componentDefinition.parameters.map(createUIParameter),
      returnType = componentDefinition.returnType,
      categories = componentDefinition.categories.getOrElse(processCategoryService.getAllCategories),
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
