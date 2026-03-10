package pl.touk.nussknacker.engine.flink.table.io.definition

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.catalog.CatalogDescriptor
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinition.TableEnvironmentCatalogTablePresenceChecks
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinitionCreationError._
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinitionRegistrationError._
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkSqlDdlStatement._

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOptional
import scala.util.Try

class FlinkDataDefinition(
    sqlDdl: List[FlinkSqlDdlStatement]
) extends Serializable
    with LazyLogging {

  /*
    The registration is performed only when its needed because it can be quite heavy, especially for catalogs.
    For it to be possible, registration has to be idempotent.
    Trying to avoid registration and doing it in every place its needed is a compromise - it pollutes code like in
    TableUsageValidator and its error-prone - errors are hard to catch and may seem nondeterministic.
    For example: missing registration before validating sink usage resulted in "Cannot find table" error, but it was
    visible only in certain situations, like using the same table as sink and source and only at certain times due
    to multiple caching layers.
   */
  def registerIn(tableEnvironment: TableEnvironment): ValidatedNel[FlinkDataDefinitionRegistrationError, Unit] = {
    val registrationResults = sqlDdl.map {
      case CreateTable(name, SqlString(sql), _) =>
        if (tableEnvironment.containsTable(name)) {
          logger.debug(
            s"""Table named "${name.value}" is already registered in environment. Skipping"""
          )
          ().validNel
        } else {
          Validated
            .fromTry(Try(tableEnvironment.executeSql(sql)))
            .leftMap(TableRegistrationError(sql, _): FlinkDataDefinitionRegistrationError)
            .toValidatedNel
        }
      case CreateCatalog(catalogName @ CatalogName(name), options, _) =>
        if (tableEnvironment.containsCatalog(catalogName)) {
          logger.debug(
            s"""Catalog named "${catalogName.value}" is already registered in environment. Skipping"""
          )
          ().validNel
        } else {
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
    apply(sql, additionalCatalogConfiguration).fold(
      errs => throw new IllegalArgumentException(errs.head.message, errs.head.cause.orNull),
      identity
    )

  implicit class DataDefinitionRegistrationResultExtension[T](
      result: ValidatedNel[FlinkDataDefinitionRegistrationError, T]
  ) {

    def orFail: T = {
      result.valueOr { errors =>
        throw new IllegalStateException(
          errors.toList
            .map(_.message)
            .mkString("Errors occurred during data definition registration in TableEnvironment: ", ", ", "")
        )
      }
    }

  }

  implicit class TableEnvironmentCatalogTablePresenceChecks(env: TableEnvironment) {

    def containsTable(name: TableName): Boolean = {
      Try {
        env.from(s"`${name.value}`")
      }.toOption.isDefined
    }

    def containsCatalog(name: CatalogName): Boolean = {
      env.getCatalog(name.value).toScala.isDefined
    }

  }

}

sealed trait FlinkSqlDdlStatement

// Note: Some of these types like CreateTable are used in external Kotlin project. 2-levels deep nested classes are
// unreachable, so they have to stay at 1 level nesting at most.
// TODO: extract this to a Kotlin-compatible API module
object FlinkSqlDdlStatement {
  final case class SqlOption(key: String, value: String)

  final case class SqlString(value: String)

  final case class CatalogName(value: String)

  final case class TableName(value: String)

  final case class Connector(value: String)

  final case class CatalogType(value: String)

  final case class CreateTable(
      name: TableName,
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
