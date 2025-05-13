package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.migration.{NodeMigration, ParametersMigration}

object SpelToSpelTemplateNodeMigration extends NodeMigration {
  private lazy val spelStringPattern   = "^'(.*)'$".r
  private lazy val parametersMigration = new ParametersMigration(mapStringParameters)

  override def description: String = "Migrate empty text fields in Spel to SpelTemplate"

  override def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] =
    parametersMigration.migrateNode(metaData)

  private def mapStringParameters: PartialFunction[Parameter, Parameter] = (param: Parameter) => {
    param.expression.expression match {
      case spelStringPattern(rawString) => Parameter(param.name, Expression(Language.SpelTemplate, rawString))
    }
  }

}
