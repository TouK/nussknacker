package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.catalog.Column.ComputedColumn
import org.apache.flink.table.catalog.{Column, ResolvedSchema}
import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.utils.DataTypeUtils

import scala.jdk.CollectionConverters._

final case class TableDefinition(
    tableName: String,
    // We can't store full ResolvedSchema in definition because it is not serializable but our Components holding this information have to be serializable
    columns: List[Column]
) {

  // FIXME: use data types locally
  lazy val sourceRowDataType: DataType = {
    DataTypes.ROW(
      columns
        .map(c => DataTypes.FIELD(c.getName, DataTypeUtils.removeTimeAttribute(c.getDataType))): _*
    )
  }

  // This is copy-paste of toSourceRowDataType with filtering-out of computed columns.
  // This type is used for test data generators - we don't want user to provide computed columns
  lazy val sourceRowDataTypeWithoutComputedColumns: DataType = {
    DataTypes.ROW(
      columns
        .filterNot(c => c.isInstanceOf[ComputedColumn])
        .map(c => DataTypes.FIELD(c.getName, DataTypeUtils.removeTimeAttribute(c.getDataType))): _*
    )
  }

  lazy val sinkRowDataType: DataType = {
    DataTypes.ROW(
      columns
        .filter(_.isPersisted)
        .map(c => DataTypes.FIELD(c.getName, DataTypeUtils.removeTimeAttribute(c.getDataType))): _*
    )
  }

}

object TableDefinition {

  def apply(tableName: String, schema: ResolvedSchema): TableDefinition = {
    TableDefinition(
      tableName,
      schema.getColumns.asScala.toList
    )
  }

}
