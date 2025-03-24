package pl.touk.nussknacker.engine.flink.table.definition

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.{catsSyntaxValidatedId, toFunctorOps}
import cats.syntax.either._
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.{Schema, Table, TableDescriptor}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.catalog.{Catalog, ObjectIdentifier, ObjectPath}
import org.apache.flink.table.factories.{
  DynamicTableFactory,
  DynamicTableSinkFactory,
  DynamicTableSourceFactory,
  FactoryUtil
}
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionDiscoveryError.{
  ConnectorDiscoveryProblem,
  NoSinkOrSourceImplementationsFoundForConnector,
  TableEnvironmentRuntimeValidationError
}
import pl.touk.nussknacker.engine.flink.table.utils.SchemaExtensions.ResolvedSchemaExtensions

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOptional
import scala.util.Try

object TablesDefinitionDiscovery extends LazyLogging {

  // TODO: Check if this works without:
  //  1. Setting ModelClassLoader as context classloader
  //  2. Setting ModelClassLoader on EnvironmnentSettings for StreamTableEnv (this may be redundant anyways)
  def discoverTables(
      flinkDataDefinition: FlinkDataDefinition,
      env: StreamTableEnvironment
  ): List[ValidatedNel[FlinkDataDefinitionError, TableDefinition]] = {
    try {
      flinkDataDefinition
        .registerIn(env)
        .fold(
          e => List(e.invalid),
          _ => new TablesDefinitionDiscoveryInternal(env).listTables
        )
    } finally {
      // TODO: check if its enough
      for {
        catalogName  <- env.listCatalogs().toList
        catalog      <- env.getCatalog(catalogName).toScala.toList
        databaseName <- catalog.listDatabases.asScala.toList
        tableName    <- env.listTables(catalogName, databaseName).toList
      } yield {
        env.useCatalog(catalogName)
        env.useDatabase(databaseName)
        env.executeSql(s"DROP TABLE `$tableName`")
      }
      env.useCatalog("default_catalog")
      env.useDatabase("default_database")
    }
  }

  private class TablesDefinitionDiscoveryInternal(env: StreamTableEnvironment) extends LazyLogging {

    import scala.jdk.CollectionConverters._

    def listTables: List[ValidatedNel[FlinkDataDefinitionError, TableDefinition]] = for {
      catalogName  <- env.listCatalogs().toList
      catalog      <- env.getCatalog(catalogName).toScala.toList
      databaseName <- catalog.listDatabases.asScala.toList
      tableName    <- env.listTables(catalogName, databaseName).toList
    } yield validateAndExtractTable(catalog, catalogName, databaseName, tableName)

    private def validateAndExtractTable(
        catalog: Catalog,
        catalogName: String,
        databaseName: String,
        tableName: String
    ): ValidatedNel[FlinkDataDefinitionDiscoveryError, TableDefinition] = {
      val tableId      = ObjectIdentifier.of(catalogName, databaseName, tableName)
      val baseTable    = catalog.getTable(new ObjectPath(databaseName, tableName))
      val table        = extractTable(tableId)
      val connectorOpt = baseTable.getOptions.asScala.get("connector")

      val validation = connectorOpt match {
        case Some(connector) => validateTable(connector, table, tableName, env)
        case None            => ().validNel // No connector found - may be a catalog-managed table
      }

      validation.map(_ => TableDefinition(tableId, table.getResolvedSchema))
    }

    private def extractTable(tableId: ObjectIdentifier): Table = Try(env.from(tableId.toString)).fold(
      ex => throw new IllegalStateException(s"Table extractor could not locate a created table with id: $tableId", ex),
      identity
    )

    private def validateTable(
        connector: String,
        table: Table,
        tableName: String,
        env: StreamTableEnvironment
    ): ValidatedNel[FlinkDataDefinitionDiscoveryError, Unit] = {
      val tableFactory = Try {
        FactoryUtil.discoverFactory(getClass.getClassLoader, classOf[DynamicTableFactory], connector)
      }.fold(ex => ConnectorDiscoveryProblem(connector, ex).invalidNel, factory => factory.validNel)

      tableFactory.andThen { factory =>
        val sourceValidationResult = factory match {
          case _: DynamicTableSourceFactory => Some(validateSource(table))
          case _                            => None
        }

        val sinkValidationResult = factory match {
          case _: DynamicTableSinkFactory if table.getResolvedSchema.containsPersistableMetadataColumns() => {
            logger.warn(s"Ommitted table [$tableName] as a Sink since persistable metadata columns are not supported")
            None
          }
          case _: DynamicTableSinkFactory => Some(validateSink(table, tableName, env))
          case _                          => None
        }

        (sourceValidationResult, sinkValidationResult) match {
          case (None, None) => NonEmptyList.one(NoSinkOrSourceImplementationsFoundForConnector(connector)).invalid
          case (Some(sourceResult), Some(sinkResult)) => sourceResult.combine(sinkResult)
          case (Some(sourceResult), None)             => sourceResult
          case (None, Some(sinkResult))               => sinkResult
        }
      }
    }

    private def validateSource(table: Table) = {
      Try {
        table
          .insertInto(
            TableDescriptor
              .forConnector("blackhole")
              .schema(Schema.newBuilder().fromRowDataType(table.getResolvedSchema.toSourceRowDataType).build)
              .build()
          )
          .compilePlan()
      }.toEither
        .leftMap(e => NonEmptyList.one(TableEnvironmentRuntimeValidationError(e)))
        .toValidated
        .void
    }

    private def validateSink(table: Table, tableName: String, env: StreamTableEnvironment) = {
      Try {
        env
          .from(
            TableDescriptor
              .forConnector("datagen")
              .schema(Schema.newBuilder().fromRowDataType(table.getResolvedSchema.toPhysicalRowDataType).build)
              .build()
          )
          .insertInto(tableName)
          .compilePlan()
      }.toEither
        .leftMap(e => NonEmptyList.one(TableEnvironmentRuntimeValidationError(e)))
        .toValidated
        .void
    }

  }

}
