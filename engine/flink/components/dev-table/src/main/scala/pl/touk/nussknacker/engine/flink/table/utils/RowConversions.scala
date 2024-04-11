package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.{ApiExpression, DataTypes, Schema}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.flink.table.ColumnDefinition

object RowConversions {

  import scala.jdk.CollectionConverters._

  def rowToMap(row: Row): java.util.Map[String, Any] = {
    val fieldNames = row.getFieldNames(true).asScala
    val fields     = fieldNames.map(n => n -> row.getField(n)).toMap
    new java.util.HashMap[String, Any](fields.asJava)
  }

  def mapToRowUnsafe(map: java.util.Map[String, Any], columns: List[ColumnDefinition]): Row = {
    val row = Row.withNames()
    columns.foreach(c => row.setField(c.columnName, map.get(c.columnName)))
    row
  }

}

object NestedRowConversions {

  import scala.jdk.CollectionConverters._

  private val NestedRowColumnDefaultFlinkGivenName = "f0"

  def columnsToSingleRowFlinkSchema(columns: List[ColumnDefinition]): Schema = {
    val fields: java.util.List[DataTypes.Field] =
      columns.map(c => DataTypes.FIELD(c.columnName, c.flinkDataType)).asJava
    val row = DataTypes.ROW(fields)
    Schema
      .newBuilder()
      .column(
        NestedRowColumnDefaultFlinkGivenName,
        row
      )
      .build()
  }

  def flatteningSelectExpressions(columns: List[ColumnDefinition]): List[ApiExpression] =
    columns.map(c => $(NestedRowColumnDefaultFlinkGivenName).get(c.columnName).as(c.columnName))

}
