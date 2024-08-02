package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.catalog.ResolvedSchema
import org.apache.flink.table.types.DataType

final case class TableDefinition(
    tableName: String,
    sourceRowDataType: DataType,
    physicalRowDataType: DataType,
    sinkRowDataType: DataType
)

object TableDefinition {

  def apply(tableName: String, schema: ResolvedSchema): TableDefinition = {
    // We can't store full ResolvedSchema in definition because it is not serializable but our Components holding this information have to be serializable
    TableDefinition(
      tableName,
      schema.toSourceRowDataType,
      schema.toPhysicalRowDataType,
      schema.toSinkRowDataType
    )
  }

}
