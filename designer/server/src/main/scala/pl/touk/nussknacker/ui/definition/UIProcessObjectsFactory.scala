package pl.touk.nussknacker.ui.definition

import cats.implicits.catsSyntaxSemigroup
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.fragment.{FragmentStaticDefinition, FragmentWithoutValidatorsDefinitionExtractor}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.modelconfig.{ComponentsUiConfig, ComponentsUiConfigParser}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.component.{ComponentAdditionalConfigConverter, ComponentDefinitionPreparer, DefaultComponentIdProvider}
import pl.touk.nussknacker.ui.config.ComponentsGroupMappingConfigExtractor
import pl.touk.nussknacker.ui.definition
import pl.touk.nussknacker.ui.definition.scenarioproperty.UiScenarioPropertyEditorDeterminer
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser

object UIProcessObjectsFactory {

  import net.ceedubs.ficus.Ficus._

  def prepareUIProcessObjects(
      modelDataForType: ModelData,
      modelDefinition: ModelDefinition[ComponentStaticDefinition],
      deploymentManager: DeploymentManager,
      user: LoggedUser,
      fragmentsDetails: Set[FragmentDetails],
      isFragment: Boolean,
      processCategoryService: ProcessCategoryService,
      scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
      processingType: ProcessingType,
      additionalUIConfigProvider: AdditionalUIConfigProvider
  ): UIProcessObjects = {
    val fixedComponentsUiConfig = ComponentsUiConfigParser.parse(modelDataForType.modelConfig)

    val combinedComponentsConfig = getCombinedComponentsConfig(fixedComponentsUiConfig, modelDefinition)

    val componentIdProvider = new DefaultComponentIdProvider(
      Map(processingType -> combinedComponentsConfig)
    ) // combinedComponentsConfig potentially changes componentIds

    val finalModelDefinition = finalizeModelDefinition(
      definition.ModelDefinitionWithComponentIds(modelDefinition, componentIdProvider, processingType),
      combinedComponentsConfig,
      additionalUIConfigProvider
        .getAllForProcessingType(processingType)
        .mapValuesNow(ComponentAdditionalConfigConverter.toSingleComponentConfig)
    )

    val fragmentComponents = extractFragmentComponents(modelDataForType.modelClassLoader.classLoader, fragmentsDetails)

    // merging because ModelDefinition doesn't contain configs for built-in components
    val finalComponentsConfig =
      toComponentsUiConfig(finalModelDefinition) |+| combinedComponentsConfig |+| ComponentsUiConfig(
        fragmentComponents.mapValuesNow(_.componentDefinition.componentConfig)
      )

    UIProcessObjects(
      componentGroups = ComponentDefinitionPreparer.prepareComponentsGroupList(
        user = user,
        modelDefinition = finalModelDefinition,
        fragmentComponents = fragmentComponents,
        isFragment = isFragment,
        componentsConfig = finalComponentsConfig,
        componentsGroupMapping = ComponentsGroupMappingConfigExtractor.extract(modelDataForType.modelConfig),
        processCategoryService = processCategoryService,
        processingType
      ),
      components = createUIComponentsDefinitions(
        finalModelDefinition,
        fragmentComponents,
        processCategoryService
      ),
      classes = modelDataForType.modelDefinitionWithClasses.classDefinitions.all.toList.map(_.clazzName),
      componentsConfig = finalComponentsConfig.config,
      scenarioPropertiesConfig =
        if (!isFragment) {
          (additionalUIConfigProvider.getScenarioPropertiesUIConfigs(processingType) |+| scenarioPropertiesConfig)
            .mapValuesNow(createUIScenarioPropertyConfig)
        } else
          Map.empty, // fixme: it should be introduced separate config for additionalPropertiesConfig for fragments. For now we skip that
      edgesForNodes = ComponentDefinitionPreparer.prepareEdgeTypes(
        modelDefinition = finalModelDefinition,
        isFragment = isFragment,
        fragmentsDetails = fragmentsDetails
      ),
      customActions = deploymentManager.customActions.map(UICustomAction(_)),
      defaultAsyncInterpretation = getDefaultAsyncInterpretation(modelDataForType.modelConfig)
    )
  }

  private def toComponentsUiConfig(
      modelDefinition: ModelDefinition[ComponentStaticDefinition]
  ): ComponentsUiConfig = {
    ComponentsUiConfig(
      modelDefinition.components.map { case (info, value) => info.name -> value.componentConfig }.toMap
    )
  }

