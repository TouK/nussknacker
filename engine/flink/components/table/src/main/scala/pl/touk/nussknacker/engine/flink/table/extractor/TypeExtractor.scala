package pl.touk.nussknacker.engine.flink.table.extractor

import org.apache.flink.table.api.Table
import org.apache.flink.table.types.DataType
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.table.ColumnDefinition

object TypeExtractor {

  import scala.jdk.CollectionConverters._

  final case class TableTypingResult(typingResult: TypingResult, columns: List[ColumnDefinition])

  def extractTypingResult(tableFromEnv: Table): TableTypingResult = {
    val columnsTypingData = tableFromEnv.getResolvedSchema.getColumns.asScala.map { column =>
      ColumnDefinition(column.getName, flinkTypeToTypingResult(column.getDataType), column.getDataType)
    }.toList
    TableTypingResult(typedColumnsToRecordTypingResult(columnsTypingData), columnsTypingData)
  }

  // TODO: handle complex types like maps, lists, rows, raws and types alignment
  private def flinkTypeToTypingResult(dataType: DataType) =
    Typed.typedClass(dataType.getLogicalType.getDefaultConversion)

  private def typedColumnsToRecordTypingResult(columns: List[ColumnDefinition]) =
    Typed.record(columns.map(c => c.columnName -> c.typingResult).toMap, Typed.typedClass[Row])

}
