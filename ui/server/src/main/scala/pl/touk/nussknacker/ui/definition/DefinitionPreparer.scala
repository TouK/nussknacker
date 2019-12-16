package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.process.SingleNodeConfig
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ProcessDefinition, SinkAdditionalData}
import pl.touk.nussknacker.engine.definition.defaults.{NodeDefinition, ParameterDefaultValueExtractorStrategy}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType.{FilterFalse, FilterTrue}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.process.uiconfig.defaults.ParameterEvaluatorExtractor
import pl.touk.nussknacker.ui.security.api.Permission._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.ui.process.ProcessTypesForCategories

import scala.collection.immutable.ListMap
import scala.runtime.BoxedUnit

//TODO: some refactoring?
object DefinitionPreparer {

  def prepareNodesToAdd(user: LoggedUser,
                        processDefinition: ProcessDefinition[ObjectDefinition],
                        isSubprocess: Boolean,
                        subprocessInputs: Map[String, ObjectDefinition],
                        extractorFactory: ParameterDefaultValueExtractorStrategy,
                        nodesConfig: Map[String, SingleNodeConfig],
                        nodeCategoryMapping: Map[String, Option[String]],
                        typesForCategories: ProcessTypesForCategories
                       ): List[NodeGroup] = {
    val evaluator = new ParameterEvaluatorExtractor(extractorFactory)
    val readCategories = typesForCategories.getAllCategories.filter(user.can(_, Read))

    def filterCategories(objectDefinition: ObjectDefinition): List[String] = readCategories.intersect(objectDefinition.categories)

    def objDefParams(id: String, objDefinition: ObjectDefinition): List[Parameter] =
      evaluator.evaluateParameters(NodeDefinition(id, objDefinition.parameters))

    def objDefBranchParams(id: String, objDefinition: ObjectDefinition): List[Parameter] =
      evaluator.evaluateBranchParameters(NodeDefinition(id, objDefinition.parameters))

    def serviceRef(id: String, objDefinition: ObjectDefinition) = ServiceRef(id, objDefParams(id, objDefinition))

    val returnsUnit = ((_: String, objectDefinition: ObjectDefinition)
    => objectDefinition.hasNoReturn).tupled

    //TODO: make it possible to configure other defaults here.
    val base = NodeGroup("base", List(
      NodeToAdd("filter", "filter", Filter("", Expression("spel", "true")), readCategories),
      NodeToAdd("split", "split", Split(""), readCategories),
      NodeToAdd("switch", "switch", Switch("", Expression("spel", "true"), "output"), readCategories),
      NodeToAdd("variable", "variable", Variable("", "varName", Expression("spel", "'value'")), readCategories),
      NodeToAdd("mapVariable", "mapVariable", VariableBuilder("", "mapVarName", List(Field("varName", Expression("spel", "'value'")))), readCategories),
      NodeToAdd("sqlVariable", "sqlVariable", Variable("", "varName", Expression("sql", "SELECT * FROM input")), readCategories)
    ))
    val services = NodeGroup("services",
      processDefinition.services.filter(returnsUnit).map {
        case (id, objDefinition) => NodeToAdd("processor", id,
          Processor("", serviceRef(id, objDefinition)), filterCategories(objDefinition))
      }.toList
    )

    val enrichers = NodeGroup("enrichers",
      processDefinition.services.filterNot(returnsUnit).map {
        case (id, objDefinition) => NodeToAdd("enricher", id,
          Enricher("", serviceRef(id, objDefinition), "output"), filterCategories(objDefinition))
      }.toList
    )

    val customTransformers = NodeGroup("custom",
      processDefinition.customStreamTransformers.map {
        // branchParameters = List.empty can be tricky here. We moved template for branch parameters to NodeToAdd because
        // branch parameters inside node.Join are branchId -> List[Parameter] and on node template level we don't know what
        // branches will be. After moving this parameters to BranchEnd it will disappear from here.
        // Also it is not the best design pattern to reply with backend's NodeData as a template in API.
        // TODO: keep only custom node ids in nodesToAdd element and move templates to parameters definition API
        case (id, (objDefinition, additionalData)) if additionalData.manyInputs => NodeToAdd("customNode", id,
          node.Join("", if (objDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(id, objDefinition), List.empty),
          filterCategories(objDefinition), objDefBranchParams(id, objDefinition))
        case (id, (objDefinition, additionalData)) => NodeToAdd("customNode", id,
          CustomNode("", if (objDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(id, objDefinition)), filterCategories(objDefinition))
      }.toList
    )

    val sinks = NodeGroup("sinks",
      processDefinition.sinkFactories.map {
        case (id, (objDefinition, SinkAdditionalData(requiresOutput))) => NodeToAdd("sink", id,
          Sink("", SinkRef(id, objDefParams(id, objDefinition)),
            if (requiresOutput) Some(Expression("spel", "#input")) else None), filterCategories(objDefinition)
        )
      }.toList)

    val inputs = if (!isSubprocess) {
      NodeGroup("sources",
        processDefinition.sourceFactories.map {
          case (id, objDefinition) => NodeToAdd("source", id,
            Source("", SourceRef(id, objDefParams(id, objDefinition))),
            filterCategories(objDefinition)
          )
        }.toList)
    } else {
      NodeGroup("subprocessDefinition", List(
        NodeToAdd("input", "input", SubprocessInputDefinition("", List()), readCategories),
        NodeToAdd("output", "output", SubprocessOutputDefinition("", "output"), readCategories)
      ))
    }

    //so far we don't allow nested subprocesses...
    val subprocesses = if (!isSubprocess) {
      List(
        NodeGroup("subprocesses",
          subprocessInputs.map {
            case (id, definition) =>
              val nodes = evaluator.evaluateParameters(NodeDefinition(id, definition.parameters))
              NodeToAdd("subprocess", id, SubprocessInput("", SubprocessRef(id, nodes)), readCategories.intersect(definition.categories))
          }.toList))
    } else {
      List.empty
    }

    // return none if category should be hidden
    def getNodeCategory(nodeName: String, initialCategory: String): Option[String] = {
      val category = nodesConfig.get(nodeName).flatMap(_.category).getOrElse(initialCategory)
      nodeCategoryMapping.getOrElse(category, Some(category))
    }

    val virtualGroups = List(
      List(inputs),
      List(base),
      List(enrichers, customTransformers) ++ subprocesses,
      List(services, sinks))

    virtualGroups
      .zipWithIndex
      .flatMap {
        case (groups, virtualGroupIndex) =>
          for {
            group <- groups
            node <- group.possibleNodes
            notHiddenCategory <- getNodeCategory(node.label, group.name)
          } yield (virtualGroupIndex, notHiddenCategory, node)
      }
      .groupBy {
        case (virtualGroupIndex, categoryName, _) => (virtualGroupIndex, categoryName)
      }
      .mapValues(v => v.map(e => e._3))
      .toList
      .sortBy {
        case ((virtualGroupIndex, categoryName), _) => (virtualGroupIndex, categoryName.toLowerCase)
      }
      // we need to merge nodes in the same category but in other virtual group
      .foldLeft(ListMap.empty[String, List[NodeToAdd]]) {
        case (acc, ((_, categoryName), elements)) =>
          val accElements = acc.getOrElse(categoryName, List.empty) ++ elements
          acc + (categoryName -> accElements)
      }
      .toList
      .map {
        case (categoryName, elements: List[NodeToAdd]) => SortedNodeGroup(categoryName, elements)
      }
  }

  def prepareEdgeTypes(user: LoggedUser, processDefinition: ProcessDefinition[ObjectDefinition],
                       isSubprocess: Boolean, subprocessesDetails: Set[SubprocessDetails]): List[NodeEdges] = {

    val subprocessOutputs = if (isSubprocess) List() else subprocessesDetails.map(_.canonical).map { process =>
      val outputs = ProcessConverter.findNodes(process).collect {
        case SubprocessOutputDefinition(_, name, _) => name
      }
      //TODO: enable choice of output type
      NodeEdges(NodeTypeId("SubprocessInput", Some(process.metaData.id)), outputs.map(EdgeType.SubprocessOutput),
        canChooseNodes = false, isForInputDefinition = false)
    }

    val joinInputs = processDefinition.customStreamTransformers.collect {
      case (name, value) if value._2.manyInputs =>
        NodeEdges(NodeTypeId("Join", Some(name)), List(), canChooseNodes = true, isForInputDefinition = true)
    }

    List(
      NodeEdges(NodeTypeId("Split"), List(), canChooseNodes = true, isForInputDefinition = false),
      NodeEdges(NodeTypeId("Switch"), List(
        EdgeType.NextSwitch(Expression("spel", "true")), EdgeType.SwitchDefault), canChooseNodes = true, isForInputDefinition = false),
      NodeEdges(NodeTypeId("Filter"), List(FilterTrue, FilterFalse), canChooseNodes = false, isForInputDefinition = false)
    ) ++ subprocessOutputs ++ joinInputs
  }
}
