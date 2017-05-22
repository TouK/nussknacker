package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.esp.engine.definition.DefinitionExtractor
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.evaluatedparam.Parameter
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.param
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.subprocess.SubprocessRef
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.esp.ui.process.displayedgraph.displayablenode.EdgeType
import pl.touk.esp.ui.process.subprocess.SubprocessRepository
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.EspPathMatchers
import pl.touk.http.argonaut.Argonaut62Support

import scala.concurrent.ExecutionContext
import scala.runtime.BoxedUnit

class DefinitionResources(processDefinition: Map[ProcessingType, ProcessDefinition[ObjectDefinition]], subprocessRepository: SubprocessRepository)
                         (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with EspPathMatchers {

  import argonaut.ArgonautShapeless._
  import pl.touk.esp.ui.codec.UiCodecs._

  val route = (user: LoggedUser) =>
    path("processDefinitionData" / EnumSegment(ProcessingType)) { (processingType) =>
      parameter('isSubprocess) { (isSubprocess) =>
        get {
          complete {
            val chosenProcessDefinition = processDefinition(processingType)
            ProcessObjects(DefinitionPreparer.prepareNodesToAdd(user, chosenProcessDefinition,
              java.lang.Boolean.valueOf(isSubprocess), subprocessRepository), chosenProcessDefinition, DefinitionPreparer.prepareEdgeTypes())
          }
        }
      }
    }

}

//TODO: dalsze czesci? co tu w sumie moze byc??
case class ProcessObjects(nodesToAdd: List[NodeGroup], processDefinition: ProcessDefinition[ObjectDefinition], edgeTypes: Map[String, EdgeType])

case class NodeToAdd(`type`: String, label: String, node: NodeData, categories: List[String])

object SortedNodeGroup {
  def apply(name: String, possibleNodes: List[NodeToAdd]): NodeGroup = NodeGroup(name, possibleNodes.sortBy(_.label))
}

case class NodeGroup(name: String, possibleNodes: List[NodeToAdd])

//TODO: czy to da sie ladniej?
object DefinitionPreparer {

  def prepareNodesToAdd(user: LoggedUser, processDefinition: ProcessDefinition[ObjectDefinition],
                        isSubprocess: Boolean, subprocessRepo: SubprocessRepository): List[NodeGroup] = {

    def filterCategories(objectDefinition: ObjectDefinition) = user.categories.intersect(objectDefinition.categories)

    def objDefParams(objDefinition: ObjectDefinition) = objDefinition.parameters.map(mapDefinitionParamToEvaluatedParam)

    def serviceRef(id: String, objDefinition: ObjectDefinition) = ServiceRef(id, objDefParams(objDefinition))

    val returnsUnit = ((id: String, objectDefinition: ObjectDefinition)
    => objectDefinition.returnType.refClazzName == classOf[BoxedUnit].getName).tupled

    val base = SortedNodeGroup("base", List(
      NodeToAdd("filter", "Filter", Filter("", Expression("spel", "true")), user.categories),
      NodeToAdd("split", "Split", Split(""), user.categories),
      NodeToAdd("switch", "Switch", Switch("", Expression("spel", "true"), "output"), user.categories),
      NodeToAdd("variable", "Variable", Variable("", "varName", Expression("spel", "'value'")), user.categories)
      //TODO: jak robic VariableBuilder??
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
        case (id, objDefinition) => NodeToAdd("customNode", id,
          CustomNode("", if (objDefinition.hasNoReturn) None else Some("outputVar"), id, objDefParams(objDefinition)), filterCategories(objDefinition))
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
                //FIXME: kategorie
                parameters.map(mapDefinitionParamToEvaluatedParam))), user.categories)
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

  private def mapDefinitionParamToEvaluatedParam(param: DefinitionExtractor.Parameter)

  = {
    val defaultExpression = param.typ.refClazzName match {
      case "long" | "short" | "int" | "java.lang.Number" => "0"
      case "float" | "double" | "java.math.BigDecimal" => "0.0"
      case "boolean" => "true"
      case "java.lang.String" => "''"
      case "java.util.List" => "{}"
      case "java.util.Map" => "{:}"
      case _ => s"#${param.name}"
    }
    //TODO: tu beda jeszcze stale!
    Parameter(param.name, Expression("spel", defaultExpression))
  }

  def prepareEdgeTypes(): Map[String, EdgeType] = {
    Map(
      "SwitchDefault" -> EdgeType.SwitchDefault,
      "NextSwitch" -> EdgeType.NextSwitch(Expression("spel", "true"))
    )
  }
}
