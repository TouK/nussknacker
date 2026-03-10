package pl.touk.nussknacker.engine.flink.table.io.definition.discovery

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.catalog.ObjectIdentifier
import pl.touk.nussknacker.engine.flink.table.io.definition.{FlinkDataDefinition, TableDefinition}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class CachingTableDiscovery(cacheTTL: FiniteDuration, delegate: TableDiscovery) extends TableDiscovery {

  private val tableIdentifiersCache: Cache[FlinkDataDefinition, List[ObjectIdentifier]] =
    Caffeine
      .newBuilder()
      .expireAfterWrite(cacheTTL.toMillis, TimeUnit.MILLISECONDS)
      .build()

  private val tableDefinitionsCache: Cache[(ObjectIdentifier, FlinkDataDefinition), TableDefinition] =
    Caffeine
      .newBuilder()
      .expireAfterWrite(cacheTTL.toMillis, TimeUnit.MILLISECONDS)
      .build()

  def discoverTableIdentifiers(
      flinkDataDefinition: FlinkDataDefinition,
      env: TableEnvironment
  ): List[ObjectIdentifier] =
    tableIdentifiersCache.get(
      flinkDataDefinition,
      _ => delegate.discoverTableIdentifiers(flinkDataDefinition, env)
    )

  override def discoverTable(
      env: TableEnvironment,
      flinkDataDefinition: FlinkDataDefinition,
      tableId: ObjectIdentifier
  ): TableDefinition =
    tableDefinitionsCache.get(
      (tableId, flinkDataDefinition),
      _ => delegate.discoverTable(env, flinkDataDefinition, tableId)
    )

}
