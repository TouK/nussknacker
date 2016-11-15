package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives
import argonaut.{EncodeJson, Json, PrettyParams}
import pl.touk.esp.engine.definition.DefinitionExtractor
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.evaluatedparam.Parameter
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node.{CustomNode, Filter, NodeData, Processor}
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.ui.process.marshall.DisplayableProcessCodec
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.ExecutionContext

class DefinitionResources(processDefinition: ProcessDefinition[ObjectDefinition])
                         (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._

  implicit val displayableProcessDecode = DisplayableProcessCodec.codec

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  implicit val displayableProcessNodeEncoder = DisplayableProcessCodec.nodeEncoder
  implicit val processObjectsEncodeEncode = EncodeJson.of[ProcessObjects]

  val route = (user: LoggedUser) =>
    path("processDefinitionData") {
      get {
        complete {
          ProcessObjects(DefinitionPreparer.prepareNodesToAdd(processDefinition), processDefinition)
        }
      }
    }

}

//TODO: dalsze czesci? co tu w sumie moze byc??
case class ProcessObjects(nodesToAdd: List[NodeGroup], processDefinition: ProcessDefinition[ObjectDefinition])

case class NodeToAdd(`type`: String, label: String, node: NodeData)

case class NodeGroup(name: String, possibleNodes: List[NodeToAdd])

object DefinitionPreparer {

  def prepareNodesToAdd(processDefinition: ProcessDefinition[ObjectDefinition]): List[NodeGroup] = {

    val base = NodeGroup("base", List(
      NodeToAdd("filter", "Filter", Filter("", Expression("spel", "true")))

      //TODO: jak robic VariableBuilder??
    ))
    val services = NodeGroup("services",
      processDefinition.services.map {
        case (id, objDefinition) => NodeToAdd("processor", id,
          Processor("", ServiceRef(id, objDefinition.parameters.map(mapDefinitionParamToEvaluatedParam))))
      }.toList
    )

    val customTransformers = NodeGroup("custom",
      processDefinition.customStreamTransformers.map {
        case (id, objDefinition) => NodeToAdd("customNode", id,
          CustomNode("", "outputVar", id, objDefinition.parameters.map(mapDefinitionParamToEvaluatedParam)))
      }.toList
    )

    //TODO: sink, source, enricher, switch...
    List(base, services, customTransformers)
  }

  private def mapDefinitionParamToEvaluatedParam(param: DefinitionExtractor.Parameter) = {
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


}
