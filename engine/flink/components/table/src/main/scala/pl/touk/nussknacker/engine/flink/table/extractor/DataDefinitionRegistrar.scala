package pl.touk.nussknacker.engine.flink.table.extractor

import cats.data.{Validated, ValidatedNel}
import cats.implicits.{toFunctorOps, toTraverseOps}
import org.apache.flink.table.api.TableEnvironment
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementNotExecutedError.statementNotExecutedErrorDescription
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement

import scala.util.Try

class DataDefinitionRegistrar private (sqlStatements: Option[List[String]]) extends Serializable {

  def registerIn(tableEnvironment: TableEnvironment): ValidatedNel[SqlStatementNotExecutedError, Unit] = {
    val sqlStatementsExecutionResult = sqlStatements.toList.flatten
      .map(s =>
        Validated
          .fromTry(Try(tableEnvironment.executeSql(s)))
          .leftMap(SqlStatementNotExecutedError(s, _))
          .toValidatedNel
      )
      .sequence
      .void
    sqlStatementsExecutionResult
  }

}

object DataDefinitionRegistrar {

  def apply(sqlStatements: Option[List[String]]): DataDefinitionRegistrar = {
    new DataDefinitionRegistrar(sqlStatements)
  }

  implicit class DataDefinitionRegistrationResultExtension[T](result: ValidatedNel[SqlStatementNotExecutedError, T]) {

    def orFail: T = {
      result.valueOr { errors =>
        throw new IllegalStateException(
          errors.toList
            .map(_.message)
            .mkString("Errors occurred when executing DDLs: ", ", ", "")
        )
      }
    }

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
