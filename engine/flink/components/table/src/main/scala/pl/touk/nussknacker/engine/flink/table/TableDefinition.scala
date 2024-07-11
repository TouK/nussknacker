package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.api.{DataTypes, Schema}
import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import scala.jdk.CollectionConverters._

final case class TableDefinition(tableName: String, typingResult: TypingResult, columns: List[ColumnDefinition]) {

  def toFlinkSchema: Schema = {
    val cols = columns.map(c => DataTypes.FIELD(c.columnName, c.flinkDataType)).asJava
    Schema.newBuilder().fromRowDataType(DataTypes.ROW(cols)).build()
  }

}

final case class ColumnDefinition(columnName: String, typingResult: TypingResult, flinkDataType: DataType)
