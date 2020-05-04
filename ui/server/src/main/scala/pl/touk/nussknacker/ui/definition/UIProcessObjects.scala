package pl.touk.nussknacker.ui.definition

import io.circe.generic.JsonCodec
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._
import pl.touk.nussknacker.engine.util.config.FicusReaders._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, Parameter, ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.process.{AdditionalPropertyConfig, ParameterConfig, SingleNodeConfig}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, definition}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ProcessDefinition, SinkAdditionalData}
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.definition.{ProcessDefinitionExtractor, TypeInfos}
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.graph.node.{NodeData, SubprocessInputDefinition}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType
import pl.touk.nussknacker.ui.definition.additionalproperty.UiAdditionalPropertyConfig
import pl.touk.nussknacker.ui.definition.defaults.{DefaultValueDeterminerChain, ParamDefaultValueConfig}
import pl.touk.nussknacker.ui.definition.editor.ParameterEditorDeterminerChain
import pl.touk.nussknacker.ui.definition.validator.ParameterValidatorsDeterminerChain
import pl.touk.nussknacker.ui.process.ProcessTypesForCategories
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser

object UIProcessObjects {
  
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
    val uiProcessDefinition = UIProcessDefinition(chosenProcessDefinition, subprocessInputs, modelDataForType.typeDefinitions.map(prepareClazzDefinition))

    val sinkAdditionalData = chosenProcessDefinition.sinkFactories.map(e => (e._1, e._2._2))
    val customTransformerAdditionalData = chosenProcessDefinition.customStreamTransformers.map(e => (e._1, e._2._2))

    val dynamicNodesConfig = uiProcessDefinition.allDefinitions.mapValues(_.nodeConfig)

    val nodesConfig = NodesConfigCombiner.combine(fixedNodesConfig, dynamicNodesConfig)

    val defaultParametersValues = ParamDefaultValueConfig(nodesConfig.map { case (k, v) => (k, v.params.getOrElse(Map.empty)) })
    val defaultParametersFactory = DefaultValueDeterminerChain(defaultParametersValues)

    val nodeCategoryMapping = processConfig.getOrElse[Map[String, Option[String]]]("nodeCategoryMapping", Map.empty)
    val additionalPropertiesConfig = processConfig
      .getOrElse[Map[String, AdditionalPropertyConfig]]("additionalPropertiesConfig", Map.empty)
      .mapValues(UiAdditionalPropertyConfig(_))

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
        subprocessesDetails = subprocessesDetails))
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
    definition.Parameter.optional(p.name, typ, runtimeClass.toOption.getOrElse(classOf[Any]))
  }
}

@JsonCodec(encodeOnly = true) case class UIProcessObjects(nodesToAdd: List[NodeGroup],
                            processDefinition: UIProcessDefinition,
                            nodesConfig: Map[String, SingleNodeConfig],
                            additionalPropertiesConfig: Map[String, UiAdditionalPropertyConfig],
                            edgesForNodes: List[NodeEdges])

@JsonCodec(encodeOnly = true) case class UIProcessDefinition(services: Map[String, UIObjectDefinition],
                               sourceFactories: Map[String, UIObjectDefinition],
                               sinkFactories: Map[String, UIObjectDefinition],
                               customStreamTransformers: Map[String, UIObjectDefinition],
                               signalsWithTransformers: Map[String, UIObjectDefinition],
                               exceptionHandlerFactory: UIObjectDefinition,
                               globalVariables: Map[String, UIObjectDefinition],
                               typesInformation: Set[UIClazzDefinition],
                               subprocessInputs: Map[String, UIObjectDefinition]) {
  // skipping exceptionHandlerFactory
  val allDefinitions: Map[String, UIObjectDefinition] = services ++ sourceFactories ++ sinkFactories ++
    customStreamTransformers ++ signalsWithTransformers ++ globalVariables ++ subprocessInputs
}


@JsonCodec(encodeOnly = true) case class UIObjectDefinition(parameters: List[UIParameter],
                              returnType: Option[TypingResult],
                              categories: List[String],
                              nodeConfig: SingleNodeConfig) {

  def hasNoReturn : Boolean = returnType.isEmpty

}

object UIObjectDefinition {
  def apply(objectDefinition: ObjectDefinition): UIObjectDefinition = {
    UIObjectDefinition(
      parameters = objectDefinition.parameters.map(param => UIParameter(param, objectDefinition.nodeConfig.paramConfig(param.name))),
      returnType = if (objectDefinition.hasNoReturn) None else Some(objectDefinition.returnType),
      categories = objectDefinition.categories,
      nodeConfig = objectDefinition.nodeConfig
    )
  }
}

@JsonCodec(encodeOnly = true) case class UIClazzDefinition(clazzName: TypingResult, methods: Map[String, MethodInfo])

@JsonCodec(encodeOnly = true) case class UIParameter(name: String,
                                                     typ: TypingResult,
                                                     editor: ParameterEditor,
                                                     validators: List[ParameterValidator],
                                                     additionalVariables: Map[String, TypingResult],
                                                     branchParam: Boolean) {

  def isOptional: Boolean = !validators.contains(MandatoryParameterValidator)

}

object UIParameter {
  def apply(parameter: Parameter, paramConfig: ParameterConfig): UIParameter = {
    UIParameter(
      name = parameter.name,
      typ = parameter.typ,
      editor = ParameterEditorDeterminerChain(parameter, paramConfig).determineEditor(),
      validators = ParameterValidatorsDeterminerChain(paramConfig).determineValidators(parameter),
      additionalVariables = parameter.additionalVariables,
      branchParam = parameter.branchParam
    )
  }
}

object UIProcessDefinition {
  def apply(processDefinition: ProcessDefinition[ObjectDefinition], subprocessInputs: Map[String, ObjectDefinition], types: Set[UIClazzDefinition]): UIProcessDefinition = {
    val uiProcessDefinition = UIProcessDefinition(
      services = processDefinition.services.mapValues(UIObjectDefinition(_)),
      sourceFactories = processDefinition.sourceFactories.mapValues(UIObjectDefinition(_)),
      sinkFactories = processDefinition.sinkFactories.mapValues(e => UIObjectDefinition(e._1)),
      subprocessInputs = subprocessInputs.mapValues(UIObjectDefinition(_)),
      customStreamTransformers = processDefinition.customStreamTransformers.mapValues(e => UIObjectDefinition(e._1)),
      signalsWithTransformers = processDefinition.signalsWithTransformers.mapValues(e => UIObjectDefinition(e._1)),
      exceptionHandlerFactory = UIObjectDefinition(processDefinition.exceptionHandlerFactory),
      globalVariables = processDefinition.expressionConfig.globalVariables.mapValues(UIObjectDefinition(_)),
      typesInformation = types
    )
    uiProcessDefinition
  }
}


@JsonCodec case class NodeTypeId(`type`: String, id: Option[String] = None)

@JsonCodec case class NodeEdges(nodeId: NodeTypeId, edges: List[EdgeType], canChooseNodes: Boolean, isForInputDefinition: Boolean)

import pl.touk.nussknacker.engine.graph.NodeDataCodec._
@JsonCodec(encodeOnly = true) case class NodeToAdd(`type`: String, label: String, node: NodeData, categories: List[String], branchParametersTemplate: List[evaluatedparam.Parameter] = List.empty)

object SortedNodeGroup {
  def apply(name: String, possibleNodes: List[NodeToAdd]): NodeGroup = NodeGroup(name, possibleNodes.sortBy(_.label.toLowerCase))
}

@JsonCodec(encodeOnly = true) case class NodeGroup(name: String, possibleNodes: List[NodeToAdd])
