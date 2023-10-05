package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.generics
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.{ComponentIdProvider, DefaultComponentIdProvider, FragmentComponentDefinitionExtractor}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.component.ComponentDefinitionPreparer
import pl.touk.nussknacker.ui.config.ComponentsGroupMappingConfigExtractor
import pl.touk.nussknacker.ui.definition.additionalproperty.{AdditionalPropertyValidatorDeterminerChain, UiAdditionalPropertyEditorDeterminer}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser

object UIProcessObjectsFactory {

  import net.ceedubs.ficus.Ficus._

  private lazy val additionalComponentsUIConfigProvider: AdditionalComponentsUIConfigProvider = {
    Multiplicity(ScalaServiceLoader.load[AdditionalComponentsUIConfigProvider](getClass.getClassLoader)) match {
      case Empty() => AdditionalComponentsUIConfigProvider.empty
      case One(provider) => provider
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(s"More than one AdditionalComponentsUIConfigProvider instance found: $moreThanOne")
    }
  }

  def prepareUIProcessObjects(modelDataForType: ModelData,
                              staticObjectsDefinition: ProcessDefinition[ObjectDefinition],
                              deploymentManager: DeploymentManager,
                              user: LoggedUser,
                              fragmentsDetails: Set[FragmentDetails],
                              isFragment: Boolean,
                              processCategoryService: ProcessCategoryService,
                              additionalPropertiesConfig: Map[String, AdditionalPropertyConfig],
                              processingType: ProcessingType): UIProcessObjects = {
    val processConfig = modelDataForType.processConfig

    val fixedComponentsUiConfig = ComponentsUiConfigExtractor.extract(processConfig)

    //FIXME: how to handle dynamic configuration of fragments??
    val fragmentInputs = extractFragmentInputs(fragmentsDetails, modelDataForType.modelClassLoader.classLoader, fixedComponentsUiConfig)
    val uiClazzDefinitions = modelDataForType.modelDefinitionWithTypes.typeDefinitions.all.map(prepareClazzDefinition)
    val uiProcessDefinition = createUIProcessDefinition(staticObjectsDefinition, fragmentInputs,
      uiClazzDefinitions, processCategoryService)

    val dynamicComponentsConfig = uiProcessDefinition.allDefinitions.mapValuesNow(_.componentConfig)
    val fragmentsComponentsConfig = fragmentInputs.mapValuesNow(_.objectDefinition.componentConfig)

    //we append fixedComponentsConfig, because configuration of default components (filters, switches) etc. will not be present in dynamicComponentsConfig...
    //maybe we can put them also in uiProcessDefinition.allDefinitions?
    val combinedComponentsConfig = ComponentDefinitionPreparer.combineComponentsConfig(fragmentsComponentsConfig, fixedComponentsUiConfig, dynamicComponentsConfig)

    val componentIdProvider = new DefaultComponentIdProvider(Map(processingType -> combinedComponentsConfig))
    val componentIdToName = staticObjectsDefinition.componentIdToName(componentIdProvider, processingType)

    val additionalComponentsUIConfig = additionalComponentsUIConfigProvider.getAllForProcessingType(processingType).flatMap {
      case (componentId, config) => componentIdToName.get(componentId).map(_ -> config.toSingleComponentConfig)
    }

    val finalComponentsConfig = ComponentDefinitionPreparer.combineComponentsConfig(additionalComponentsUIConfig, combinedComponentsConfig)

    val componentsGroupMapping = ComponentsGroupMappingConfigExtractor.extract(processConfig)

    val additionalPropertiesConfigForUi = additionalPropertiesConfig
      .filter(_ => !isFragment) // fixme: it should be introduced separate config for additionalPropertiesConfig for fragments. For now we skip that
      .mapValuesNow(createUIAdditionalPropertyConfig)

    val defaultUseAsyncInterpretationFromConfig = processConfig.as[Option[Boolean]]("asyncExecutionConfig.defaultUseAsyncInterpretation")
    val defaultAsyncInterpretation: DefaultAsyncInterpretationValue = DefaultAsyncInterpretationValueDeterminer.determine(defaultUseAsyncInterpretationFromConfig)

    UIProcessObjects(
      componentGroups = ComponentDefinitionPreparer.prepareComponentsGroupList(
        user = user,
        processDefinition = uiProcessDefinition,
        isFragment = isFragment,
        componentsConfig = finalComponentsConfig,
        componentsGroupMapping = componentsGroupMapping,
        processCategoryService = processCategoryService,
        customTransformerAdditionalData = staticObjectsDefinition.customStreamTransformers.mapValuesNow(_._2),
        processingType
      ),
      processDefinition = uiProcessDefinition,
      componentsConfig = finalComponentsConfig,
      additionalPropertiesConfig = additionalPropertiesConfigForUi,
      edgesForNodes = ComponentDefinitionPreparer.prepareEdgeTypes(
        processDefinition = staticObjectsDefinition,
        isFragment = isFragment,
        fragmentsDetails = fragmentsDetails
      ),
      customActions = deploymentManager.customActions.map(UICustomAction(_)),
      defaultAsyncInterpretation = defaultAsyncInterpretation.value)
  }

