package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{CustomNode, Enricher, Join, Processor, Sink, Source, WithParameters}
import pl.touk.nussknacker.engine.migration.NodeMigration

object SpelToSpelTemplateNodeMigration extends NodeMigration {
  private lazy val spelStringPattern = "^'(.*)'$".r

  override def description: String = "Migrate empty text fields in Spel to SpelTemplate"

  override def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] = {
    case node: WithParameters if node.parameters.exists(isStringSpelParameter) =>
      node match {
        case n: CustomNode => n.copy(parameters = n.parameters.map(mapStringParameters))
        case n: Enricher => n.copy(service = n.service.copy(parameters = n.service.parameters.map(mapStringParameters)))
        case n: Join     => n.copy(parameters = n.parameters.map(mapStringParameters))
        case n: Processor =>
          n.copy(service = n.service.copy(parameters = n.service.parameters.map(mapStringParameters)))
        case n: Sink   => n.copy(ref = n.ref.copy(parameters = n.ref.parameters.map(mapStringParameters)))
        case n: Source => n.copy(ref = n.ref.copy(parameters = n.ref.parameters.map(mapStringParameters)))
      }
  }

  private def isStringSpelParameter(param: Parameter): Boolean =
    param.expression.language == Language.Spel && spelStringPattern.findFirstIn(param.expression.expression).isDefined

  private def mapStringParameters(param: Parameter): Parameter = {
    param.expression.expression match {
      case spelStringPattern(rawString) => Parameter(param.name, Expression(Language.SpelTemplate, rawString))
      case _                            => param
    }
  }

}
