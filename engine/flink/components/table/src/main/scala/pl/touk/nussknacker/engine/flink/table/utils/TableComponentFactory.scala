package pl.touk.nussknacker.engine.flink.table.utils

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.flink.table.TableDefinition

object TableComponentFactory {

  val tableNameParamName: ParameterName = ParameterName("Table")

  def buildTableNameParam(
      defs: List[TableDefinition]
  ): ParameterExtractor[String] with ParameterCreatorWithNoDependency = {
    // TODO: We should print tableName only when table is defined inside the default catalog and the default database
    //       For other cases, we should show database and catalog
    val possibleTableParamValues = defs.map(c => FixedExpressionValue(s"'${c.tableId}'", c.tableId.getObjectName))
    ParameterDeclaration
      .mandatory[String](tableNameParamName)
      .withCreator(
        modify = _.copy(editor =
          Some(FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: possibleTableParamValues))
        )
      )
  }

  def getSelectedTableUnsafe(
      // There is no easy way to create ObjectIdentifier from String and we don't want to add this type to Nu supported types so we pass String instead
      tableIdString: String,
      configs: List[TableDefinition]
  ): TableDefinition =
    configs
      .find(_.tableId.toString == tableIdString)
      .getOrElse(throw new IllegalStateException("Table with selected name not found."))

}
