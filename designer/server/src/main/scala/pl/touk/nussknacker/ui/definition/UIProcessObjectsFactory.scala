package pl.touk.nussknacker.ui.definition

import cats.implicits.catsSyntaxSemigroup
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinition
import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, DefaultComponentIdProvider}
import pl.touk.nussknacker.engine.definition.fragment.{FragmentComponentDefinitionExtractor, FragmentStaticDefinition}
import pl.touk.nussknacker.engine.definition.model.{
  ComponentIdWithName,
  ModelDefinition,
  ModelDefinitionWithComponentIds
}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.component.ComponentDefinitionPreparer
import pl.touk.nussknacker.ui.config.ComponentsGroupMappingConfigExtractor
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

    val fragmentComponents =
      extractFragmentComponents(modelDataForType, fragmentsDetails)

    val combinedComponentsConfig =
      getCombinedComponentsConfig(fixedComponentsUiConfig, fragmentComponents, modelDefinition)

    val componentIdProvider =
      new DefaultComponentIdProvider(
        Map(processingType -> combinedComponentsConfig)
      ) // combinedComponentsConfig potentially changes componentIds

    val finalModelDefinition = finalizeModelDefinition(
      modelDefinition.withComponentIds(componentIdProvider, processingType),
      combinedComponentsConfig,
      additionalUIConfigProvider
        .getAllForProcessingType(processingType)
        .mapValuesNow(_.toSingleComponentConfig)
    )

    val finalComponentsConfig =
      toComponentsUiConfig(
        finalModelDefinition
      ) |+| combinedComponentsConfig // merging with combinedComponentsConfig, because ModelDefinition doesn't contain configs for base components and fragments

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
      processDefinition = createUIModelDefinition(
        finalModelDefinition,
        fragmentComponents,
        modelDataForType.modelDefinitionWithClasses.classDefinitions.all.map(prepareClazzDefinition),
        processCategoryService
      ),
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
      modelDefinition: ModelDefinitionWithComponentIds[ComponentStaticDefinition]
  ): ComponentsUiConfig = {
    ComponentsUiConfig(
      modelDefinition.components.map { case (idWithName, value) => idWithName.name -> value.componentConfig }.toMap
    )
  }

  private def finalizeModelDefinition(
      modelDefinitionWithIds: ModelDefinitionWithComponentIds[ComponentStaticDefinition],
      combinedComponentsConfig: ComponentsUiConfig,
      additionalComponentsUiConfig: Map[ComponentId, SingleComponentConfig]
  ) = {

    val finalizeComponentConfig
        : ((ComponentIdWithName, ComponentStaticDefinition)) => (ComponentIdWithName, ComponentStaticDefinition) = {
      case (idWithName, value) =>
        val finalConfig = additionalComponentsUiConfig.getOrElse(idWithName.id, SingleComponentConfig.zero) |+|
          combinedComponentsConfig.getConfigByComponentName(idWithName.name) |+|
          value.componentConfig

        idWithName -> value.withComponentConfig(finalConfig)
    }

    modelDefinitionWithIds.copy(
      components = modelDefinitionWithIds.components.map(finalizeComponentConfig)
    )
  }

  private def getCombinedComponentsConfig(
      fixedComponentsUiConfig: ComponentsUiConfig,
      fragmentComponents: Map[String, FragmentStaticDefinition],
      modelDefinition: ModelDefinition[ComponentStaticDefinition],
  ): ComponentsUiConfig = {
    val fragmentsComponentsConfig = ComponentsUiConfig(
      fragmentComponents.mapValuesNow(_.componentDefinition.componentConfig)
    )
    val modelDefinitionComponentsConfig = ComponentsUiConfig(modelDefinition.components.map { case (info, component) =>
      info.name -> component.componentConfig
    })

    // we append fixedComponentsConfig, because configuration of default components (filters, switches) etc. will not be present in modelDefinitionComponentsConfig...
    // maybe we can put them also in uiProcessDefinition.allDefinitions?
    ComponentDefinitionPreparer.combineComponentsConfig(
      fragmentsComponentsConfig,
      fixedComponentsUiConfig,
      modelDefinitionComponentsConfig
    )
  }

  private def getDefaultAsyncInterpretation(modelConfig: Config) = {
    val defaultUseAsyncInterpretationFromConfig =
      modelConfig.as[Option[Boolean]]("asyncExecutionConfig.defaultUseAsyncInterpretation")
    DefaultAsyncInterpretationValueDeterminer.determine(defaultUseAsyncInterpretationFromConfig).value
  }

  private def prepareClazzDefinition(definition: ClassDefinition): UIClassDefinition = {
    UIClassDefinition(definition.clazzName)
  }

  private def extractFragmentComponents(
      modelDataForType: ModelData,
      fragmentsDetails: Set[FragmentDetails],
  ): Map[String, FragmentStaticDefinition] = {
    val definitionExtractor = FragmentComponentDefinitionExtractor(modelDataForType)
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
    )
  }

  private def createUIFragmentComponentDefinition(
      fragmentDefinition: FragmentStaticDefinition,
      processCategoryService: ProcessCategoryService
  ): UIFragmentComponentDefinition = {
    UIFragmentComponentDefinition(
      parameters = fragmentDefinition.componentDefinition.parameters.map(createUIParameter),
      outputParameters = fragmentDefinition.outputNames,
      returnType = fragmentDefinition.componentDefinition.returnType,
      categories = fragmentDefinition.componentDefinition.categories.getOrElse(processCategoryService.getAllCategories)
    )
  }

  def createUIModelDefinition(
      modelDefinition: ModelDefinitionWithComponentIds[ComponentStaticDefinition],
      fragmentInputs: Map[String, FragmentStaticDefinition],
      types: Set[UIClassDefinition],
      processCategoryService: ProcessCategoryService
  ): UIModelDefinition = {
    def createUIFragmentComponentDef(fragmentDef: FragmentStaticDefinition) =
      createUIFragmentComponentDefinition(fragmentDef, processCategoryService)

    def filterByTypeAndTransform(componentType: ComponentType): Map[String, UIComponentDefinition] =
      modelDefinition.components
        .filter(_._2.componentType == componentType)
        .map { case (idWithName, value) =>
          idWithName.name -> createUIComponentDefinition(value, processCategoryService)
        }
        .toMap

    UIModelDefinition(
      services = filterByTypeAndTransform(ComponentType.Service),
      sourceFactories = filterByTypeAndTransform(ComponentType.Source),
      sinkFactories = filterByTypeAndTransform(ComponentType.Sink),
      fragmentInputs = fragmentInputs.mapValuesNow(createUIFragmentComponentDef),
      customStreamTransformers = filterByTypeAndTransform(ComponentType.CustomComponent),
      typesInformation = types
    )
  }

  def createUIParameter(parameter: Parameter): UIParameter = {
    val defaultValue = parameter.defaultValue.getOrElse(Expression.spel(""))
    UIParameter(
      name = parameter.name,
      typ = parameter.typ,
      editor = parameter.editor.getOrElse(RawParameterEditor),
      validators = parameter.validators,
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
