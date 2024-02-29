package pl.touk.nussknacker.engine.flink.table.extractor

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.{EnvironmentSettings, Table, TableEnvironment, TableResult}
import org.apache.flink.table.catalog.{CatalogBaseTable, ObjectPath}
import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.table.extractor.SqlDataSourceConfig.Connector
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement

import scala.util.Try

object DataSourceSqlExtractor extends LazyLogging {

  import scala.jdk.CollectionConverters._
  import scala.jdk.OptionConverters.RichOptional

  private val connectorKey = "connector"

  // TODO: validate duplicate table names
  def extractTablesFromFlinkRuntime(
      createTableStatements: List[SqlStatement]
  ): List[Either[SqlExtractorError, SqlDataSourceConfig]] = {
    val settings = EnvironmentSettings
      .newInstance()
      .build()
    val tableEnv = TableEnvironment.create(settings)

    /**
     * Access to `CatalogBaseTable` (carrying schema details such as connector, format, and options) requires the name
     * of the catalog/database. This code uses the default catalog and database.
     * Assumptions:
     *  - Default catalog is provided automatically
     *  - Within the default catalog, a default database exists
     *
     * @see <a href="https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/table/catalogs/#registering-a-catalog">Flink docs source </a>
     */
    val catalog = tableEnv.getCatalog(tableEnv.getCurrentCatalog).toScala match {
      case Some(value) => value
      case None =>
        throw new IllegalStateException(
          "Default catalog was not found during parsing of sql for generic table components."
        )
    }

    /**
     *  1. Execution of arbitrary sql statement in temp context
     *  1. Extraction of schema and metadata from the first (only) table in the env
     *  1. Dropping the table so we have guarantee that there are is only one table in the env
     *
     *  Assumptions:
     *   - There are no tables registered by default
     **/
    def extractOne(
        statement: SqlStatement
    ): Either[SqlExtractorError, SqlDataSourceConfig] = {
      implicit val statementImplicit: SqlStatement = statement
      for {
        _ <- tryExecuteStatement(statement, tableEnv)
        // Need to grab table from env because table from result has wrong schema
        tableName <- tableEnv
          .listTables()
          .headOption
          .toRight(SqlExtractorError(TableNotCreatedOrCreatedOutsideOfContext, None))

        tablePath                 = new ObjectPath(tableEnv.getCurrentDatabase, tableName)
        tableWithUnresolvedSchema = catalog.getTable(tablePath)
        metaData <- extractMetaData(tableWithUnresolvedSchema)

        tableFromEnv            = tableEnv.from(tableName)
        (columns, typingResult) = extractSchema(tableFromEnv)

        _ <- tryExecuteStatement(s"DROP TABLE $tableName", tableEnv)
      } yield SqlDataSourceConfig(
        tableName,
        metaData.connector,
        DataSourceSchema(columns),
        typingResult,
        statement
      )
    }

    val tablesResults = createTableStatements.map(s => extractOne(s))
    tablesResults
  }

  private def tryExecuteStatement(
      sqlStatement: SqlStatement,
      env: TableEnvironment
  ): Either[SqlExtractorError, TableResult] =
    Try(env.executeSql(sqlStatement)).toEither.left.map(e =>
      SqlExtractorError(StatementNotExecuted, Some(e))(sqlStatement)
    )

  private final case class TableMetaData(connector: Connector)

  // CatalogBaseTable - contains more metadata but has unresolved schema
  // TODO: extract format - have to fail on formats that are not on classpath
  private def extractMetaData(
      tableWithMetadata: CatalogBaseTable
  )(implicit statement: SqlStatement): Either[SqlExtractorError, TableMetaData] = {
    val connectorOpt = tableWithMetadata.getOptions.asScala.get(connectorKey)
    connectorOpt match {
      case Some(connector) => Right(TableMetaData(connector))
      case None            => Left(SqlExtractorError(ConnectorMissing))
    }
  }

  // Resolved schema - has resolved column types but doesn't have info about connectors, options etc.
  private def extractSchema(tableFromEnv: Table) = {
    val (columns, columnsTypingData) = tableFromEnv.getResolvedSchema.getColumns.asScala
      .map { column =>
        val name     = column.getName
        val dataType = column.getDataType
        (Column(name, dataType), name -> columnClassToTypingData(dataType))
      }
      .toList
      .unzip
    columns -> Typed.record(columnsTypingData.toMap)
  }

  // TODO: handle complex data types - Maps, Arrays, Rows, Raw
  private def columnClassToTypingData(dataType: DataType) =
    Typed.typedClass(dataType.getLogicalType.getDefaultConversion)

}

//  TODO: add boundedness information
final case class SqlDataSourceConfig(
    name: String,
    connector: Connector,
    schema: DataSourceSchema,
    typingResult: TypingResult,
    sqlCreateTableStatement: SqlStatement
)

object SqlDataSourceConfig {
  type Connector = String
}

// TODO: flatten this?
final case class DataSourceSchema(columns: List[Column])
final case class Column(name: String, dataType: DataType)
