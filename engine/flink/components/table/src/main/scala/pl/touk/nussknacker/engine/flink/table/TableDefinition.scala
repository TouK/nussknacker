package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.catalog.ResolvedSchema
import org.apache.flink.table.types.DataType

// We need to use ResolvedSchema instead of (unresolved) Schema because we need to know the type
// of computed columns in sources. UnresolvedComputedColumn holds unresolved Expression which unknown type.
// After expression resolution, the type is determined.
final case class TableDefinition(tableName: String, schema: ResolvedSchema) {

  lazy val sourceRowDataType: DataType = schema.toSourceRowDataType

  lazy val sinkRowDataType: DataType = schema.toSinkRowDataType

}
