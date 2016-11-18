package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives
import argonaut.{EncodeJson, Json, PrettyParams}
import pl.touk.esp.engine.definition.DefinitionExtractor
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.evaluatedparam.Parameter
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.ui.process.marshall.DisplayableProcessCodec
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.ExecutionContext
import scala.runtime.BoxedUnit

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
          ProcessObjects(DefinitionPreparer.prepareNodesToAdd(user, processDefinition), processDefinition)
        }
      }
    }

}

//TODO: dalsze czesci? co tu w sumie moze byc??
case class ProcessObjects(nodesToAdd: List[NodeGroup], processDefinition: ProcessDefinition[ObjectDefinition])

case class NodeToAdd(`type`: String, label: String, node: NodeData, categories: List[String])

case class NodeGroup(name: String, possibleNodes: List[NodeToAdd])

//TODO: czy to da sie ladniej?
object DefinitionPreparer {

  def prepareNodesToAdd(user:LoggedUser, processDefinition: ProcessDefinition[ObjectDefinition]): List[NodeGroup] = {

    def filterCategories(objectDefinition: ObjectDefinition) = user.categories.intersect(objectDefinition.categories)

    def serviceRef(id: String, objDefinition: ObjectDefinition) = ServiceRef(id, objDefinition.parameters.map(mapDefinitionParamToEvaluatedParam))

    val returnsUnit = ((id: String, objectDefinition: ObjectDefinition)
      => objectDefinition.returnType.refClazzName == classOf[BoxedUnit].getName).tupled

    val base = NodeGroup("base", List(
      NodeToAdd("filter", "Filter", Filter("", Expression("spel", "true")), user.categories),
      NodeToAdd("split", "Split", Split(""), user.categories)
      //TODO: jak robic VariableBuilder??
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
        case (id, objDefinition) => NodeToAdd("customNode", id,
          CustomNode("", "outputVar", id, objDefinition.parameters.map(mapDefinitionParamToEvaluatedParam)), filterCategories(objDefinition))
      }.toList
    )

    //TODO: sink, source, switch...
    List(base, services, enrichers, customTransformers)
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
