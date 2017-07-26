package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.param
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.ui.api.DefinitionPreparer.NodeEdges
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.EdgeType
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.EdgeType.{FilterFalse, FilterTrue}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.SubprocessRepository
import pl.touk.nussknacker.ui.process.uiconfig.defaults.{ParameterDefaultValueExtractorStrategy, ParameterEvaluatorExtractor}
import pl.touk.nussknacker.ui.security.LoggedUser
import pl.touk.nussknacker.ui.util.EspPathMatchers
import pl.touk.http.argonaut.Argonaut62Support

import scala.concurrent.ExecutionContext
import scala.runtime.BoxedUnit

class DefinitionResources(processDefinition: Map[ProcessingType, ProcessDefinition[ObjectDefinition]],
                          subprocessRepository: SubprocessRepository,
                          parameterDefaultValueExtractorStrategyFactory: ParameterDefaultValueExtractorStrategy)
                         (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with EspPathMatchers  with RouteWithUser {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser) : Route =
    path("processDefinitionData" / EnumSegment(ProcessingType)) { (processingType) =>
      parameter('isSubprocess.as[Boolean]) { (isSubprocess) =>
        get {
          complete {
            val chosenProcessDefinition = processDefinition(processingType)
            ProcessObjects(DefinitionPreparer.prepareNodesToAdd(user = user, processDefinition = chosenProcessDefinition,
              isSubprocess = isSubprocess, subprocessRepo = subprocessRepository, extractorFactory=parameterDefaultValueExtractorStrategyFactory),
              chosenProcessDefinition,
              DefinitionPreparer.prepareEdgeTypes(user, chosenProcessDefinition, isSubprocess, subprocessRepository))
          }
        }
      }
    }

}

case class ProcessObjects(nodesToAdd: List[NodeGroup],
                          processDefinition: ProcessDefinition[ObjectDefinition],
                          edgesForNodes: List[NodeEdges])

case class NodeToAdd(`type`: String, label: String, node: NodeData, categories: List[String])

object SortedNodeGroup {
  def apply(name: String, possibleNodes: List[NodeToAdd]): NodeGroup = NodeGroup(name, possibleNodes.sortBy(_.label))
}

case class NodeGroup(name: String, possibleNodes: List[NodeToAdd])
case class NodeDefinition(id: String, parameters: List[DefinitionExtractor.Parameter])

//TODO: some refactoring?
object DefinitionPreparer {

  def prepareNodesToAdd(user: LoggedUser, processDefinition: ProcessDefinition[ObjectDefinition],
                        isSubprocess: Boolean, subprocessRepo: SubprocessRepository,
                        extractorFactory: ParameterDefaultValueExtractorStrategy): List[NodeGroup] = {
    val evaluator = new ParameterEvaluatorExtractor(extractorFactory)

    def filterCategories(objectDefinition: ObjectDefinition) = user.categories.intersect(objectDefinition.categories)

    def objDefParams(nodeDefinition: NodeDefinition): List[Parameter] = evaluator.evaluateParameters( nodeDefinition)

    def serviceRef(id: String, objDefinition: ObjectDefinition) = ServiceRef(id, objDefParams(NodeDefinition(id, objDefinition.parameters)))

    val returnsUnit = ((id: String, objectDefinition: ObjectDefinition)
    => objectDefinition.returnType.refClazzName == classOf[BoxedUnit].getName).tupled

    val base = SortedNodeGroup("base", List(
      NodeToAdd("filter", "Filter", Filter("", Expression("spel", "true")), user.categories),
      NodeToAdd("split", "Split", Split(""), user.categories),
      NodeToAdd("switch", "Switch", Switch("", Expression("spel", "true"), "output"), user.categories),
      NodeToAdd("variable", "Variable", Variable("", "varName", Expression("spel", "'value'")), user.categories)
    ))
    val services = SortedNodeGroup("services",
      processDefinition.services.filter(returnsUnit).map {
        case (id, objDefinition) => NodeToAdd("processor", id,
          Processor("", serviceRef(id, objDefinition)), filterCategories(objDefinition))
      }.toList
    )

    val enrichers = SortedNodeGroup("enrichers",
      processDefinition.services.filterNot(returnsUnit).map {
        case (id, objDefinition) => NodeToAdd("enricher", id,
          Enricher("", serviceRef(id, objDefinition), "output"), filterCategories(objDefinition))
      }.toList
    )

    val customTransformers = SortedNodeGroup("custom",
      processDefinition.customStreamTransformers.map {
        case (id, (objDefinition, _)) => NodeToAdd("customNode", id,
          CustomNode("", if (objDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(NodeDefinition(id,objDefinition.parameters))), filterCategories(objDefinition))
      }.toList
    )

    val subprocessDependent = if (!isSubprocess) {
      List(
        SortedNodeGroup("sinks",
          processDefinition.sinkFactories.map {
            case (id, objDefinition) => NodeToAdd("sink", id,
              Sink("", SinkRef(id, objDefinition.parameters.map(p => param.Parameter(p.name, "TODO"))),
                Some(Expression("spel", "#input"))), filterCategories(objDefinition)
            )
          }.toList),
        SortedNodeGroup("sources",
          processDefinition.sourceFactories.map {
            case (id, objDefinition) => NodeToAdd("source", id,
              Source("", SourceRef(id, objDefinition.parameters.map(p => param.Parameter(p.name, "TODO")))),
              filterCategories(objDefinition)
            )
          }.toList
        ),
        //so far we don't allow nested subprocesses...
        SortedNodeGroup("subprocesses",
          subprocessRepo.loadSubprocesses().collect {
            case CanonicalProcess(MetaData(id, _, _, _), _, FlatNode(SubprocessInputDefinition(_, parameters, _)) :: _) => NodeToAdd("subprocess", id,
              SubprocessInput("", SubprocessRef(id,
                //FIXME: categories
                evaluator.evaluateParameters(NodeDefinition(id, parameters)))), user.categories)
          }.toList
        )
      )
    } else {
      List(
        SortedNodeGroup("subprocessDefinition", List(
          NodeToAdd("input", "input", SubprocessInputDefinition("", List()), user.categories),
          NodeToAdd("output", "output", SubprocessOutputDefinition("", "output"), user.categories)
        )))
    }

    List(base, services, enrichers, customTransformers) ++ subprocessDependent
  }

  def prepareEdgeTypes(user: LoggedUser, processDefinition: ProcessDefinition[ObjectDefinition],
                       isSubprocess: Boolean, subprocessRepo: SubprocessRepository): List[NodeEdges] = {

    val subprocessOutputs = if (isSubprocess) List() else subprocessRepo.loadSubprocesses().map { process =>
      val outputs = ProcessConverter.findNodes(process).collect {
        case SubprocessOutputDefinition(_, name, _) => name
      }
      //TODO: enable choice of output type
      NodeEdges(NodeTypeId("SubprocessInput", Some(process.metaData.id)), outputs.map(EdgeType.SubprocessOutput), canChooseNodes = false)
    }

    List(
      NodeEdges(NodeTypeId("Split"), List(), canChooseNodes = true),
      NodeEdges(NodeTypeId("Switch"), List(
        EdgeType.NextSwitch(Expression("spel", "true")), EdgeType.SwitchDefault), canChooseNodes = true),
      NodeEdges(NodeTypeId("Filter"), List(FilterTrue, FilterFalse), canChooseNodes = false)
    ) ++ subprocessOutputs


  }

  case class NodeTypeId(`type`: String, id: Option[String] = None)

  case class NodeEdges(nodeId: NodeTypeId, edges: List[EdgeType], canChooseNodes: Boolean)

}
