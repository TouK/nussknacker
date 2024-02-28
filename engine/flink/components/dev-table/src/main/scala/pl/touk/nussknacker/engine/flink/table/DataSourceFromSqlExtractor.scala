package pl.touk.nussknacker.engine.flink.table

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.{EnvironmentSettings, TableEnvironment}
import org.apache.flink.table.catalog.{CatalogBaseTable, ObjectPath}
import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.table.SqlFromResourceReader.SqlStatement

import scala.util.{Failure, Success, Try}

object DataSourceFromSqlExtractor extends LazyLogging {

  import scala.jdk.CollectionConverters._
  import scala.jdk.OptionConverters.RichOptional

  private val defaultCatalogName = "default_catalog"
  private val defaultDatabase    = "default_database"
  private val connectorKey       = "connector"

  def extractTablesFromFlinkRuntime(createTableStatements: List[SqlStatement]): List[SqlDataSourceConfig] = {
    val settings = EnvironmentSettings
      .newInstance()
      .build()
    val tableEnv = TableEnvironment.create(settings)

    val catalog = tableEnv.getCatalog(defaultCatalogName).toScala match {
      case Some(value) => value
      case None =>
        throw new IllegalStateException(
          "Default catalog was not found during parsing of sql for generic table components."
        )
    }

    // We don't know what's the name of the table outside of this context so we create a table, extract metadata from it
    // and drop it and do that for every statement
    // TODO: refactor this to functional cats validated
    val tableResults = createTableStatements.flatMap { statement =>
      val tryCreateTableAndExtractData = Try(tableEnv.executeSql(statement)) match {
        case Failure(exception) =>
          logger.error(s"Failed to execute sql statement from dataSource configuration file: $exception")
          None
        case Success(_) =>
          // TODO local: find if there is a simpler way to get granular table metadata like connector
          // Lower level api - Here we have access to unresolved schema and more metadata
          val (tableName, connectorName) = tableEnv.listTables().headOption match {
            case Some(tableName) =>
              val tablePath                                   = new ObjectPath(defaultDatabase, tableName)
              val tableWithUnresolvedSchema: CatalogBaseTable = catalog.getTable(tablePath)
              val connectorName = Try(tableWithUnresolvedSchema.getOptions.get(connectorKey))
                .getOrElse(throw new IllegalStateException(s"Table $tableName did not have connector specified."))
              tableName -> connectorName
            case None => throw new IllegalStateException(s"Could not parse table based on statement: $statement")
          }

          // Higher level api - Here we don't seem to have access to unresolved schema but we have access to resolved schema
          val tableWithResolvedSchema = tableEnv.from(tableName)

          val (columns, typingMap) = tableWithResolvedSchema.getResolvedSchema.getColumns.asScala
            .map { column =>
              val name     = column.getName
              val dataType = column.getDataType
              (Column(name, dataType), name -> columnClassToTypingData(dataType))
            }
            .toList
            .unzip

          val typingResult = Typed.record(typingMap.toMap)

          tableEnv.executeSql(
            s"DROP TABLE $tableName"
          )
          Some(SqlDataSourceConfig(tableName, connectorName, DataSourceSchema(columns), typingResult, statement))
      }
      tryCreateTableAndExtractData.toList
    }

    tableResults
  }

  private def columnClassToTypingData(dataType: DataType): TypingResult =
    Typed.typedClass(dataType.getLogicalType.getDefaultConversion)

}

final case class SqlDataSourceConfig(
    name: String,
    connector: String,
    schema: DataSourceSchema,
    typingResult: TypingResult,
    sqlCreateTableStatement: String
)

final case class DataSourceSchema(columns: List[Column])
final case class Column(name: String, dataType: DataType)
