package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.catalog.ResolvedSchema
import org.apache.flink.table.types.DataType

final case class TableDefinition(tableName: String, schema: ResolvedSchema) {

  lazy val sourceRowDataType: DataType = schema.toSourceRowDataType

  lazy val sinkRowDataType: DataType = schema.toSinkRowDataType

}
