package pl.touk.nussknacker.engine.flink.table.extractor

import cats.data.{Validated, ValidatedNel}
import cats.implicits.{toFunctorOps, toTraverseOps}
import org.apache.flink.configuration.Configuration
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.catalog.CatalogDescriptor
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement

import scala.util.Try

class FlinkDataDefinition private (
    sqlStatements: Option[List[String]],
    catalogConfigurationOpt: Option[Configuration],
    defaultDbName: Option[String]
) extends Serializable {

  private val internalCatalogName = "$nuCatalog"

  def registerIn(tableEnvironment: TableEnvironment): ValidatedNel[DataDefinitionRegistrationError, Unit] = {
    val sqlStatementsExecutionResults = sqlStatements.toList.flatten
      .map(s =>
        Validated
          .fromTry(Try(tableEnvironment.executeSql(s)))
          .leftMap(SqlStatementExecutionError(s, _): DataDefinitionRegistrationError)
          .toValidatedNel
      )
    val catalogRegistrationResult = catalogConfigurationOpt.map { catalogConfiguration =>
      Validated
        .fromTry(
          Try {
            tableEnvironment
              .createCatalog(internalCatalogName, CatalogDescriptor.of(internalCatalogName, catalogConfiguration))
            tableEnvironment.useCatalog(internalCatalogName)
          }
        )
        .leftMap(CatalogRegistrationError(catalogConfiguration, _): DataDefinitionRegistrationError)
        .toValidatedNel
    }
    val databaseUsageError = defaultDbName.map { dbName =>
      Validated
        .fromTry(Try(tableEnvironment.useDatabase(dbName)))
        .leftMap(DefaultDatabaseSetupError(dbName, _): DataDefinitionRegistrationError)
        .toValidatedNel
    }
    (sqlStatementsExecutionResults ::: catalogRegistrationResult.toList ::: databaseUsageError.toList).sequence.void
  }

}

object FlinkDataDefinition {

  def create(
      sqlStatements: Option[List[String]],
      catalogConfigurationOpt: Option[Configuration],
      defaultDbName: Option[String]
  ): Validated[EmptyDataDefinition.type, FlinkDataDefinition] = {
    Validated.cond(
      sqlStatements.isDefined || catalogConfigurationOpt.isDefined,
      new FlinkDataDefinition(sqlStatements, catalogConfigurationOpt, defaultDbName),
      EmptyDataDefinition
    )
  }

  implicit class DataDefinitionRegistrationResultExtension[T](
      result: ValidatedNel[DataDefinitionRegistrationError, T]
  ) {

    def orFail: T = {
      result.valueOr { errors =>
        throw new IllegalStateException(
          errors.toList
            .map(_.message)
            .mkString("Errors occurred when data definition registration in TableEnvironment: ", ", ", "")
        )
      }
    }

  }

}

object EmptyDataDefinition

sealed trait DataDefinitionRegistrationError {
  def message: String
}

final case class SqlStatementExecutionError(statement: SqlStatement, exception: Throwable)
    extends DataDefinitionRegistrationError {

  override def message: String =
    s"""Could not execute sql statement. The statement may be malformed.
       |Sql statement: $statement
       |Caused by: $exception""".stripMargin

}

final case class CatalogRegistrationError(catalogConfiguration: Configuration, exception: Throwable)
    extends DataDefinitionRegistrationError {

  override def message: String =
    s"Could not created catalog with configuration: $catalogConfiguration. Caused by: $exception"

}

final case class DefaultDatabaseSetupError(dbName: String, exception: Throwable)
    extends DataDefinitionRegistrationError {

  override def message: SqlStatement =
    s"Could not set default database to: $dbName. Caused by: $exception"

}
