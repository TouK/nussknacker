package pl.touk.nussknacker.engine.flink.table.io.definition.discovery

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.catalog.ObjectIdentifier
import pl.touk.nussknacker.engine.flink.table.io.definition.{FlinkDataDefinition, TableDefinition}
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinition.DataDefinitionRegistrationResultExtension
import pl.touk.nussknacker.engine.flink.table.io.definition.discovery.CatalogDiscovery.discoverCatalog

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.{Failure, Success, Try}

class TableDiscoveryImpl(databasesOmitList: List[String]) extends TableDiscovery with LazyLogging {

  def discoverTableIdentifiers(
      flinkDataDefinition: FlinkDataDefinition,
      env: TableEnvironment
  ): List[ObjectIdentifier] = {
    flinkDataDefinition.registerIn(env).orFail
    for {
      catalogName  <- env.listCatalogs().toList
      catalog      <- env.getCatalog(catalogName).toScala.toList
      databaseName <- catalog.listDatabases.asScala.toList.filterNot(db => databasesOmitList.contains(db))
      tableName    <- listTableNamesSafe(env, catalogName, databaseName)
    } yield ObjectIdentifier.of(catalogName, databaseName, tableName)
  }

  def discoverTable(
      env: TableEnvironment,
      flinkDataDefinition: FlinkDataDefinition,
      tableId: ObjectIdentifier
  ): TableDefinition = {
    flinkDataDefinition.registerIn(env).orFail
    val table = Try(env.from(tableId.toString)).getOrElse(
      throw new IllegalStateException(s"Failed to extract table for id: $tableId")
    )
    val catalog = discoverCatalog(env, flinkDataDefinition, tableId.getCatalogName)
    val catalogBaseTable = Try(catalog.getTable(tableId.toObjectPath))
      .getOrElse(
        throw new IllegalStateException(s"Failed to extract table for id: $tableId")
      )
    TableDefinition(tableId, table, catalogBaseTable)
  }

  private def listTableNamesSafe(env: TableEnvironment, catalogName: String, databaseName: String): List[String] = {
    Try {
      env.listTables(catalogName, databaseName)
    } match {
      // This fails for example when user has no access to a database like an internal Aiven Postgres `_aiven` db
      case Failure(exception) =>
        logger.warn(
          s"Could not list tables from catalog [$catalogName] under database [$databaseName]",
          exception
        )
        List.empty
      case Success(tableList) =>
        tableList.toList
    }
  }

}
