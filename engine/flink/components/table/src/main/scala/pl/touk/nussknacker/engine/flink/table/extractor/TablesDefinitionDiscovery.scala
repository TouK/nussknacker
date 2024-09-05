package pl.touk.nussknacker.engine.flink.table.extractor

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.{EnvironmentSettings, TableEnvironment}
import org.apache.flink.table.catalog.ObjectIdentifier
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementNotExecutedError.statementNotExecutedErrorDescription
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.{TableDefinition, extractor}

import scala.jdk.OptionConverters.RichOptional
import scala.util.{Failure, Success, Try}

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
    val table = Try(tableEnv.from(tableId.toString)).getOrElse(
      throw new IllegalStateException(s"Table extractor could not locate a created table with path: $tableId")
    )
    TableDefinition(tableId.getObjectName, table.getResolvedSchema)
  }

}

object TablesDefinitionDiscovery {

  def prepareDiscoveryUnsafe(sqlStatements: List[SqlStatement]): TablesDefinitionDiscovery = {
    prepareDiscovery(sqlStatements).valueOr { errors =>
      throw new IllegalStateException(
        errors.toList
          .map(_.message)
          .mkString("Errors occurred when parsing sql component configuration file: ", ", ", "")
      )
    }

  }

  def prepareDiscovery(
      sqlStatements: List[SqlStatement]
  ): ValidatedNel[SqlStatementNotExecutedError, TablesDefinitionDiscovery] = {
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
    NonEmptyList
      .fromList(sqlErrors)
      .map(_.invalid[TablesDefinitionDiscovery])
      .getOrElse(new extractor.TablesDefinitionDiscovery(tableEnv).valid)
  }

}

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
