package pl.touk.nussknacker.ui.definition

import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{AdditionalPropertyConfig, ParameterConfig, SingleNodeConfig}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, definition}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.util.config.FicusReaders._
import pl.touk.nussknacker.restmodel.definition.{NodeGroup, NodeToAdd, UIClazzDefinition, UIObjectDefinition, UIParameter, UIProcessDefinition, UIProcessObjects, UiAdditionalPropertyConfig}
import pl.touk.nussknacker.ui.definition.additionalproperty.{AdditionalPropertyValidatorDeterminerChain, UiAdditionalPropertyEditorDeterminer}
import pl.touk.nussknacker.ui.definition.defaults.{DefaultValueDeterminerChain, ParamDefaultValueConfig}
import pl.touk.nussknacker.ui.definition.editor.ParameterEditorDeterminerChain
import pl.touk.nussknacker.ui.definition.validator.ParameterValidatorsDeterminerChain
import pl.touk.nussknacker.ui.process.ProcessTypesForCategories
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser

object UIProcessObjectsFactory {

  def prepareUIProcessObjects(modelDataForType: ModelData,
                              user: LoggedUser,
                              subprocessesDetails: Set[SubprocessDetails],
                              isSubprocess: Boolean,
                              typesForCategories: ProcessTypesForCategories): UIProcessObjects = {
    val processConfig = modelDataForType.processConfig

    val chosenProcessDefinition: ProcessDefinition[ObjectDefinition] = modelDataForType.processDefinition
    val fixedNodesConfig = ProcessDefinitionExtractor.extractNodesConfig(processConfig)

    //FIXME: how to handle dynamic configuration of subprocesses??
    val subprocessInputs = fetchSubprocessInputs(subprocessesDetails, modelDataForType.modelClassLoader.classLoader, fixedNodesConfig)
    val uiProcessDefinition = createUIProcessDefinition(chosenProcessDefinition, subprocessInputs, modelDataForType.typeDefinitions.map(prepareClazzDefinition))

    val sinkAdditionalData = chosenProcessDefinition.sinkFactories.map(e => (e._1, e._2._2))
    val customTransformerAdditionalData = chosenProcessDefinition.customStreamTransformers.map(e => (e._1, e._2._2))

    val dynamicNodesConfig = uiProcessDefinition.allDefinitions.mapValues(_.nodeConfig)

    val nodesConfig = NodesConfigCombiner.combine(fixedNodesConfig, dynamicNodesConfig)

    val defaultParametersValues = ParamDefaultValueConfig(nodesConfig.map { case (k, v) => (k, v.params.getOrElse(Map.empty)) })
    val defaultParametersFactory = DefaultValueDeterminerChain(defaultParametersValues)

    val nodeCategoryMapping = processConfig.getOrElse[Map[String, Option[String]]]("nodeCategoryMapping", Map.empty)
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
        defaultsStrategy = defaultParametersFactory,
        nodesConfig = nodesConfig,
        nodeCategoryMapping = nodeCategoryMapping,
        typesForCategories = typesForCategories,
        sinkAdditionalData = sinkAdditionalData,
        customTransformerAdditionalData = customTransformerAdditionalData
      ),
      processDefinition = uiProcessDefinition,
      nodesConfig = nodesConfig,
      additionalPropertiesConfig = additionalPropertiesConfig,
      edgesForNodes = DefinitionPreparer.prepareEdgeTypes(
        user = user,
        processDefinition = chosenProcessDefinition,
        isSubprocess = isSubprocess,
        subprocessesDetails = subprocessesDetails),
      defaultAsyncInterpretation = defaultAsyncInterpretation.value)
  }

  private def prepareClazzDefinition(definition: ClazzDefinition): UIClazzDefinition = {
    // TODO: present all overloaded methods on FE
    val methodsWithHighestArity = definition.methods.mapValues(_.maxBy(_.parameters.size))
    UIClazzDefinition(definition.clazzName, methodsWithHighestArity)
  }

  private def fetchSubprocessInputs(subprocessesDetails: Set[SubprocessDetails],
                                    classLoader: ClassLoader,
                                    fixedNodesConfig: Map[String, SingleNodeConfig]): Map[String, ObjectDefinition] = {
    val subprocessInputs = subprocessesDetails.collect {
      case SubprocessDetails(CanonicalProcess(MetaData(id, _, _, _, _), _, FlatNode(SubprocessInputDefinition(_, parameters, _)) :: _, additionalBranches), category) =>
        val typedParameters = parameters.map(extractSubprocessParam(classLoader))
        (id, new ObjectDefinition(typedParameters, Typed[java.util.Map[String, Any]], List(category), fixedNodesConfig.getOrElse(id, SingleNodeConfig.zero)))
    }.toMap
    subprocessInputs
  }

  private def extractSubprocessParam(classLoader: ClassLoader)(p: SubprocessParameter) = {
    val runtimeClass = p.typ.toRuntimeClass(classLoader)
    //TODO: currently if we cannot parse parameter class we assume it's unknown
    val typ = runtimeClass.map(Typed(_)).getOrElse(Unknown)
    //subprocess parameter name, editor and validators yet can not be configured via annotation or process creator
    definition.Parameter.optional(p.name, typ)
  }

  def createUIObjectDefinition(objectDefinition: ObjectDefinition): UIObjectDefinition = {
    UIObjectDefinition(
      parameters = objectDefinition.parameters.map(param => createUIParameter(param, objectDefinition.nodeConfig.paramConfig(param.name))),
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

  def createUIParameter(parameter: Parameter, paramConfig: ParameterConfig): UIParameter = {
    UIParameter(
      name = parameter.name,
      typ = parameter.typ,
      editor = ParameterEditorDeterminerChain(parameter, paramConfig).determineEditor(),
      validators = ParameterValidatorsDeterminerChain(paramConfig).determineValidators(parameter),
      additionalVariables = parameter.additionalVariables,
      branchParam = parameter.branchParam
    )
  }

  def createUIAdditionalPropertyConfig(config: AdditionalPropertyConfig): UiAdditionalPropertyConfig = {
    val editor = UiAdditionalPropertyEditorDeterminer.determine(config)
    val determinedValidators = AdditionalPropertyValidatorDeterminerChain(config).determine()
    new UiAdditionalPropertyConfig(config.defaultValue, editor, determinedValidators, config.label)
  }
}

object SortedNodeGroup {
  def apply(name: String, possibleNodes: List[NodeToAdd]): NodeGroup = NodeGroup(name, possibleNodes.sortBy(_.label.toLowerCase))
}

