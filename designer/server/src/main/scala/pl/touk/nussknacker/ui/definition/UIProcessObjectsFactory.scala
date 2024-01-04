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
import pl.touk.nussknacker.ui.component.{
  ComponentAdditionalConfigConverter,
  ComponentGroupsPreparer,
  DefaultComponentIdProvider,
  EdgeTypesPreparer
}
import pl.touk.nussknacker.ui.config.ComponentsGroupMappingConfigExtractor
import pl.touk.nussknacker.ui.definition.scenarioproperty.UiScenarioPropertyEditorDeterminer
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
      scenarioPropertiesConfig: Map[ProcessingType, ScenarioPropertyConfig],
      processingType: ProcessingType,
      additionalUIConfigProvider: AdditionalUIConfigProvider
  ): UIProcessObjects = {
    val componentIdProvider = new DefaultComponentIdProvider(
      Map(processingType -> toComponentsUiConfig(modelDefinitionWithBuiltInComponentsAndFragments))
    )

    val finalModelDefinition = finalizeModelDefinition(
      ModelDefinitionWithComponentIds(
        modelDefinitionWithBuiltInComponentsAndFragments,
        componentIdProvider,
        processingType
      ),
      additionalUIConfigProvider
        .getAllForProcessingType(processingType)
        .mapValuesNow(ComponentAdditionalConfigConverter.toSingleComponentConfig)
    )
    val finalComponentsConfig = toComponentsUiConfig(finalModelDefinition)

    val componentGroupsPreparer = new ComponentGroupsPreparer(
      ComponentsGroupMappingConfigExtractor.extract(modelDataForType.modelConfig)
    )
    UIProcessObjects(
      componentGroups = componentGroupsPreparer.prepareComponentGroups(
        user = user,
        definitions = finalModelDefinition,
        componentsConfig = finalComponentsConfig,
        processCategoryService = processCategoryService,
        processingType
      ),
      components = modelDefinitionWithBuiltInComponentsAndFragments.components.mapValuesNow(
        createUIComponentDefinition(_, processCategoryService)
      ),
      classes = modelDataForType.modelDefinitionWithClasses.classDefinitions.all.toList.map(_.clazzName),
      componentsConfig = finalComponentsConfig.config,
      scenarioPropertiesConfig =
        if (!forFragment) {
          (additionalUIConfigProvider.getScenarioPropertiesUIConfigs(processingType) |+| scenarioPropertiesConfig)
            .mapValuesNow(createUIScenarioPropertyConfig)
        } else
          Map.empty, // fixme: it should be introduced separate config for additionalPropertiesConfig for fragments. For now we skip that
      edgesForNodes = EdgeTypesPreparer.prepareEdgeTypes(definitions = finalModelDefinition),
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

  // FIXME: Extract this step to step before model definition enrichments
  private def finalizeModelDefinition(
      modelDefinitionWithIds: ModelDefinitionWithComponentIds,
      additionalComponentsUiConfig: Map[ComponentId, SingleComponentConfig]
  ): ModelDefinition[ComponentStaticDefinition] = {

    def finalizeComponentConfig(
        idWithInfo: ComponentIdWithInfo,
        staticDefinition: ComponentStaticDefinition
    ): (String, ComponentStaticDefinition) = {
      val finalConfig = additionalComponentsUiConfig.getOrElse(idWithInfo.id, SingleComponentConfig.zero) |+|
        staticDefinition.componentConfig

      idWithInfo.name -> staticDefinition.withComponentConfig(finalConfig)
    }

    ModelDefinition[ComponentStaticDefinition](
      modelDefinitionWithIds.components.map(finalizeComponentConfig _ tupled),
      modelDefinitionWithIds.expressionConfig,
      modelDefinitionWithIds.settings
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

object SortedComponentGroup {
  def apply(name: ComponentGroupName, components: List[ComponentNodeTemplate]): ComponentGroup =
    ComponentGroup(name, components.sortBy(_.label.toLowerCase))
}
