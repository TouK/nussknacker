package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.process.SingleNodeConfig
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ProcessDefinition, SinkAdditionalData}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.restmodel.definition._
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType.{FilterFalse, FilterTrue}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.api.Permission._

import scala.collection.immutable.ListMap

//TODO: some refactoring?
object DefinitionPreparer {

  def prepareNodesToAdd(user: LoggedUser,
                        processDefinition: UIProcessDefinition,
                        isSubprocess: Boolean,
                        nodesConfig: Map[String, SingleNodeConfig],
                        componentsGroupMapping: Map[String, Option[String]],
                        processCategoryService: ProcessCategoryService,
                        sinkAdditionalData: Map[String, SinkAdditionalData],
                        customTransformerAdditionalData: Map[String, CustomTransformerAdditionalData]
                       ): List[NodeGroup] = {
    val readCategories = processCategoryService.getAllCategories.filter(user.can(_, Read))

    def filterCategories(objectDefinition: UIObjectDefinition): List[String] = readCategories.intersect(objectDefinition.categories)

    def objDefParams(id: String, objDefinition: UIObjectDefinition): List[Parameter] =
      EvaluatedParameterPreparer.prepareEvaluatedParameter(objDefinition.parameters)

    def objDefBranchParams(id: String, objDefinition: UIObjectDefinition): List[Parameter] =
      EvaluatedParameterPreparer.prepareEvaluatedBranchParameter(objDefinition.parameters)

    def serviceRef(id: String, objDefinition: UIObjectDefinition) = ServiceRef(id, objDefParams(id, objDefinition))

    val returnsUnit = ((_: String, objectDefinition: UIObjectDefinition)
    => objectDefinition.hasNoReturn).tupled

    //TODO: make it possible to configure other defaults here.
    val base = NodeGroup("base", List(
      NodeToAdd("filter", "filter", Filter("", Expression("spel", "true")), readCategories),
      NodeToAdd("split", "split", Split(""), readCategories),
      NodeToAdd("switch", "switch", Switch("", Expression("spel", "true"), "output"), readCategories),
      NodeToAdd("variable", "variable", Variable("", "varName", Expression("spel", "'value'")), readCategories),
      NodeToAdd("mapVariable", "mapVariable", VariableBuilder("", "mapVarName", List(Field("varName", Expression("spel", "'value'")))), readCategories),
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
      processDefinition.customStreamTransformers.collect {
        // branchParameters = List.empty can be tricky here. We moved template for branch parameters to NodeToAdd because
        // branch parameters inside node.Join are branchId -> List[Parameter] and on node template level we don't know what
        // branches will be. After moving this parameters to BranchEnd it will disappear from here.
        // Also it is not the best design pattern to reply with backend's NodeData as a template in API.
        // TODO: keep only custom node ids in nodesToAdd element and move templates to parameters definition API
        case (id, uiObjectDefinition) if customTransformerAdditionalData(id).manyInputs => NodeToAdd("customNode", id,
          node.Join("", if (uiObjectDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(id, uiObjectDefinition), List.empty),
          filterCategories(uiObjectDefinition), objDefBranchParams(id, uiObjectDefinition))
        case (id, uiObjectDefinition) if !customTransformerAdditionalData(id).canBeEnding => NodeToAdd("customNode", id,
          CustomNode("", if (uiObjectDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(id, uiObjectDefinition)), filterCategories(uiObjectDefinition))
      }.toList
    )

    val optionalEndingCustomTransformers = NodeGroup("optionalEndingCustom",
      processDefinition.customStreamTransformers.collect {
        case (id, uiObjectDefinition) if customTransformerAdditionalData(id).canBeEnding => NodeToAdd("customNode", id,
          CustomNode("", if (uiObjectDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(id, uiObjectDefinition)), filterCategories(uiObjectDefinition))
      }.toList
    )

    val sinks = NodeGroup("sinks",
      processDefinition.sinkFactories.map {
        case (id, uiObjectDefinition) => NodeToAdd("sink", id,
          Sink("", SinkRef(id, objDefParams(id, uiObjectDefinition)),
            if (sinkAdditionalData(id).requiresOutput) Some(Expression("spel", "#input")) else None), filterCategories(uiObjectDefinition)
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
      NodeGroup("fragmentDefinition", List(
        NodeToAdd("input", "input", SubprocessInputDefinition("", List()), readCategories),
        NodeToAdd("output", "output", SubprocessOutputDefinition("", "output", List.empty), readCategories)
      ))
    }

    //so far we don't allow nested subprocesses...
    val subprocesses = if (!isSubprocess) {
      List(
        NodeGroup("fragments",
          processDefinition.subprocessInputs.map {
            case (id, definition) =>
              val nodes = EvaluatedParameterPreparer.prepareEvaluatedParameter(definition.parameters)
              NodeToAdd("fragments", id, SubprocessInput("", SubprocessRef(id, nodes)), readCategories.intersect(definition.categories))
          }.toList))
    } else {
      List.empty
    }

    // return none if component group should be hidden
    def getComponentGroup(componentName: String, baseComponentGroup: String): Option[String] = {
      val groupName = nodesConfig.get(componentName).flatMap(_.category).getOrElse(baseComponentGroup)
      componentsGroupMapping.getOrElse(groupName, Some(groupName))
    }

    val virtualComponentGroups = List(
      List(inputs),
      List(base),
      List(enrichers, customTransformers) ++ subprocesses,
      List(services, optionalEndingCustomTransformers, sinks))

    virtualComponentGroups
      .zipWithIndex
      .flatMap {
        case (groups, virtualGroupIndex) =>
          for {
            group <- groups
            node <- group.possibleNodes
            notHiddenComponentGroup <- getComponentGroup(node.label, group.name)
          } yield (virtualGroupIndex, notHiddenComponentGroup, node)
      }
      .groupBy {
        case (virtualGroupIndex, componentGroupName, _) => (virtualGroupIndex, componentGroupName)
      }
      .mapValues(v => v.map(e => e._3))
      .toList
      .sortBy {
        case ((virtualGroupIndex, componentGroupName), _) => (virtualGroupIndex, componentGroupName.toLowerCase)
      }
      // we need to merge nodes in the same category but in other virtual group
      .foldLeft(ListMap.empty[String, List[NodeToAdd]]) {
        case (acc, ((_, categoryName), elements)) =>
          val accElements = acc.getOrElse(categoryName, List.empty) ++ elements
          acc + (categoryName -> accElements)
      }
      .toList
      .map {
        case (componentGroupName, elements: List[NodeToAdd]) => SortedNodeGroup(componentGroupName, elements)
      }
  }

  def prepareEdgeTypes(user: LoggedUser, processDefinition: ProcessDefinition[ObjectDefinition],
                       isSubprocess: Boolean, subprocessesDetails: Set[SubprocessDetails]): List[NodeEdges] = {

    val subprocessOutputs = if (isSubprocess) List() else subprocessesDetails.map(_.canonical).map { process =>
      val outputs = ProcessConverter.findNodes(process).collect {
        case SubprocessOutputDefinition(_, name, _, _) => name
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
