package pl.touk.nussknacker.engine.flink.table.extractor

import org.apache.flink.table.api.Table
import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

object TypeExtractor {

  import scala.jdk.CollectionConverters._

  private case class ColumnTypingResult(columnName: String, typingResult: TypingResult)

  def extractTypedSchema(tableFromEnv: Table): Schema = {
    val (columns, columnsTypingData) = tableFromEnv.getResolvedSchema.getColumns.asScala
      .map { column =>
        val name     = column.getName
        val dataType = column.getDataType
        (Column(name, dataType), ColumnTypingResult(name, flinkTypeToTypingResult(dataType)))
      }
      .toList
      .unzip
    Schema(columns, typedColumnsToRecordTypingResult(columnsTypingData))
  }

  // TODO: handle complex types like maps, lists, rows, raws
  private def flinkTypeToTypingResult(dataType: DataType) =
    Typed.typedClass(dataType.getLogicalType.getDefaultConversion)

  private def typedColumnsToRecordTypingResult(columns: List[ColumnTypingResult]) =
    Typed.record(columns.map(c => c.columnName -> c.typingResult).toMap)

}
