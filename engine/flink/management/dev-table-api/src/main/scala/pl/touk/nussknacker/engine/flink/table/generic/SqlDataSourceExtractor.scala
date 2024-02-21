package pl.touk.nussknacker.engine.flink.table.generic

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.{EnvironmentSettings, TableEnvironment}
import org.apache.flink.table.catalog.{CatalogBaseTable, ObjectPath}
import org.apache.flink.table.types.DataType

import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.jdk.CollectionConverters._
import scala.util.Try

// TODO local: write tests for this
object SqlDataSourceExtractor extends LazyLogging {

  private val defaultCatalogName = "default_catalog"
  private val defaultDatabase    = "default_database"
  private val connectorKey       = "connector"

  def extractTablesFromFlinkRuntime(createTableStatements: List[String]): List[DataSourceTable] = {
    val settings = EnvironmentSettings
      .newInstance()
      .build()
    val tableEnv = TableEnvironment.create(settings)

    val catalog = tableEnv.getCatalog(defaultCatalogName).asScala match {
      case Some(value) => value
      case None =>
        throw new IllegalStateException(
          "Default catalog was not found during parsing of sql for generic table components."
        )
    }

    // We don't know what's the name of the table outside of this context so we create a table, extract metadata from it
    // and drop it and do that for every statement
    // TODO local: table results show if adding table was successful - we may use this to handle failures
    val tableResults = createTableStatements.map { statement =>
      tableEnv.executeSql(statement)

      // TODO local: find if there is a simpler way to get granular table metadata like connector
      // Lower level api - Here we have access to unresolved schema and more metadata
      val (tableName, connectorName) = tableEnv.listTables().headOption match {
        case Some(tableName) =>
          val tablePath                                   = new ObjectPath(defaultDatabase, tableName)
          val tableWithUnresolvedSchema: CatalogBaseTable = catalog.getTable(tablePath)
          val connectorName = Try(tableWithUnresolvedSchema.getOptions.get(connectorKey))
            .getOrElse(throw new IllegalStateException(s"Table $tableName did not have connector specified."))
          tableName -> connectorName
        case None => throw new IllegalStateException()
      }

      // Higher level api - Here we don't seem to have access to unresolved schema but we have access to resolved schema
      val tableWithResolvedSchema = tableEnv.from(tableName)
      val columns =
        tableWithResolvedSchema.getResolvedSchema.getColumns.asScala.map(c => Column(c.getName, c.getDataType)).toList

      tableEnv.executeSql(
        s"DROP TABLE $tableName"
      )

      DataSourceTable(tableName, connectorName, Schema(columns), statement)
    }

    tableResults
  }

}

final case class DataSourceTable(name: String, connector: String, schema: Schema, sqlCreateTableStatement: String)
final case class Schema(columns: List[Column])
final case class Column(name: String, dataType: DataType)
