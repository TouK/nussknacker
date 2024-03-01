package pl.touk.nussknacker.engine.flink.table.extractor

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.{EnvironmentSettings, TableEnvironment, TableResult}
import org.apache.flink.table.catalog.{CatalogBaseTable, ObjectPath}
import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.table.extractor.SqlDataSourceConfig.Connector
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.extractor.TypeExtractor.extractTypedSchema

import scala.util.Try

// TODO: additional validation (maybe outside scope of this class' responsibility):
//  - duplicate table names
//  - custom validation per connector
object DataSourceSqlExtractor extends LazyLogging {

  import scala.jdk.CollectionConverters._
  import scala.jdk.OptionConverters.RichOptional

  private val connectorKey        = "connector"
  private val builtInCatalogName  = "defaultCatalog"
  private val buildInDatabaseName = "defaultDatabase"

  def extractTablesFromFlinkRuntime(
      createTableStatements: List[SqlStatement]
  ): List[Either[SqlExtractorError, SqlDataSourceConfig]] = {
    val settings = EnvironmentSettings
      .newInstance()
      .withBuiltInCatalogName(builtInCatalogName)
      .withBuiltInDatabaseName(buildInDatabaseName)
      .build()
    val tableEnv = TableEnvironment.create(settings)

    /**
     * Access to `CatalogBaseTable` (carrying schema details such as connector, format, and options) requires the name
     * of the catalog/database. This code uses the default catalog and database.
     * Assumptions:
     *  - The executed sql statements create tables in the default catalog and database
     *
     * @see <a href="https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/table/catalogs/#registering-a-catalog">Flink docs source </a>
     */
    val catalog = tableEnv.getCatalog(builtInCatalogName).toScala match {
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

        // CatalogBaseTable - contains metadata
        tablePath                 = new ObjectPath(buildInDatabaseName, tableName)
        tableWithUnresolvedSchema = catalog.getTable(tablePath)
        metaData <- extractMetaData(tableWithUnresolvedSchema)

        // Table with resolved schema - contains resolved column types
        tableFromEnv = tableEnv.from(tableName)
        typedSchema  = extractTypedSchema(tableFromEnv)

        _ <- tryExecuteStatement(s"DROP TABLE $tableName", tableEnv)
      } yield SqlDataSourceConfig(
        tableName,
        metaData.connector,
        typedSchema,
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

  private def extractMetaData(
      tableWithMetadata: CatalogBaseTable
  )(implicit statement: SqlStatement): Either[SqlExtractorError, TableMetaData] = {
    tableWithMetadata.getOptions.asScala
      .get(connectorKey)
      .toRight(SqlExtractorError(ConnectorMissing))
      .map(TableMetaData)
  }

}

final case class SqlDataSourceConfig(
    tableName: String,
    connector: Connector,
    schema: Schema,
    sqlCreateTableStatement: SqlStatement
)

object SqlDataSourceConfig {
  type Connector = String
}

final case class Schema(columns: List[Column], typingResult: TypingResult)
final case class Column(name: String, flinkDataType: DataType)
