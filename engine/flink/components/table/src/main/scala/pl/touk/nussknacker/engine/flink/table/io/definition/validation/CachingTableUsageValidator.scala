package pl.touk.nussknacker.engine.flink.table.io.definition.validation

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.apache.flink.table.api.TableEnvironment
import pl.touk.nussknacker.engine.flink.table.io.definition._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class CachingTableUsageValidator(cacheTTL: FiniteDuration, delegate: TableUsageValidator) extends TableUsageValidator {

  private val cache: Cache[(TableDefinition, TableUseCase), ValidatedNel[FlinkDataDefinitionError, Unit]] =
    Caffeine
      .newBuilder()
      .expireAfterWrite(cacheTTL.toMillis, TimeUnit.MILLISECONDS)
      .build()

  /* Safety of caching table usage validation:
     - CREATE TABLE - can be cached fully (with errors) UNLESS there is an unexpected error like from concurrent
       operations on state underneath minicluster
     - CREATE CATALOG - can not cached because the validator does external calls for catalogs. Caching can be implemented
       for catalogs by using a flag that turns of external validations, though the validation would be very narrow -
       only checking if a connector is on classpath and if table from catalog can potentially be used as source or sink.
   */
  override def validateTableUsage(
      table: TableDefinition,
      tableUseCase: TableUseCase,
      env: TableEnvironment,
      flinkDataDefinition: FlinkDataDefinition
  ): Validated[NonEmptyList[FlinkDataDefinitionError], Unit] =
    cache.get(
      table -> tableUseCase,
      _ => delegate.validateTableUsage(table, tableUseCase, env, flinkDataDefinition)
    )

}
