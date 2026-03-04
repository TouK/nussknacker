package pl.touk.nussknacker.engine.flink.table.io.definition.discovery

import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.catalog.ObjectIdentifier
import pl.touk.nussknacker.engine.flink.table.io.definition.{FlinkDataDefinition, TableDefinition}

trait TableDiscovery {

  def discoverTableIdentifiers(flinkDataDefinition: FlinkDataDefinition, env: TableEnvironment): List[ObjectIdentifier]

  def discoverTable(
      env: TableEnvironment,
      flinkDataDefinition: FlinkDataDefinition,
      tableId: ObjectIdentifier
  ): TableDefinition

}
