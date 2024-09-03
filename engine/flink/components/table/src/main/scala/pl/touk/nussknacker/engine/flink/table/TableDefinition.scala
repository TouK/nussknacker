package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.catalog.{ObjectIdentifier, ResolvedSchema}
import org.apache.flink.table.types.DataType

// We need to use ResolvedSchema instead of (unresolved) Schema because we need to know the type
// of computed columns in sources. UnresolvedComputedColumn holds unresolved Expression which unknown type.
// After expression resolution, the type is determined.
final case class TableDefinition(tableId: ObjectIdentifier, schema: ResolvedSchema) {

  // FIXME: Check places of usage and replace with tableId
  def tableName: String = tableId.toString

  lazy val sourceRowDataType: DataType = schema.toSourceRowDataType

  lazy val sinkRowDataType: DataType = schema.toSinkRowDataType

}
