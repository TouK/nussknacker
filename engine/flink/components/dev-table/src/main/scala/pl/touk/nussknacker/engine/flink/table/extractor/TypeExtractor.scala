package pl.touk.nussknacker.engine.flink.table.extractor

import org.apache.flink.table.api.Table
import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.table.ColumnDefinition

object TypeExtractor {

  import scala.jdk.CollectionConverters._

  final case class TableTypingResult(typingResult: typing.TypedObjectTypingResult, columns: List[ColumnDefinition])

  def extractTypingResult(tableFromEnv: Table): TableTypingResult = {
    val columnsTypingData = tableFromEnv.getResolvedSchema.getColumns.asScala.map { column =>
      ColumnDefinition(
        columnName = column.getName,
        typingResult = flinkTypeToTypingResult(column.getDataType),
        flinkDataType = column.getDataType,
        isPhysical = column.isPhysical
      )
    }.toList
    TableTypingResult(typedColumnsToRecordTypingResult(columnsTypingData), columnsTypingData)
  }

  // TODO: handle complex types like maps, lists, rows, raws
  private def flinkTypeToTypingResult(dataType: DataType) =
    Typed.typedClass(dataType.getLogicalType.getDefaultConversion)

  private def typedColumnsToRecordTypingResult(columns: List[ColumnDefinition]) =
    Typed.record(columns.map(c => c.columnName -> c.typingResult).toMap)

}
