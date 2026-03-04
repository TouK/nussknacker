package pl.touk.nussknacker.engine.flink.table.io.definition

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.flink.table.io.definition.ExceptionUtils.deepestCauseMessage
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkSqlDdlStatement._
import pl.touk.nussknacker.engine.flink.table.io.definition.validation.TableUseCase

import scala.annotation.tailrec

sealed trait FlinkDataDefinitionError {
  def message: String
  def cause: Option[Throwable] = None
}

sealed trait FlinkDataDefinitionCreationError extends FlinkDataDefinitionError

// Note: Some of these types like ParseError are used in external Kotlin project. 2-levels deep nested classes are
// unreachable, so they have to stay at 1 level nesting at most.
// TODO: extract this to a Kotlin-compatible API module
object FlinkDataDefinitionCreationError {

  case object EmptyDataDefinitionConfiguration extends FlinkDataDefinitionCreationError {
    override def message: String =
      "Empty data definition configuration. At least one of either tableDefinition or catalogConfiguration should be configured"
  }

  sealed trait FlinkDdlParseError extends FlinkDataDefinitionCreationError

  final case class ParseError(causedBy: Throwable) extends FlinkDdlParseError {
    override def message: String          = s"Could not parse SQL statements: ${deepestCauseMessage(causedBy)}"
    override def cause: Option[Throwable] = Some(causedBy)
  }

  final case class UnallowedStatement(sql: SqlString) extends FlinkDdlParseError {
    override def message: String =
      "Found invalid SQL statement. Only `CREATE TABLE` and `CREATE CATALOG` statements are allowed"
  }

  final case class MissingConnectorOption(sql: SqlString) extends FlinkDdlParseError {
    override def message: String =
      s"Connector not specified in `CREATE TABLE` statement [${sql.value}]"
  }

  final case class MissingCatalogTypeOption(catalogName: String, options: List[SqlOption]) extends FlinkDdlParseError {
    override def message: String =
      s"Catalog type not specified in catalog: [$catalogName] definition. Given options were: $options"
  }

}

sealed trait FlinkDataDefinitionRegistrationError extends FlinkDataDefinitionError

object FlinkDataDefinitionRegistrationError {

  final case class CatalogRegistrationError(catalogConfiguration: Configuration, causedBy: Throwable)
      extends FlinkDataDefinitionRegistrationError {

    override def message: String =
      s"""Could not create catalog.
         |Cause: ${deepestCauseMessage(causedBy)}""".stripMargin

    override def cause: Option[Throwable] = Some(causedBy)
  }

  final case class TableRegistrationError(statement: String, causedBy: Throwable)
      extends FlinkDataDefinitionRegistrationError {

    override def message: String =
      s"""Could not register table by executing a CREATE TABLE statement. The statement may be malformed.
         |Sql statement: [$statement]
         |Cause: ${deepestCauseMessage(causedBy)}""".stripMargin

    override def cause: Option[Throwable] = Some(causedBy)
  }

}

trait FlinkDataDefinitionValidationError extends FlinkDataDefinitionError

object FlinkDataDefinitionValidationError {

  final case class ConnectorNotFound(connector: String, causedBy: Throwable)
      extends FlinkDataDefinitionValidationError {
    override def message: String          = s"Could not find matching connector: [$connector]"
    override def cause: Option[Throwable] = Some(causedBy)
  }

  final case class CatalogNotFound(catalogType: String, causedBy: Throwable)
      extends FlinkDataDefinitionValidationError {
    override def message: String          = s"Could not find matching catalog: [$catalogType]"
    override def cause: Option[Throwable] = Some(causedBy)
  }

  final case class TableRuntimeValidationError(causedBy: Throwable) extends FlinkDataDefinitionValidationError {
    override def message: String          = s"Table validation failed. Reason: ${deepestCauseMessage(causedBy)}"
    override def cause: Option[Throwable] = Some(causedBy)
  }

  trait TableUseCaseError

  final case class TableUseCaseNotSupportedByConnector(
      connector: String,
      tableUseCase: TableUseCase
  ) extends FlinkDataDefinitionValidationError
      with TableUseCaseError {
    override def message: String = s"Table using connector '$connector' cannot be used as $tableUseCase"
  }

  case object PersistableMetadataColumnUsedInSink extends FlinkDataDefinitionValidationError with TableUseCaseError {
    override def message: String = "Table with persistable metadata columns cannot be used as Sink"
  }

  case object PostgresCatalogUnsupportedType extends FlinkDataDefinitionValidationError with TableUseCaseError {
    override def message: String =
      s"Table using 'timestamp with time zone' data type is not supported by Postgres catalog"
  }

}

object ExceptionUtils {

  // TODO: use apache lang3.exception.ExceptionUtils
  // TODO: handle better cases with non-descriptive root exception messages - like java.net.ConnectException
  @tailrec
  private[definition] def deepestCauseMessage(ex: Throwable): String =
    if (ex.getCause != null && ex.getCause.getMessage != null && ex.getCause != ex) deepestCauseMessage(ex.getCause)
    else Option(ex.getMessage).getOrElse("Unknown error")

}
