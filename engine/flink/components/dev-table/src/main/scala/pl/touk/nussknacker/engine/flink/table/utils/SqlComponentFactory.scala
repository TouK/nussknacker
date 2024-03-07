package pl.touk.nussknacker.engine.flink.table.utils

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.flink.table.TableDefinition

object SqlComponentFactory {

  val TableNameParamName = "Table"

  def buildTableNameParam(defs: List[TableDefinition]): ParameterWithExtractor[String] = {
    val possibleTableParamValues =
      defs.map(c => FixedExpressionValue(s"'${c.tableName}'", c.tableName))
    val parameter = Parameter[String](
      name = TableNameParamName
    ).copy(editor = Some(FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: possibleTableParamValues)))
    ParameterWithExtractor(parameter)
  }

  def getSelectedTableUnsafe(
      tableName: String,
      configs: List[TableDefinition]
  ): TableDefinition =
    configs
      .find(_.tableName == tableName)
      .getOrElse(throw new IllegalStateException("Table with selected name not found."))

}
