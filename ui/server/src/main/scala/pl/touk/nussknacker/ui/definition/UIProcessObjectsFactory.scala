package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.component.{AdditionalPropertyConfig, ComponentGroupName, ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, NotBlankParameterValidator, Parameter, RawParameterEditor, StringParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, generics}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.definition.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.parameter.defaults.{DefaultValueDeterminerChain, DefaultValueDeterminerParameters}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{ValidatorExtractorParameters, ValidatorsExtractor}
import pl.touk.nussknacker.engine.graph.node.{SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.component.ComponentDefinitionPreparer
import pl.touk.nussknacker.ui.config.ComponentsGroupMappingConfigExtractor
import pl.touk.nussknacker.ui.definition.additionalproperty.{AdditionalPropertyValidatorDeterminerChain, UiAdditionalPropertyEditorDeterminer}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser

object UIProcessObjectsFactory {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

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
    val subprocessInputs = fetchSubprocessInputs(subprocessesDetails, modelDataForType.modelClassLoader.classLoader, fixedComponentsUiConfig)
    val uiProcessDefinition = createUIProcessDefinition(chosenProcessDefinition, subprocessInputs, modelDataForType.typeDefinitions.map(prepareClazzDefinition), processCategoryService)

    val customTransformerAdditionalData = chosenProcessDefinition.customStreamTransformers.mapValuesNow(_._2)

    val dynamicComponentsConfig = uiProcessDefinition.allDefinitions.mapValues(_.componentConfig)

    val subprocessesComponentsConfig = subprocessInputs.mapValues(_.objectDefinition.componentConfig)
    //we append fixedComponentsConfig, because configuration of default components (filters, switches) etc. will not be present in dynamicComponentsConfig...
    //maybe we can put them also in uiProcessDefinition.allDefinitions?
    val finalComponentsConfig = ComponentDefinitionPreparer.combineComponentsConfig(subprocessesComponentsConfig, fixedComponentsUiConfig, dynamicComponentsConfig)

    val componentsGroupMapping = ComponentsGroupMappingConfigExtractor.extract(processConfig)

    val additionalPropertiesConfigForUi = additionalPropertiesConfig
      .filter(_ => !isSubprocess)// fixme: it should be introduced separate config for additionalPropertiesConfig for fragments. For now we skip that
      .mapValues(createUIAdditionalPropertyConfig)

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
    val methodsWithHighestArity = definition.methods.mapValues(toUIMethod)
    val staticMethodsWithHighestArity = definition.staticMethods.mapValues(toUIMethod)
    UIClazzDefinition(definition.clazzName, methodsWithHighestArity, staticMethodsWithHighestArity)
  }

  private def fetchSubprocessInputs(subprocessesDetails: Set[SubprocessDetails],
                                    classLoader: ClassLoader,
                                    fixedComponentsConfig: Map[String, SingleComponentConfig]): Map[String, FragmentObjectDefinition] = {
    val subprocessInputs = subprocessesDetails.collect {
      case fragment@SubprocessDetails(CanonicalProcess(MetaData(id, FragmentSpecificData(docsUrl), _, _), FlatNode(SubprocessInputDefinition(_, parameters, _)) :: _, _), category) =>
        val config = fixedComponentsConfig.getOrElse(id, SingleComponentConfig.zero).copy(docsUrl = docsUrl)
        val typedParameters = parameters.map(extractSubprocessParam(classLoader, config))

        def mapOutputs(nodes: List[CanonicalNode]): List[CanonicalNode] = nodes.flatMap {
          case canonicalnode.SplitNode(_, nextParts) => mapOutputs(nextParts.flatten)
          case node => List(node)
        }

        //Figure outputs parameter
        val outputParameters = ProcessConverter.findNodes(fragment.canonical).collect {
          case SubprocessOutputDefinition(_, name, fields, _) if fields.nonEmpty => extractSubprocessOutputParam(name)
        }

        val objectDefinition = new ObjectDefinition(typedParameters, Typed[java.util.Map[String, Any]], Some(List(category)), config)

        (id, FragmentObjectDefinition(objectDefinition, outputParameters))
    }.toMap
    subprocessInputs
  }

  private def extractSubprocessOutputParam(name: String) = Parameter(
    name = name,
    typ = Typed.apply[String],
    validators = List(MandatoryParameterValidator, NotBlankParameterValidator),
    editor = Some(StringParameterEditor),
    defaultValue = None,
    additionalVariables = Map.empty,
    variablesToHide = Set.empty,
    branchParam = false,
    isLazyParameter = false,
    scalaOptionParameter = false,
    javaOptionalParameter = false
  )

  private def extractSubprocessParam(classLoader: ClassLoader, componentConfig: SingleComponentConfig)(p: SubprocessParameter): Parameter = {
    val runtimeClass = p.typ.toRuntimeClass(classLoader)
    //TODO: currently if we cannot parse parameter class we assume it's unknown
    val typ = runtimeClass.map(Typed(_)).getOrElse(Unknown)
    val config = componentConfig.params.flatMap(_.get(p.name)).getOrElse(ParameterConfig.empty)
    val parameterData = ParameterData(typ, Nil)
    val extractedEditor = EditorExtractor.extract(parameterData, config)
    Parameter(
      name = p.name,
      typ = typ,
      editor = extractedEditor,
      validators = ValidatorsExtractor.extract(ValidatorExtractorParameters(parameterData, isOptional = true, config, extractedEditor)),
      // TODO: ability to pick default value from gui
      defaultValue = DefaultValueDeterminerChain.determineParameterDefaultValue(DefaultValueDeterminerParameters(parameterData, isOptional = true, config, extractedEditor)),
      additionalVariables = Map.empty,
      variablesToHide = Set.empty,
      branchParam = false,
      isLazyParameter = false,
      scalaOptionParameter = false,
      javaOptionalParameter = false
    )
  }

  private case class FragmentObjectDefinition(objectDefinition: ObjectDefinition, outputsDefinition: List[Parameter])

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
      outputParameters = fragmentObjectDefinition.outputsDefinition.map(createUIParameter),
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
      services = processDefinition.services.mapValues(createUIObjectDef),
      sourceFactories = processDefinition.sourceFactories.mapValues(createUIObjectDef),
      sinkFactories = processDefinition.sinkFactories.mapValues(createUIObjectDef),
      subprocessInputs = subprocessInputs.mapValues(createUIFragmentObjectDef),
      customStreamTransformers = processDefinition.customStreamTransformers.mapValues(e => createUIObjectDef(e._1)),
      signalsWithTransformers = processDefinition.signalsWithTransformers.mapValues(e => createUIObjectDef(e._1)),
      globalVariables = processDefinition.expressionConfig.globalVariables.mapValues(createUIObjectDef),
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
