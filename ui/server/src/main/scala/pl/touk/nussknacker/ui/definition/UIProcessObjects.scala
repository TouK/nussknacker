package pl.touk.nussknacker.ui.definition

import argonaut.CodecJson
import argonaut.Argonaut._
import com.typesafe.config.ConfigRenderOptions
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{ParameterConfig, SingleNodeConfig}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.graph.node.{NodeData, SubprocessInputDefinition}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType
import pl.touk.nussknacker.ui.process.uiconfig.defaults.{DefaultValueExtractorChain, ParamDefaultValueConfig}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.definition.ParameterRestriction
import pl.touk.nussknacker.engine.api.{MetaData, definition}
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.definition.ParameterTypeMapper
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails

object UIProcessObjects {

  private implicit val nodeConfig: ValueReader[ParameterRestriction] = ValueReader.relative(config => {
    val json = config.root().render(ConfigRenderOptions.concise().setJson(true))
    implicit val cd: CodecJson[ParameterRestriction] = ParameterRestriction.codec
    json.decodeEither[ParameterRestriction].right.getOrElse(throw new IllegalArgumentException("Failed to parse config"))
  })

  def prepareUIProcessObjects(modelDataForType: ModelData,
                              user: LoggedUser,
                              subprocessesDetails: Set[SubprocessDetails],
                              isSubprocess: Boolean): UIProcessObjects = {
    val processConfig = modelDataForType.processConfig

    val chosenProcessDefinition = modelDataForType.processDefinition
    val fixedNodesConfig = processConfig.getOrElse[Map[String, SingleNodeConfig]]("nodes", Map.empty)

    //FIXME: how to handle dynamic configuration of subprocesses??
    val subprocessInputs = fetchSubprocessInputs(subprocessesDetails, modelDataForType.modelClassLoader.classLoader, fixedNodesConfig)
    val uiProcessDefinition = UIProcessDefinition(chosenProcessDefinition, subprocessInputs)

    val dynamicNodesConfig = uiProcessDefinition.allDefinitions.mapValues(_.nodeConfig)

    val nodesConfig = NodesConfigCombiner.combine(fixedNodesConfig, dynamicNodesConfig)

    val defaultParametersValues = ParamDefaultValueConfig(nodesConfig.map { case (k, v) => (k, v.params.getOrElse(Map.empty)) })
    val defaultParametersFactory = DefaultValueExtractorChain(defaultParametersValues, modelDataForType.modelClassLoader)

    val nodeCategoryMapping = processConfig.getOrElse[Map[String, String]]("nodeCategoryMapping", Map.empty)
    val additionalPropertiesConfig = processConfig.getOrElse[Map[String, AdditionalProcessProperty]]("additionalFieldsConfig", Map.empty)


    UIProcessObjects(
      nodesToAdd = DefinitionPreparer.prepareNodesToAdd(
        user = user,
        processDefinition = chosenProcessDefinition,
        isSubprocess = isSubprocess,
        subprocessInputs = subprocessInputs,
        extractorFactory = defaultParametersFactory,
        nodesConfig = nodesConfig,
        nodeCategoryMapping = nodeCategoryMapping
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

  private def fetchSubprocessInputs(subprocessesDetails: Set[SubprocessDetails], classLoader: ClassLoader, config: Map[String, SingleNodeConfig]): Map[String, ObjectDefinition] = {
    val subprocessInputs = subprocessesDetails.collect {
      case SubprocessDetails(CanonicalProcess(MetaData(id, _, _, _, _), _, FlatNode(SubprocessInputDefinition(_, parameters, _)) :: _, additionalBranches), category) =>
        val clazzRefParams = parameters.map { p =>
          //TODO: currently if we cannot parse parameter class we assume it's unknown
          val classRef = p.typ.toClazzRef(classLoader).getOrElse(ClazzRef.unknown)
          val parameterConfig = config.get(id).map(_.paramConfig(p.name)).getOrElse(ParameterConfig.empty)
          definition.Parameter(p.name, classRef, classRef, ParameterTypeMapper.prepareRestrictions(classRef.clazz, None, parameterConfig))
        }
        (id, ObjectDefinition(clazzRefParams, ClazzRef[java.util.Map[String, Any]], List(category)))
    }.toMap
    subprocessInputs
  }
}

case class UIProcessObjects(nodesToAdd: List[NodeGroup],
                            processDefinition: UIProcessDefinition,
                            nodesConfig: Map[String, SingleNodeConfig],
                            additionalPropertiesConfig: Map[String, AdditionalProcessProperty],
                            edgesForNodes: List[NodeEdges])

case class UIProcessDefinition(services: Map[String, ObjectDefinition],
                               sourceFactories: Map[String, ObjectDefinition],
                               sinkFactories: Map[String, ObjectDefinition],
                               customStreamTransformers: Map[String, ObjectDefinition],
                               signalsWithTransformers: Map[String, ObjectDefinition],
                               exceptionHandlerFactory: ObjectDefinition,
                               globalVariables: Map[String, ObjectDefinition],
                               typesInformation: List[ClazzDefinition],
                               subprocessInputs: Map[String, ObjectDefinition]) {
  // skipping exceptionHandlerFactory
  val allDefinitions: Map[String, ObjectDefinition] = services ++ sourceFactories ++ sinkFactories ++
    customStreamTransformers ++ signalsWithTransformers ++ globalVariables ++ subprocessInputs
}


object UIProcessDefinition {
  def apply(processDefinition: ProcessDefinition[ObjectDefinition], subprocessInputs: Map[String, ObjectDefinition]): UIProcessDefinition = {
    val uiProcessDefinition = UIProcessDefinition(
      services = processDefinition.services,
      sourceFactories = processDefinition.sourceFactories,
      sinkFactories = processDefinition.sinkFactories,
      subprocessInputs = subprocessInputs,
      customStreamTransformers = processDefinition.customStreamTransformers.mapValues(_._1),
      signalsWithTransformers = processDefinition.signalsWithTransformers.mapValues(_._1),
      exceptionHandlerFactory = processDefinition.exceptionHandlerFactory,
      globalVariables = processDefinition.expressionConfig.globalVariables,
      typesInformation = processDefinition.typesInformation
    )
    uiProcessDefinition
  }
}

case class NodeTypeId(`type`: String, id: Option[String] = None)

case class NodeEdges(nodeId: NodeTypeId, edges: List[EdgeType], canChooseNodes: Boolean, isForInputDefinition: Boolean)

case class NodeToAdd(`type`: String, label: String, node: NodeData, categories: List[String])

object SortedNodeGroup {
  def apply(name: String, possibleNodes: List[NodeToAdd]): NodeGroup = NodeGroup(name, possibleNodes.sortBy(_.label.toLowerCase))
}

case class NodeGroup(name: String, possibleNodes: List[NodeToAdd])
