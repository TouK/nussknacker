package pl.touk.nussknacker.engine.flink.table.extractor

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.{EnvironmentSettings, TableEnvironment}
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementNotExecutedError.statementNotExecutedErrorDescription
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement

import scala.jdk.OptionConverters.RichOptional
import scala.util.{Failure, Success, Try}

object TablesExtractor extends LazyLogging {

  import scala.jdk.CollectionConverters._

  // TODO: Make this extractor more memory/cpu efficient and ensure closing of resources. For more details see
  // https://github.com/TouK/nussknacker/pull/5627#discussion_r1512881038
  def extractTablesFromFlinkRuntime(
      sqlStatements: List[SqlStatement]
  ): TablesExtractionResult = {
    val settings = EnvironmentSettings
      .newInstance()
      .build()
    val tableEnv = TableEnvironment.create(settings)

    val sqlErrors = sqlStatements.flatMap(s =>
      Try(tableEnv.executeSql(s)) match {
        case Failure(exception) => Some(SqlStatementNotExecutedError(s, exception))
        case Success(_)         => None
      }
    )

    val tableDefinitions = for {
      catalogName  <- tableEnv.listCatalogs().toList
      catalog      <- tableEnv.getCatalog(catalogName).toScala.toList
      databaseName <- catalog.listDatabases.asScala.toList
      tableName    <- tableEnv.listTables(catalogName, databaseName).toList
      // table path may be different for some catalog-managed tables - for example JDBC catalog adds a schema name to
      // table name when listing tables, but querying using tablePath with catalog.database.schema.tableName throws
      // exception
      tablePath = s"$catalogName.$databaseName.`$tableName`"
      table = Try(tableEnv.from(tablePath))
        .getOrElse(
          throw new IllegalStateException(s"Table extractor could not locate a created table with path: $tablePath")
        )
    } yield TableDefinition(tableName, table.getResolvedSchema)

    TablesExtractionResult(tableDefinitions, sqlErrors)
  }

}

final case class TablesExtractionResult(
    tableDefinitions: List[TableDefinition],
    sqlStatementExecutionErrors: List[SqlStatementNotExecutedError]
)

final case class SqlStatementNotExecutedError(
    statement: SqlStatement,
    exception: Throwable,
) {

  val message: String = {
    val baseErrorMessage    = s"$statementNotExecutedErrorDescription"
    val sqlStatementMessage = s"Sql statement: $statement"
    val exceptionMessage    = s"Caused by: $exception"
    s"$baseErrorMessage\n$sqlStatementMessage\n$exceptionMessage."
  }

}

object SqlStatementNotExecutedError {

  private val statementNotExecutedErrorDescription = "Could not execute sql statement. The statement may be malformed."

}
