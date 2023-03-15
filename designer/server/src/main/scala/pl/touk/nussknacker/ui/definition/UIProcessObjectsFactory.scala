package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.component.{AdditionalPropertyConfig, ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.generics
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.SubprocessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.component.ComponentDefinitionPreparer
import pl.touk.nussknacker.ui.config.ComponentsGroupMappingConfigExtractor
import pl.touk.nussknacker.ui.definition.additionalproperty.{AdditionalPropertyValidatorDeterminerChain, UiAdditionalPropertyEditorDeterminer}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser

object UIProcessObjectsFactory {

  import net.ceedubs.ficus.Ficus._

  def prepareUIProcessObjects(modelDataForType: ModelData,
                              deploymentManager: DeploymentManager,
                              user: LoggedUser,
                              subprocessesDetails: Set[SubprocessDetails],
                              isSubprocess: Boolean,
                              processCategoryService: ProcessCategoryService,
                              additionalPropertiesConfig: Map[String, AdditionalPropertyConfig],
                              processingType: String): UIProcessObjects = {
    val processConfig = modelDataForType.processConfig

    val chosenProcessDefinition: ProcessDefinition[ObjectDefinition] = modelDataForType.processDefinition
    val fixedComponentsUiConfig = ComponentsUiConfigExtractor.extract(processConfig)

    //FIXME: how to handle dynamic configuration of subprocesses??
    val subprocessInputs = extractSubprocessInputs(subprocessesDetails, modelDataForType.modelClassLoader.classLoader, fixedComponentsUiConfig)
    val uiProcessDefinition = createUIProcessDefinition(chosenProcessDefinition, subprocessInputs, modelDataForType.typeDefinitions.map(prepareClazzDefinition), processCategoryService)

    val customTransformerAdditionalData = chosenProcessDefinition.customStreamTransformers.mapValuesNow(_._2)

    val dynamicComponentsConfig = uiProcessDefinition.allDefinitions.mapValuesNow(_.componentConfig)

    val subprocessesComponentsConfig = subprocessInputs.mapValuesNow(_.objectDefinition.componentConfig)
    //we append fixedComponentsConfig, because configuration of default components (filters, switches) etc. will not be present in dynamicComponentsConfig...
    //maybe we can put them also in uiProcessDefinition.allDefinitions?
    val finalComponentsConfig = ComponentDefinitionPreparer.combineComponentsConfig(subprocessesComponentsConfig, fixedComponentsUiConfig, dynamicComponentsConfig)

    val componentsGroupMapping = ComponentsGroupMappingConfigExtractor.extract(processConfig)

    val additionalPropertiesConfigForUi = additionalPropertiesConfig
      .filter(_ => !isSubprocess)// fixme: it should be introduced separate config for additionalPropertiesConfig for fragments. For now we skip that
      .mapValuesNow(createUIAdditionalPropertyConfig)

    val defaultUseAsyncInterpretationFromConfig = processConfig.as[Option[Boolean]]("asyncExecutionConfig.defaultUseAsyncInterpretation")
    val defaultAsyncInterpretation: DefaultAsyncInterpretationValue = DefaultAsyncInterpretationValueDeterminer.determine(defaultUseAsyncInterpretationFromConfig)

    UIProcessObjects(
      componentGroups = ComponentDefinitionPreparer.prepareComponentsGroupList(
        user = user,
        processDefinition = uiProcessDefinition,
        isSubprocess = isSubprocess,
        componentsConfig = finalComponentsConfig,
        componentsGroupMapping = componentsGroupMapping,
        processCategoryService = processCategoryService,
        customTransformerAdditionalData = customTransformerAdditionalData,
        processingType
      ),
      processDefinition = uiProcessDefinition,
      componentsConfig = finalComponentsConfig,
      additionalPropertiesConfig = additionalPropertiesConfigForUi,
      edgesForNodes = ComponentDefinitionPreparer.prepareEdgeTypes(
        processDefinition = chosenProcessDefinition,
        isSubprocess = isSubprocess,
        subprocessesDetails = subprocessesDetails
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

  private def extractSubprocessInputs(subprocessesDetails: Set[SubprocessDetails],
                                      classLoader: ClassLoader,
                                      fixedComponentsConfig: Map[String, SingleComponentConfig]): Map[String, FragmentObjectDefinition] = {
    val definitionExtractor = new SubprocessDefinitionExtractor(fixedComponentsConfig.get, classLoader)
    subprocessesDetails.map { details =>
      val definition = definitionExtractor.extractSubprocessDefinition(details.canonical)
      val outputParameterNames = definition.allOutputs.map(_.name).sorted
      val objectDefinition = new ObjectDefinition(definition.parameters, Typed[java.util.Map[String, Any]], Some(List(details.category)), definition.config)
      details.canonical.id -> FragmentObjectDefinition(objectDefinition, outputParameterNames)
    }.toMap
  }

  case class FragmentObjectDefinition(objectDefinition: ObjectDefinition, outputsDefinition: List[String])

  def createUIObjectDefinition(objectDefinition: ObjectDefinition, processCategoryService: ProcessCategoryService): UIObjectDefinition = {
    UIObjectDefinition(
      parameters = objectDefinition.parameters.map(createUIParameter),
      returnType = if (objectDefinition.hasNoReturn) None else Some(objectDefinition.returnType),
      categories = objectDefinition.categories.getOrElse(processCategoryService.getAllCategories),
      componentConfig = objectDefinition.componentConfig
    )
  }

  def createUIFragmentObjectDefinition(fragmentObjectDefinition: FragmentObjectDefinition, processCategoryService: ProcessCategoryService): UIFragmentObjectDefinition = {
    UIFragmentObjectDefinition(
      parameters = fragmentObjectDefinition.objectDefinition.parameters.map(createUIParameter),
      outputParameters = fragmentObjectDefinition.outputsDefinition,
      returnType = if (fragmentObjectDefinition.objectDefinition.hasNoReturn) None else Some(fragmentObjectDefinition.objectDefinition.returnType),
      categories = fragmentObjectDefinition.objectDefinition.categories.getOrElse(processCategoryService.getAllCategories),
      componentConfig = fragmentObjectDefinition.objectDefinition.componentConfig
    )
  }

  def createUIProcessDefinition(processDefinition: ProcessDefinition[ObjectDefinition],
                                subprocessInputs: Map[String, FragmentObjectDefinition],
                                types: Set[UIClazzDefinition],
                                processCategoryService: ProcessCategoryService): UIProcessDefinition = {
    def createUIObjectDef(objDef: ObjectDefinition) = createUIObjectDefinition(objDef, processCategoryService)
    def createUIFragmentObjectDef(objDef: FragmentObjectDefinition) = createUIFragmentObjectDefinition(objDef, processCategoryService)

    val uiProcessDefinition = UIProcessDefinition(
      services = processDefinition.services.mapValuesNow(createUIObjectDef),
      sourceFactories = processDefinition.sourceFactories.mapValuesNow(createUIObjectDef),
      sinkFactories = processDefinition.sinkFactories.mapValuesNow(createUIObjectDef),
      subprocessInputs = subprocessInputs.mapValuesNow(createUIFragmentObjectDef),
      customStreamTransformers = processDefinition.customStreamTransformers.mapValuesNow(e => createUIObjectDef(e._1)),
      globalVariables = processDefinition.expressionConfig.globalVariables.mapValuesNow(createUIObjectDef),
      typesInformation = types
    )
    uiProcessDefinition
  }

  def createUIParameter(parameter: Parameter): UIParameter = {
    UIParameter(name = parameter.name, typ = parameter.typ, editor = parameter.editor.getOrElse(RawParameterEditor), validators = parameter.validators, defaultValue = parameter.defaultValue.getOrElse(""),
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
