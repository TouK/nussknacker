package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.table.catalog.ResolvedSchema
import org.apache.flink.table.types.DataType

final case class TableDefinition(tableName: String, schema: ResolvedSchema, boundedness: Boundedness) {

  lazy val sourceRowDataType: DataType = schema.toSourceRowDataType

  lazy val sinkRowDataType: DataType = schema.toSinkRowDataType

}
