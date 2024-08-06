package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.catalog.ResolvedSchema

final case class TableDefinition(tableName: String, schema: ResolvedSchema)
