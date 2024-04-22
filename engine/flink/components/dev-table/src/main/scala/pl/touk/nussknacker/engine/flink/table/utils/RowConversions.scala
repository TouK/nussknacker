package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.{DataTypes, Schema, Table}
import org.apache.flink.table.types.DataType
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

  /*
   This "f0" value is name given by flink at conversion of one element stream. For details read:
   https://nightlies.apache.org/flink/flink-docs-master/docs/dev/table/data_stream_api/.
   */
  private val NestedRowColumnDefaultFlinkGivenName = "f0"

  final case class ColumnFlinkSchema(columnName: String, flinkDataType: DataType)

  // TODO: avoid this step by mapping DataStream directly without this intermediate table with nested row
  def buildTableFromRowStream(
      tableEnv: StreamTableEnvironment,
      streamOfRows: SingleOutputStreamOperator[Row],
      columnSchema: List[ColumnFlinkSchema]
  ): Table = {
    val nestedRowSchema    = columnsToSingleRowFlinkSchema(columnSchema)
    val tableWithNestedRow = tableEnv.fromDataStream(streamOfRows, nestedRowSchema)
    val tableWithFlattenedRow = tableWithNestedRow.select(
      columnSchema.map(c => $(NestedRowColumnDefaultFlinkGivenName).get(c.columnName).as(c.columnName)): _*
    )
    tableWithFlattenedRow
  }

  private def columnsToSingleRowFlinkSchema(columns: List[ColumnFlinkSchema]): Schema = {
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

}
