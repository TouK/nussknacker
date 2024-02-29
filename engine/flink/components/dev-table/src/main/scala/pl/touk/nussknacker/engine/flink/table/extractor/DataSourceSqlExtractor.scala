package pl.touk.nussknacker.engine.flink.table.extractor

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.{EnvironmentSettings, Table, TableEnvironment}
import org.apache.flink.table.catalog.{CatalogBaseTable, ObjectPath}
import org.apache.flink.table.types.DataType
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.table.extractor.SqlDataSourceConfig.Connector
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement

import scala.util.{Failure, Success, Try}

object DataSourceSqlExtractor extends LazyLogging {

  import scala.jdk.CollectionConverters._
  import scala.jdk.OptionConverters.RichOptional

  private val connectorKey = "connector"

  // TODO: validate duplicate table names
  def extractTablesFromFlinkRuntime(
      createTableStatements: List[SqlStatement]
  ): List[Validated[SqlExtractorError, SqlDataSourceConfig]] = {
    val settings = EnvironmentSettings
      .newInstance()
      .build()
    val tableEnv = TableEnvironment.create(settings)

    /**
     * Access to `CatalogBaseTable` (carrying schema details such as connector, format, and options) requires the name
     * of the catalog/database. This code uses the default catalog and database.
     *gg
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
    ): Validated[SqlExtractorError, SqlDataSourceConfig] = {
      implicit val implicitStatement: SqlStatement = statement;

      Try(tableEnv.executeSql(statement)) match {
        case Failure(exception) =>
          Invalid(SqlExtractorError(StatementNotExecuted, Some(exception)))
        case Success(_) =>
          tableEnv.listTables().headOption match {
            case Some(tableName) =>
              val tablePath                                   = new ObjectPath(tableEnv.getCurrentDatabase, tableName)
              val tableWithUnresolvedSchema: CatalogBaseTable = catalog.getTable(tablePath)
              val connectorOpt: Validated[SqlExtractorError, TableMetaData] = extractMetaData(tableWithUnresolvedSchema)

              connectorOpt match {
                case Valid(TableMetaData(connector)) => {
                  // Need to grab table from env because table from result has wrong schema
                  val tableFromEnv                                               = tableEnv.from(tableName)
                  val (columns, typedColumns: List[(String, typing.TypedClass)]) = extractSchema(tableFromEnv)
                  val typingResult: typing.TypedObjectTypingResult               = Typed.record(typedColumns.toMap)

                  Try {
                    tableEnv.executeSql(
                      s"DROP TABLE $tableName"
                    )
                  } match {
                    case Success(_) =>
                      Valid(
                        SqlDataSourceConfig(
                          tableName,
                          connector,
                          DataSourceSchema(columns),
                          typingResult,
                          statement
                        )
                      )
                    case Failure(exception) => Invalid(SqlExtractorError(TableNotCreated, Some(exception)))
                  }
                }
                case i @ Invalid(_) => i
              }
            // TODO: test for this - is this assumption correct
            case None => Invalid(SqlExtractorError(TableNotCreatedOrCreatedInNonDefaultCatalog, None))
          }
      }
    }

    val tablesResults = createTableStatements.map(extractOne)
    tablesResults
  }

  final case class TableMetaData(connector: Connector)

  // CatalogBaseTable - contains more metadata but has unresolved schema
  // TODO: extract format - have to fail on formats that are not on classpath
  private def extractMetaData(
      tableWithMetadata: CatalogBaseTable
  )(implicit statement: SqlStatement): Validated[SqlExtractorError, TableMetaData] = {
    val connectorOpt = tableWithMetadata.getOptions.asScala.get(connectorKey)
    connectorOpt match {
      case Some(connector) => Valid(TableMetaData(connector))
      case None            => Invalid(SqlExtractorError(ConnectorMissing))
    }
  }

  // Resolved schema - has resolved column types but doesn't have info about connectors, options etc.
  private def extractSchema(tableFromEnv: Table) =
    tableFromEnv.getResolvedSchema.getColumns.asScala
      .map { column =>
        val name     = column.getName
        val dataType = column.getDataType
        (Column(name, dataType), name -> columnClassToTypingData(dataType))
      }
      .toList
      .unzip

  // TODO: handle complex data types - Maps, Arrays, Rows, Raw
  private def columnClassToTypingData(dataType: DataType) =
    Typed.typedClass(dataType.getLogicalType.getDefaultConversion)

}

sealed trait SqlExtractionErrorType {
  val message: String
}

object StatementNotExecuted extends SqlExtractionErrorType {

  // TODO: explain our required syntax - for example we don't handle explicit catalog / db name
  override val message: String = """
      |Could not execute sql statement. The statement may be malformed. The statement has to be a CREATE TABLE statement
      |according to https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/table/sql/create/#create-table
      |syntax.
      |""".stripMargin

}

object TableNotCreated extends SqlExtractionErrorType {
  override val message: String = "Statement was executed, but did not create expected table."
}

object TableNotCreatedOrCreatedInNonDefaultCatalog extends SqlExtractionErrorType {

  override val message: String =
    """"
      |Statement was executed, but did not create expected table or it created a table outside of default catalog or 
      |database.""".stripMargin

}

// Can never happen now - missing connector statements fail on execution unless `ManagedTableFactory` is on the
// classpath
object ConnectorMissing extends SqlExtractionErrorType {
  override val message: String = "Connector is missing."
}

final case class SqlExtractorError(
    errorType: SqlExtractionErrorType,
    statement: SqlStatement,
    exception: Option[Throwable],
)

object SqlExtractorError {

  def apply(errorType: SqlExtractionErrorType, exception: Option[Throwable] = None)(
      implicit sqlStatement: SqlStatement
  ): SqlExtractorError = {
    SqlExtractorError(errorType, sqlStatement, exception)
  }

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

final case class DataSourceSchema(columns: List[Column])
final case class Column(name: String, dataType: DataType)
