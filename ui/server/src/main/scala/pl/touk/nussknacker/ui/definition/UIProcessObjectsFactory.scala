package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.{Parameter, RawParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.component.{AdditionalPropertyConfig, ParameterConfig}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.definition.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.parameter.defaults.{DefaultValueDeterminerChain, DefaultValueDeterminerParameters}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{ValidatorExtractorParameters, ValidatorsExtractor}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.ui.definition.additionalproperty.{AdditionalPropertyValidatorDeterminerChain, UiAdditionalPropertyEditorDeterminer}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser

object UIProcessObjectsFactory {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._
  import pl.touk.nussknacker.ui.config.ComponentsGroupMappingConfig._

  def prepareUIProcessObjects(modelDataForType: ModelData,
                              deploymentManager: DeploymentManager,
                              user: LoggedUser,
                              subprocessesDetails: Set[SubprocessDetails],
                              isSubprocess: Boolean,
                              processCategoryService: ProcessCategoryService): UIProcessObjects = {
    val processConfig = modelDataForType.processConfig

    val chosenProcessDefinition: ProcessDefinition[ObjectDefinition] = modelDataForType.processDefinition
    val fixedNodesConfig = ProcessDefinitionExtractor.extractNodesConfig(processConfig)

    //FIXME: how to handle dynamic configuration of subprocesses??
    val subprocessInputs = fetchSubprocessInputs(subprocessesDetails, modelDataForType.modelClassLoader.classLoader, fixedNodesConfig)
    val uiProcessDefinition = createUIProcessDefinition(chosenProcessDefinition, subprocessInputs, modelDataForType.typeDefinitions.map(prepareClazzDefinition))

    val sinkAdditionalData = chosenProcessDefinition.sinkFactories.map(e => (e._1, e._2._2))
    val customTransformerAdditionalData = chosenProcessDefinition.customStreamTransformers.map(e => (e._1, e._2._2))

    val dynamicNodesConfig = uiProcessDefinition.allDefinitions.mapValues(_.nodeConfig)

    //we append fixedNodesConfig, because configuration of default nodes (filters, switches) etc. will not be present in dynamicNodesConfig...
    //maybe we can put them also in uiProcessDefinition.allDefinitions?
    val finalNodesConfig = NodesConfigCombiner.combine(fixedNodesConfig, dynamicNodesConfig)

    val componentsGroupMapping = processConfig.as[Map[ComponentGroupName, Option[ComponentGroupName]]]("componentsGroupMapping")

    val additionalPropertiesConfig = processConfig
      .getOrElse[Map[String, AdditionalPropertyConfig]]("additionalPropertiesConfig", Map.empty)
      .mapValues(createUIAdditionalPropertyConfig)

    val defaultUseAsyncInterpretationFromConfig = processConfig.as[Option[Boolean]]("asyncExecutionConfig.defaultUseAsyncInterpretation")
    val defaultAsyncInterpretation: DefaultAsyncInterpretationValue = DefaultAsyncInterpretationValueDeterminer.determine(defaultUseAsyncInterpretationFromConfig)

    UIProcessObjects(
      nodesToAdd = DefinitionPreparer.prepareNodesToAdd(
        user = user,
        processDefinition = uiProcessDefinition,
        isSubprocess = isSubprocess,
        nodesConfig = finalNodesConfig,
        componentsGroupMapping = componentsGroupMapping,
        processCategoryService = processCategoryService,
        sinkAdditionalData = sinkAdditionalData,
        customTransformerAdditionalData = customTransformerAdditionalData
      ),
      processDefinition = uiProcessDefinition,
      nodesConfig = finalNodesConfig,
      additionalPropertiesConfig = additionalPropertiesConfig,
      edgesForNodes = DefinitionPreparer.prepareEdgeTypes(
        processDefinition = chosenProcessDefinition,
        isSubprocess = isSubprocess,
        subprocessesDetails = subprocessesDetails
      ),
      customActions = deploymentManager.customActions.map(UICustomAction(_)),
      defaultAsyncInterpretation = defaultAsyncInterpretation.value)
  }

  private def prepareClazzDefinition(definition: ClazzDefinition): UIClazzDefinition = {
    // TODO: present all overloaded methods on FE
    val methodsWithHighestArity = definition.methods.mapValues(_.maxBy(_.parameters.size))
    val staticMethodsWithHighestArity = definition.staticMethods.mapValues(_.maxBy(_.parameters.size))
    UIClazzDefinition(definition.clazzName, methodsWithHighestArity, staticMethodsWithHighestArity)
  }

  private def fetchSubprocessInputs(subprocessesDetails: Set[SubprocessDetails],
                                    classLoader: ClassLoader,
                                    fixedNodesConfig: Map[String, SingleComponentConfig]): Map[String, ObjectDefinition] = {
    val subprocessInputs = subprocessesDetails.collect {
      case SubprocessDetails(CanonicalProcess(MetaData(id, _, _, _, _), _, FlatNode(SubprocessInputDefinition(_, parameters, _)) :: _, _), category) =>
        val config = fixedNodesConfig.getOrElse(id, SingleComponentConfig.zero)
        val typedParameters = parameters.map(extractSubprocessParam(classLoader, config))
        (id, new ObjectDefinition(typedParameters, Typed[java.util.Map[String, Any]], List(category), fixedNodesConfig.getOrElse(id, SingleComponentConfig.zero)))
    }.toMap
    subprocessInputs
  }

  private def extractSubprocessParam(classLoader: ClassLoader, nodeConfig: SingleComponentConfig)(p: SubprocessParameter): Parameter = {
    val runtimeClass = p.typ.toRuntimeClass(classLoader)
    //TODO: currently if we cannot parse parameter class we assume it's unknown
    val typ = runtimeClass.map(Typed(_)).getOrElse(Unknown)
    val config = nodeConfig.params.flatMap(_.get(p.name)).getOrElse(ParameterConfig.empty)
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
      javaOptionalParameter = false)
  }

  def createUIObjectDefinition(objectDefinition: ObjectDefinition): UIObjectDefinition = {
    UIObjectDefinition(
      parameters = objectDefinition.parameters.map(param => createUIParameter(param)),
      returnType = if (objectDefinition.hasNoReturn) None else Some(objectDefinition.returnType),
      categories = objectDefinition.categories,
      nodeConfig = objectDefinition.nodeConfig
    )
  }

  def createUIProcessDefinition(processDefinition: ProcessDefinition[ObjectDefinition],
                                subprocessInputs: Map[String, ObjectDefinition],
                                types: Set[UIClazzDefinition]): UIProcessDefinition = {
    val uiProcessDefinition = UIProcessDefinition(
      services = processDefinition.services.mapValues(createUIObjectDefinition),
      sourceFactories = processDefinition.sourceFactories.mapValues(createUIObjectDefinition),
      sinkFactories = processDefinition.sinkFactories.mapValues(e => createUIObjectDefinition(e._1)),
      subprocessInputs = subprocessInputs.mapValues(createUIObjectDefinition),
      customStreamTransformers = processDefinition.customStreamTransformers.mapValues(e => createUIObjectDefinition(e._1)),
      signalsWithTransformers = processDefinition.signalsWithTransformers.mapValues(e => createUIObjectDefinition(e._1)),
      exceptionHandlerFactory = createUIObjectDefinition(processDefinition.exceptionHandlerFactory),
      globalVariables = processDefinition.expressionConfig.globalVariables.mapValues(createUIObjectDefinition),
      typesInformation = types
    )
    uiProcessDefinition
  }

  def createUIParameter(parameter: Parameter): UIParameter = {
    UIParameter(
      name = parameter.name,
      typ = parameter.typ,
      editor = parameter.editor.getOrElse(RawParameterEditor),
      validators = parameter.validators,
      defaultValue = parameter.defaultValue,
      additionalVariables = parameter.additionalVariables,
      variablesToHide = parameter.variablesToHide,
      branchParam = parameter.branchParam
    )
  }

  def createUIAdditionalPropertyConfig(config: AdditionalPropertyConfig): UiAdditionalPropertyConfig = {
    val editor = UiAdditionalPropertyEditorDeterminer.determine(config)
    val determinedValidators = AdditionalPropertyValidatorDeterminerChain(config).determine()
    UiAdditionalPropertyConfig(config.defaultValue, editor, determinedValidators, config.label)
  }
}

object SortedNodeGroup {
  def apply(name: ComponentGroupName, possibleNodes: List[NodeToAdd]): NodeGroup = NodeGroup(name, possibleNodes.sortBy(_.label.toLowerCase))
}

