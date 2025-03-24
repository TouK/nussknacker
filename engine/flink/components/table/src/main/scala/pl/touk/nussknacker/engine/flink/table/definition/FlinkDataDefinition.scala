package pl.touk.nussknacker.engine.flink.table.definition

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import org.apache.flink.configuration.Configuration
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.catalog.CatalogDescriptor
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition.FlinkSqlDdlStatement
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition.FlinkSqlDdlStatement._

import scala.jdk.CollectionConverters._
import scala.util.Try

class FlinkDataDefinition(
    sqlDdl: List[FlinkSqlDdlStatement]
) extends Serializable {

  def registerIn(tableEnvironment: TableEnvironment): ValidatedNel[DataDefinitionRegistrationError, Unit] = {
    val registrationResults = sqlDdl.map {
      case CreateTable(SqlString(sql)) =>
        Validated
          .fromTry(Try(tableEnvironment.executeSql(sql)))
          .leftMap(SqlStatementExecutionError(sql, _): DataDefinitionRegistrationError)
          .toValidatedNel
      case CreateCatalog(CatalogName(name), options) =>
        val catalogConf = Configuration.fromMap(
          options
            .map { case SqlOption(key, value) =>
              key -> value
            }
            .toMap
            .asJava
        )
        Validated
          .fromTry(Try { tableEnvironment.createCatalog(name, CatalogDescriptor.of(name, catalogConf)) })
          .leftMap(CatalogRegistrationError(catalogConf, _): DataDefinitionRegistrationError)
          .toValidatedNel
    }
    registrationResults.sequence.void
  }

}

object FlinkDataDefinition {

  trait FlinkDataDefinitionCreationError extends IllegalArgumentException

  case object EmptyDataDefinitionConfiguration extends FlinkDataDefinitionCreationError {
    override def getMessage: String =
      "Empty data definition configuration. At least one of either tableDefinitionFilePath or catalogConfiguration should be configured"
  }

  def apply(
      sql: Option[String],
      additionalCatalogConfiguration: Option[Configuration]
  ): ValidatedNel[FlinkDataDefinitionCreationError, FlinkDataDefinition] = {
    if (sql.isEmpty && additionalCatalogConfiguration.isEmpty) {
      EmptyDataDefinitionConfiguration.invalidNel
    } else {
      val parsedDdls = sql.map(FlinkDdlParser.parse).getOrElse(List.empty.validNel)
      val additionalCatalog = additionalCatalogConfiguration
        .map(CreateCatalog.buildAdditionalCatalogFromConfig)
        .toList
        .validNel

      (parsedDdls, additionalCatalog).mapN { (ddls, catalogs) =>
        new FlinkDataDefinition(ddls ++ catalogs)
      }
    }
  }

  def applyUnsafe(sql: Option[String], additionalCatalogConfiguration: Option[Configuration]): FlinkDataDefinition =
    apply(sql, additionalCatalogConfiguration).fold(errs => throw errs.head, identity)

  sealed trait FlinkSqlDdlStatement

  object FlinkSqlDdlStatement {
    final case class SqlOption(key: String, value: String)
    final case class SqlString(value: String)
    final case class CatalogName(value: String)

    final case class CreateTable(
        sql: SqlString
    ) extends FlinkSqlDdlStatement

    final case class CreateCatalog(
        name: CatalogName,
        options: List[SqlOption]
    ) extends FlinkSqlDdlStatement

    object CreateCatalog {
      // We can't user dollar ($) character in this name as some catalogs such as Apache Iceberg use it internally
      // to split object paths
      private val internalCatalogName = "_nu_catalog"

      def buildAdditionalCatalogFromConfig(configuration: Configuration): CreateCatalog = {
        val conf = configuration.toMap.asScala.map { case (k, v) =>
          SqlOption(k, v)
        }.toList
        CreateCatalog(CatalogName(internalCatalogName), conf)
      }

    }

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

sealed trait DataDefinitionRegistrationError {
  def message: String
}

final case class SqlStatementExecutionError(statement: String, exception: Throwable)
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
