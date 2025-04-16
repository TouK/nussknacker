package pl.touk.nussknacker.engine.flink.table.definition

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import org.apache.flink.configuration.Configuration
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.catalog.CatalogDescriptor
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition._
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition.FlinkSqlDdlStatement._
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionCreationError.EmptyDataDefinitionConfiguration
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionCreationError.FlinkDdlParseError.MissingCatalogTypeOption
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionRegistrationError._

import scala.jdk.CollectionConverters._
import scala.util.Try

class FlinkDataDefinition(
    sqlDdl: List[FlinkSqlDdlStatement]
) extends Serializable {

  def registerIn(tableEnvironment: TableEnvironment): ValidatedNel[FlinkDataDefinitionRegistrationError, Unit] = {
    val registrationResults = sqlDdl.map {
      case CreateTable(SqlString(sql), _) =>
        Validated
          .fromTry(Try(tableEnvironment.executeSql(sql)))
          .leftMap(SqlStatementExecutionError(sql, _): FlinkDataDefinitionRegistrationError)
          .toValidatedNel
      case CreateCatalog(CatalogName(name), options, _) =>
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
          .leftMap(CatalogRegistrationError(catalogConf, _): FlinkDataDefinitionRegistrationError)
          .toValidatedNel
    }
    registrationResults.sequence.void
  }

}

object FlinkDataDefinition {

  def apply(
      sql: List[FlinkSqlDdlStatement],
      additionalCatalogConfiguration: Option[Configuration]
  ): ValidatedNel[FlinkDataDefinitionCreationError, FlinkDataDefinition] = {
    if (sql.isEmpty && additionalCatalogConfiguration.isEmpty) {
      EmptyDataDefinitionConfiguration.invalidNel
    } else {
      val additionalCatalog = additionalCatalogConfiguration
        .map(CreateCatalog.buildAdditionalCatalogFromConfig)
        .map(_.map(List(_)))
        .getOrElse(List.empty[CreateCatalog].validNel)
      additionalCatalog.map(catalogs => new FlinkDataDefinition(sql ++ catalogs))
    }
  }

  def applyUnsafe(
      sql: List[FlinkSqlDdlStatement],
      additionalCatalogConfiguration: Option[Configuration]
  ): FlinkDataDefinition =
    apply(sql, additionalCatalogConfiguration).fold(errs => throw errs.head, identity)

  sealed trait FlinkSqlDdlStatement

  object FlinkSqlDdlStatement {
    final case class SqlOption(key: String, value: String)
    final case class SqlString(value: String)
    final case class CatalogName(value: String)
    final case class Connector(value: String)
    final case class CatalogType(value: String)

    final case class CreateTable(
        sql: SqlString,
        connector: Connector
    ) extends FlinkSqlDdlStatement

    final case class CreateCatalog(
        name: CatalogName,
        options: List[SqlOption],
        catalogType: CatalogType
    ) extends FlinkSqlDdlStatement

    object CreateCatalog {
      // We can't user dollar ($) character in this name as some catalogs such as Apache Iceberg use it internally
      // to split object paths
      private val internalCatalogName = "_nu_catalog"

      def buildAdditionalCatalogFromConfig(
          configuration: Configuration
      ): ValidatedNel[MissingCatalogTypeOption, CreateCatalog] = {
        val options = configuration.toMap.asScala.map { case (k, v) =>
          SqlOption(k, v)
        }.toList
        options
          .find(_.key == "type")
          .toValidNel(MissingCatalogTypeOption(internalCatalogName, options))
          .map(_.value)
          .map { catalogType =>
            CreateCatalog(CatalogName(internalCatalogName), options, CatalogType(catalogType))
          }
      }

    }

  }

  implicit class DataDefinitionRegistrationResultExtension[T](
      result: ValidatedNel[FlinkDataDefinitionRegistrationError, T]
  ) {

    def orFail: T = {
      result.valueOr { errors =>
        throw new IllegalStateException(
          errors.toList
            .map(_.getMessage)
            .mkString("Errors occurred when data definition registration in TableEnvironment: ", ", ", ""),
          errors.head
        )
      }
    }

  }

}