  private def finalizeModelDefinition(
      modelDefinitionWithIds: ModelDefinitionWithComponentIds,
      combinedComponentsConfig: ComponentsUiConfig,
      additionalComponentsUiConfig: Map[ComponentId, SingleComponentConfig]
  ): ModelDefinition[ComponentStaticDefinition] = {

    def finalizeComponentConfig(
        idWithInfo: ComponentIdWithInfo,
        staticDefinition: ComponentStaticDefinition
    ): (String, ComponentStaticDefinition) = {
      val finalConfig = additionalComponentsUiConfig.getOrElse(idWithInfo.id, SingleComponentConfig.zero) |+|
        combinedComponentsConfig.getConfigByComponentName(idWithInfo.name) |+|
        staticDefinition.componentConfig

      idWithInfo.name -> staticDefinition.withComponentConfig(finalConfig)
    }

    ModelDefinition[ComponentStaticDefinition](
      modelDefinitionWithIds.components.map(finalizeComponentConfig _ tupled),
      modelDefinitionWithIds.expressionConfig,
      modelDefinitionWithIds.settings
    )
  }

  private def getCombinedComponentsConfig(
      fixedComponentsUiConfig: ComponentsUiConfig,
      modelDefinition: ModelDefinition[ComponentStaticDefinition],
  ): ComponentsUiConfig = {
    val modelDefinitionComponentsConfig = ComponentsUiConfig(modelDefinition.components.map { case (info, component) =>
      info.name -> component.componentConfig
    })

    // we append fixedComponentsConfig, because configuration of default components (filters, switches) etc. will not be present in modelDefinitionComponentsConfig...
    // maybe we can put them also in uiProcessDefinition.allDefinitions?
    ComponentDefinitionPreparer.combineComponentsConfig(
      fixedComponentsUiConfig,
      modelDefinitionComponentsConfig
    )
  }

  private def getDefaultAsyncInterpretation(modelConfig: Config) = {
    val defaultUseAsyncInterpretationFromConfig =
      modelConfig.as[Option[Boolean]]("asyncExecutionConfig.defaultUseAsyncInterpretation")
    DefaultAsyncInterpretationValueDeterminer.determine(defaultUseAsyncInterpretationFromConfig).value
  }

  private def extractFragmentComponents(
      classLoader: ClassLoader,
      fragmentsDetails: Set[FragmentDetails],
  ): Map[String, FragmentStaticDefinition] = {
    val definitionExtractor = new FragmentWithoutValidatorsDefinitionExtractor(classLoader)
    (for {
      details    <- fragmentsDetails
      definition <- definitionExtractor.extractFragmentComponentDefinition(details.canonical).toOption
    } yield {
      details.canonical.id -> definition.toStaticDefinition(details.category)
    }).toMap
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

  private def createUIFragmentComponentDefinition(
      fragmentDefinition: FragmentStaticDefinition,
      processCategoryService: ProcessCategoryService
  ): UIComponentDefinition = {
    UIComponentDefinition(
      parameters = fragmentDefinition.componentDefinition.parameters.map(createUIParameter),
      outputParameters = Some(fragmentDefinition.outputNames),
      returnType = fragmentDefinition.componentDefinition.returnType,
      categories = fragmentDefinition.componentDefinition.categories.getOrElse(processCategoryService.getAllCategories)
    )
  }

  private def createUIComponentsDefinitions(
      modelDefinition: ModelDefinition[ComponentStaticDefinition],
      // TODO: pass modelDefinition already enriched with components
      fragmentInputs: Map[String, FragmentStaticDefinition],
      processCategoryService: ProcessCategoryService
  ): Map[ComponentInfo, UIComponentDefinition] = {
    def createUIFragmentComponentDef(fragmentName: String, fragmentDef: FragmentStaticDefinition) =
      ComponentInfo(ComponentType.Fragment, fragmentName) -> createUIFragmentComponentDefinition(
        fragmentDef,
        processCategoryService
      )

    val transformedDefinition: Map[ComponentInfo, UIComponentDefinition] =
      modelDefinition.components.mapValuesNow(createUIComponentDefinition(_, processCategoryService))

    transformedDefinition ++ fragmentInputs.map(createUIFragmentComponentDef _ tupled)
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
