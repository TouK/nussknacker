package pl.touk.nussknacker.engine.flink.table.definition

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition.FlinkSqlDdlStatement.{SqlOption, SqlString}

sealed trait FlinkDataDefinitionError         extends RuntimeException
sealed trait FlinkDataDefinitionCreationError extends FlinkDataDefinitionError

object FlinkDataDefinitionCreationError {

  case object EmptyDataDefinitionConfiguration extends FlinkDataDefinitionCreationError {
    override def getMessage: String =
      "Empty data definition configuration. At least one of either tableDefinition or catalogConfiguration should be configured"
  }

  sealed trait FlinkDdlParseError extends FlinkDataDefinitionCreationError

  object FlinkDdlParseError {

    final case class ParseError(exception: Throwable) extends FlinkDdlParseError {
      override def getCause: Throwable = exception
      override def getMessage: String  = s"Could not parse SQL statements: ${exception.getMessage}"
    }

    final case class UnallowedStatement(sql: SqlString) extends FlinkDdlParseError {
      override def getMessage: String =
        "Found invalid SQL statement. Only `CREATE TABLE` and `CREATE CATALOG` statements are allowed"
    }

    final case class MissingConnectorOption(sql: SqlString) extends FlinkDdlParseError {
      override def getMessage: String =
        s"Connector not specified in `CREATE TABLE` statement [${sql.value}]"
    }

    final case class MissingCatalogTypeOption(catalogName: String, options: List[SqlOption])
        extends FlinkDdlParseError {
      override def getMessage: String =
        s"Catalog type not specified in catalog: [$catalogName] definition. Given options were: ${options}"
    }

  }

}

sealed trait FlinkDataDefinitionRegistrationError extends FlinkDataDefinitionError

object FlinkDataDefinitionRegistrationError {

  final case class CatalogRegistrationError(catalogConfiguration: Configuration, exception: Throwable)
      extends FlinkDataDefinitionRegistrationError {

    override def getMessage: String =
      s"""Could not create catalog.
         |Cause: ${getCause.getMessage}""".stripMargin

    override def getCause: Throwable = exception
  }

  final case class SqlStatementExecutionError(statement: String, cause: Throwable)
      extends FlinkDataDefinitionRegistrationError {

    override def getMessage: String =
      s"""Could not execute sql statement. The statement may be malformed.
         |Sql statement: [$statement]
         |Cause: ${getCause.getMessage}""".stripMargin

    override def getCause: Throwable = cause
  }

}

sealed trait FlinkDataDefinitionDiscoveryError extends FlinkDataDefinitionError

object FlinkDataDefinitionDiscoveryError {

  final case class ConnectorDiscoveryProblem(connector: String, exception: Throwable)
      extends FlinkDataDefinitionDiscoveryError {
    override def getMessage: String  = s"Could not find matching connector: [$connector]"
    override def getCause: Throwable = exception
  }

  final case class CatalogDiscoveryProblem(catalogType: String, exception: Throwable)
      extends FlinkDataDefinitionDiscoveryError {
    override def getMessage: String  = s"Could not find matching catalog: [$catalogType]"
    override def getCause: Throwable = exception
  }

  final case class NoSinkOrSourceImplementationsFoundForConnector(connector: String)
      extends FlinkDataDefinitionDiscoveryError {
    override def getMessage: String =
      s"Could not find Source or Sink factory for connector [${connector}]. The connector is invalid."
  }

  final case class TableEnvironmentRuntimeValidationError(exception: Throwable)
      extends FlinkDataDefinitionDiscoveryError {

    // Usually message of second exception in the stack trace is most descriptive
    override def getMessage: String = Option(exception.getCause)
      .flatMap(c => Option(c.getMessage))
      .getOrElse(exception.getMessage)

    override def getCause: Throwable = exception
  }

  final case class CatalogNonTransientValidationError(exception: Throwable) extends FlinkDataDefinitionDiscoveryError {

    // Usually message of second exception in the stack trace is most descriptive
    override def getMessage: String = Option(exception.getCause)
      .flatMap(c => Option(c.getMessage))
      .getOrElse(exception.getMessage)

    override def getCause: Throwable = exception
  }

}
