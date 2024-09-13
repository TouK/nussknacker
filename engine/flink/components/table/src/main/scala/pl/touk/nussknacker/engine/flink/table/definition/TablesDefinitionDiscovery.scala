package pl.touk.nussknacker.engine.flink.table.definition

import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.{EnvironmentSettings, TableEnvironment}
import org.apache.flink.table.catalog.ObjectIdentifier
import pl.touk.nussknacker.engine.flink.table.TableDefinition

import scala.jdk.OptionConverters.RichOptional
import scala.util.Try

// TODO: Make this extractor more memory/cpu efficient and ensure closing of resources. For more details see
// https://github.com/TouK/nussknacker/pull/5627#discussion_r1512881038
class TablesDefinitionDiscovery(tableEnv: TableEnvironment) extends LazyLogging {

  import scala.jdk.CollectionConverters._

  def listTables: List[TableDefinition] = {
    for {
      catalogName  <- tableEnv.listCatalogs().toList
      catalog      <- tableEnv.getCatalog(catalogName).toScala.toList
      databaseName <- catalog.listDatabases.asScala.toList
      tableName    <- tableEnv.listTables(catalogName, databaseName).toList
      tableId = ObjectIdentifier.of(catalogName, databaseName, tableName)
    } yield extractTableDefinition(tableId)
  }

  private def extractTableDefinition(tableId: ObjectIdentifier) = {
    val table = Try(tableEnv.from(tableId.toString)).fold(
      ex => throw new IllegalStateException(s"Table extractor could not locate a created table with id: $tableId", ex),
      identity
    )
    TableDefinition(tableId, table.getResolvedSchema)
  }

}

object TablesDefinitionDiscovery {

  def prepareDiscovery(
      flinkDataDefinition: FlinkDataDefinition
  ): ValidatedNel[DataDefinitionRegistrationError, TablesDefinitionDiscovery] = {
    val environmentSettings = EnvironmentSettings
      .newInstance()
      .build()
    prepareDiscovery(flinkDataDefinition, environmentSettings)
  }

  private[definition] def prepareDiscovery(
      flinkDataDefinition: FlinkDataDefinition,
      environmentSettings: EnvironmentSettings
  ): ValidatedNel[DataDefinitionRegistrationError, TablesDefinitionDiscovery] = {
    val tableEnv = TableEnvironment.create(environmentSettings)
    flinkDataDefinition.registerIn(tableEnv).map(_ => new TablesDefinitionDiscovery(tableEnv))
  }

}
