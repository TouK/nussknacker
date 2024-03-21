package pl.touk.nussknacker.engine.flink.table.utils

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.flink.table.TableDefinition

object TableComponentFactory {

  val tableNameParamName: ParameterName = ParameterName("Table")

  def buildTableNameParam(
      defs: List[TableDefinition]
  ): ParameterExtractor[String] with ParameterCreatorWithNoDependency = {
    val possibleTableParamValues = defs.map(c => FixedExpressionValue(s"'${c.tableName}'", c.tableName))
    ParameterDeclaration
      .mandatory[String](tableNameParamName)
      .withCreator(
        modify = _.copy(editor =
          Some(FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: possibleTableParamValues))
        )
      )
  }

  def getSelectedTableUnsafe(
      tableName: String,
      configs: List[TableDefinition]
  ): TableDefinition =
    configs
      .find(_.tableName == tableName)
      .getOrElse(throw new IllegalStateException("Table with selected name not found."))

}
