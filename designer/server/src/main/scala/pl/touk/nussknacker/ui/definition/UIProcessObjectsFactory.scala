package pl.touk.nussknacker.ui.definition

import cats.implicits.catsSyntaxSemigroup
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.generics
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.{DefaultComponentIdProvider, FragmentComponentDefinitionExtractor}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ComponentIdWithName, ProcessDefinition, ProcessDefinitionWithComponentIds, mapByName}
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

  def prepareUIProcessObjects(
      modelDataForType: ModelData,
      processDefinition: ProcessDefinition[ObjectDefinition],
      deploymentManager: DeploymentManager,
      user: LoggedUser,
      fragmentsDetails: Set[FragmentDetails],
      isFragment: Boolean,
      processCategoryService: ProcessCategoryService,
      additionalPropertiesConfig: Map[String, AdditionalPropertyConfig],
      processingType: ProcessingType
  ): UIProcessObjects = {
    val fixedComponentsUiConfig = ComponentsUiConfigExtractor.extract(modelDataForType.processConfig)

    val fragmentInputs =
      extractFragmentInputs(fragmentsDetails, modelDataForType.modelClassLoader.classLoader, fixedComponentsUiConfig)

    val combinedComponentsConfig =
      getCombinedComponentsConfig(fixedComponentsUiConfig, fragmentInputs, processDefinition)

    val componentIdProvider = new DefaultComponentIdProvider(Map(processingType -> combinedComponentsConfig)) // combinedComponentsConfig potentially changes componentIds

    val finalProcessDefinition = finalizeProcessDefinition(
      processDefinition.withComponentIds(componentIdProvider, processingType),
      combinedComponentsConfig,
      additionalComponentsUIConfigProvider.getAllForProcessingType(processingType).mapValuesNow(_.toSingleComponentConfig)
    )

    UIProcessObjects(
      componentGroups = ComponentDefinitionPreparer.prepareComponentsGroupList(
        user = user,
        processDefinition = finalProcessDefinition,
        fragmentInputs = fragmentInputs,
        isFragment = isFragment,
        componentsConfig = combinedComponentsConfig,
        componentsGroupMapping = ComponentsGroupMappingConfigExtractor.extract(modelDataForType.processConfig),
        processCategoryService = processCategoryService,
        customTransformerAdditionalData = finalProcessDefinition.customStreamTransformers.mapValuesNow(_._2),
        processingType
      ),
      processDefinition = createUIProcessDefinition(
        finalProcessDefinition,
        fragmentInputs,
        modelDataForType.modelDefinitionWithTypes.typeDefinitions.all.map(prepareClazzDefinition),
        processCategoryService
      ),
      componentsConfig = toComponentsUiConfig(finalProcessDefinition) |+| combinedComponentsConfig, // combinedComponentsConfig also contains config for components not part of processDefinition (fragments, base components)
      // TODO remove `componentsConfig` field? merge it with processDefinition? it would require FE changes
      additionalPropertiesConfig = additionalPropertiesConfig
        .filter(_ =>
          !isFragment
        ) // fixme: it should be introduced separate config for additionalPropertiesConfig for fragments. For now we skip that
        .mapValuesNow(createUIAdditionalPropertyConfig),
      edgesForNodes = ComponentDefinitionPreparer.prepareEdgeTypes(
        processDefinition = finalProcessDefinition,
        isFragment = isFragment,
        fragmentsDetails = fragmentsDetails
      ),
      customActions = deploymentManager.customActions.map(UICustomAction(_)),
      defaultAsyncInterpretation = getDefaultAsyncInterpretation(modelDataForType.processConfig)
    )
  }

  private def toComponentsUiConfig(processDefinition: ProcessDefinitionWithComponentIds[ObjectDefinition]): ComponentsUiConfig =
    processDefinition.allDefinitions.map{ case (idWithName, value) => idWithName.name -> value.componentConfig}

  private def finalizeProcessDefinition(processDefinitionWithIds: ProcessDefinitionWithComponentIds[ObjectDefinition],
                             combinedComponentsConfig: Map[String, SingleComponentConfig],
                             additionalComponentsUiConfig: Map[ComponentId, SingleComponentConfig]) = {

    val finalizeComponentConfig: ((ComponentIdWithName, ObjectDefinition)) => (ComponentIdWithName, ObjectDefinition) = {
      case (idWithName, value) =>
        val finalConfig = additionalComponentsUiConfig.getOrElse(idWithName.id, SingleComponentConfig.zero) |+|
          combinedComponentsConfig.getOrElse(idWithName.name, SingleComponentConfig.zero) |+|
          value.componentConfig

        idWithName -> value.withComponentConfig(finalConfig)
    }

    processDefinitionWithIds.copy(
      services = processDefinitionWithIds.services.map(finalizeComponentConfig),
      sourceFactories = processDefinitionWithIds.sourceFactories.map(finalizeComponentConfig),
      sinkFactories = processDefinitionWithIds.sinkFactories.map(finalizeComponentConfig),
      customStreamTransformers = processDefinitionWithIds.customStreamTransformers.map { case (idWithName, (value, additionalData)) =>
        val (_, finalValue) = finalizeComponentConfig(idWithName, value)
        idWithName -> (finalValue, additionalData)
      },
    )
  }

  private def getCombinedComponentsConfig(
      fixedComponentsUiConfig: ComponentsUiConfig,
      fragmentInputs: Map[String, FragmentObjectDefinition],
      processDefinition: ProcessDefinition[ObjectDefinition],
  ): ComponentsUiConfig = {
    val fragmentsComponentsConfig = fragmentInputs.mapValuesNow(_.objectDefinition.componentConfig)
    val processDefinitionComponentsConfig = processDefinition.allDefinitions.mapValuesNow(_.componentConfig)

    // we append fixedComponentsConfig, because configuration of default components (filters, switches) etc. will not be present in processDefinitionComponentsConfig...
    // maybe we can put them also in uiProcessDefinition.allDefinitions?
    ComponentDefinitionPreparer.combineComponentsConfig(
      fragmentsComponentsConfig,
      fixedComponentsUiConfig,
      processDefinitionComponentsConfig
    )
  }

  private def getDefaultAsyncInterpretation(processConfig: Config) = {
    val defaultUseAsyncInterpretationFromConfig =
      processConfig.as[Option[Boolean]]("asyncExecutionConfig.defaultUseAsyncInterpretation")
    DefaultAsyncInterpretationValueDeterminer.determine(defaultUseAsyncInterpretationFromConfig).value
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

  private def createUIObjectDefinition(
      objectDefinition: ObjectDefinition,
      processCategoryService: ProcessCategoryService
  ): UIObjectDefinition = {
    UIObjectDefinition(
      parameters = objectDefinition.parameters.map(createUIParameter),
      returnType = objectDefinition.returnType,
      categories = objectDefinition.categories.getOrElse(processCategoryService.getAllCategories),
      componentConfig = objectDefinition.componentConfig
    )
  }

  private def createUIFragmentObjectDefinition(
      fragmentObjectDefinition: FragmentObjectDefinition,
      processCategoryService: ProcessCategoryService
  ): UIFragmentObjectDefinition = {
    UIFragmentObjectDefinition(
      parameters = fragmentObjectDefinition.objectDefinition.parameters.map(createUIParameter),
      outputParameters = fragmentObjectDefinition.outputsDefinition,
      returnType = fragmentObjectDefinition.objectDefinition.returnType,
      categories = fragmentObjectDefinition.objectDefinition.categories.getOrElse(processCategoryService.getAllCategories),
      componentConfig = fragmentObjectDefinition.objectDefinition.componentConfig
    )
  }

  def createUIProcessDefinition(processDefinition: ProcessDefinitionWithComponentIds[ObjectDefinition],
                                fragmentInputs: Map[String, FragmentObjectDefinition],
                                types: Set[UIClazzDefinition],
                                processCategoryService: ProcessCategoryService): UIProcessDefinition = {
    def createUIObjectDef(objDef: ObjectDefinition) = createUIObjectDefinition(objDef, processCategoryService)

    def createUIFragmentObjectDef(objDef: FragmentObjectDefinition) = createUIFragmentObjectDefinition(objDef, processCategoryService)

    val transformed = processDefinition.transform(createUIObjectDef)
    UIProcessDefinition(
      services = mapByName(transformed.services),
      sourceFactories = mapByName(transformed.sourceFactories),
      sinkFactories = mapByName(transformed.sinkFactories),
      fragmentInputs = fragmentInputs.mapValuesNow(createUIFragmentObjectDef),
      customStreamTransformers = mapByName(transformed.customStreamTransformers).mapValuesNow(_._1),
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
