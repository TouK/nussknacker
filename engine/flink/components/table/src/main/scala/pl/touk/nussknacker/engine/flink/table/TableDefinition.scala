package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

final case class TableDefinition(tableName: String, typingResult: TypingResult, columns: List[ColumnDefinition])
final case class ColumnDefinition(columnName: String, typingResult: TypingResult, flinkDataType: DataType)