  private def prepareClazzDefinition(definition: ClazzDefinition): UIClazzDefinition = {
    def toUIBasicParam(p: generics.Parameter): UIBasicParameter = UIBasicParameter(p.name, p.refClazz)

    // TODO: present all overloaded methods on FE
    def toUIMethod(methods: List[MethodInfo]): UIMethodInfo = {
      val m = methods.maxBy(_.signatures.map(_.parametersToList.length).toList.max)
      val sig = m.signatures.toList.maxBy(_.parametersToList.length)
      // We send varArg as Type instead of Array[Type] so it is easier to
      // format it on FE.
      UIMethodInfo(
        (sig.noVarArgs ::: sig.varArg.toList).map(toUIBasicParam),
        sig.result,
        m.description,
        sig.varArg.isDefined
      )
    }

    val methodsWithHighestArity = definition.methods.mapValuesNow(toUIMethod)
    val staticMethodsWithHighestArity = definition.staticMethods.mapValuesNow(toUIMethod)
    UIClazzDefinition(definition.clazzName, methodsWithHighestArity, staticMethodsWithHighestArity)
  }

  private def extractFragmentInputs(fragmentsDetails: Set[FragmentDetails],
                                    classLoader: ClassLoader,
                                    fixedComponentsConfig: Map[String, SingleComponentConfig]): Map[String, FragmentObjectDefinition] = {
    val definitionExtractor = new FragmentComponentDefinitionExtractor(fixedComponentsConfig.get, classLoader)
    (for {
      details <- fragmentsDetails
      definition <- definitionExtractor.extractFragmentComponentDefinition(details.canonical).toOption
    } yield {
      val objectDefinition = ObjectDefinition(definition.parameters, Some(Typed[java.util.Map[String, Any]]), Some(List(details.category)), definition.config)
      details.canonical.id -> FragmentObjectDefinition(objectDefinition, definition.outputNames)
    }).toMap
  }

  case class FragmentObjectDefinition(objectDefinition: ObjectDefinition, outputsDefinition: List[String])

  def createUIObjectDefinition(objectDefinition: ObjectDefinition, processCategoryService: ProcessCategoryService): UIObjectDefinition = {
    UIObjectDefinition(
      parameters = objectDefinition.parameters.map(createUIParameter),
      returnType = objectDefinition.returnType,
      categories = objectDefinition.categories.getOrElse(processCategoryService.getAllCategories),
      componentConfig = objectDefinition.componentConfig
    )
  }

  def createUIFragmentObjectDefinition(fragmentObjectDefinition: FragmentObjectDefinition, processCategoryService: ProcessCategoryService): UIFragmentObjectDefinition = {
    UIFragmentObjectDefinition(
      parameters = fragmentObjectDefinition.objectDefinition.parameters.map(createUIParameter),
      outputParameters = fragmentObjectDefinition.outputsDefinition,
      returnType = fragmentObjectDefinition.objectDefinition.returnType,
      categories = fragmentObjectDefinition.objectDefinition.categories.getOrElse(processCategoryService.getAllCategories),
      componentConfig = fragmentObjectDefinition.objectDefinition.componentConfig
    )
  }

  def createUIProcessDefinition(processDefinition: ProcessDefinition[ObjectDefinition],
                                fragmentInputs: Map[String, FragmentObjectDefinition],
                                types: Set[UIClazzDefinition],
                                processCategoryService: ProcessCategoryService): UIProcessDefinition = {
    def createUIObjectDef(objDef: ObjectDefinition) = createUIObjectDefinition(objDef, processCategoryService)

    def createUIFragmentObjectDef(objDef: FragmentObjectDefinition) = createUIFragmentObjectDefinition(objDef, processCategoryService)

    val transformed = processDefinition.transform(createUIObjectDef)
    UIProcessDefinition(
      services = transformed.services,
      sourceFactories = transformed.sourceFactories,
      sinkFactories = transformed.sinkFactories,
      fragmentInputs = fragmentInputs.mapValuesNow(createUIFragmentObjectDef),
      customStreamTransformers = transformed.customStreamTransformers.mapValuesNow(_._1),
      globalVariables = transformed.expressionConfig.globalVariables,
      typesInformation = types
    )
  }

  def createUIParameter(parameter: Parameter): UIParameter = {
    val defaultValue = parameter.defaultValue.getOrElse(Expression.spel(""))
    UIParameter(name = parameter.name, typ = parameter.typ, editor = parameter.editor.getOrElse(RawParameterEditor), validators = parameter.validators, defaultValue = defaultValue,
      additionalVariables = parameter.additionalVariables.mapValuesNow(_.typingResult), variablesToHide = parameter.variablesToHide, branchParam = parameter.branchParam)
  }

  def createUIAdditionalPropertyConfig(config: AdditionalPropertyConfig): UiAdditionalPropertyConfig = {
    val editor = UiAdditionalPropertyEditorDeterminer.determine(config)
    val determinedValidators = AdditionalPropertyValidatorDeterminerChain(config).determine()
    UiAdditionalPropertyConfig(config.defaultValue, editor, determinedValidators, config.label)
  }
}

object SortedComponentGroup {
  def apply(name: ComponentGroupName, components: List[ComponentTemplate]): ComponentGroup =
    ComponentGroup(name, components.sortBy(_.label.toLowerCase))
}
