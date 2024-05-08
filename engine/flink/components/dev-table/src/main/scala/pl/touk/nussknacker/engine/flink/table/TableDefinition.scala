package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}

final case class TableDefinition(
    tableName: String,
    typingResult: TypedObjectTypingResult,
    columns: List[ColumnDefinition]
) {

  def typingResultWithRequiredFieldsOnly: TypingResult = {
    val nonPhysicalColumnNames = columns.filterNot(_.isPhysical).map(_.columnName)
    typingResult.copy(
      typingResult.fields.filterNot { case (fieldName, _) => nonPhysicalColumnNames.contains(fieldName) }
    )
  }

  def physicalColumns: List[ColumnDefinition] = columns.filter(_.isPhysical)
}

final case class ColumnDefinition(
    columnName: String,
    typingResult: TypingResult,
    flinkDataType: DataType,
    isPhysical: Boolean
)
