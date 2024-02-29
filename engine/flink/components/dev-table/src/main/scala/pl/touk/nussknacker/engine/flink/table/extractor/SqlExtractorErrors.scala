package pl.touk.nussknacker.engine.flink.table.extractor

import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement

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

object TableNotCreatedOrCreatedOutsideOfContext extends SqlExtractionErrorType {

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
