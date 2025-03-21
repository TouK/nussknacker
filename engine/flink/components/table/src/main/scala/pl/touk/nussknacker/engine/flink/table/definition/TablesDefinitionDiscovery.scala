package pl.touk.nussknacker.engine.flink.table.definition

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.{catsSyntaxValidatedId, toFunctorOps}
import cats.syntax.either._
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import org.apache.flink.table.api.{EnvironmentSettings, Schema, Table, TableDescriptor}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.catalog.{ObjectIdentifier, ObjectPath}
import org.apache.flink.table.catalog.Catalog
import org.apache.flink.table.factories.{
  DynamicTableFactory,
  DynamicTableSinkFactory,
  DynamicTableSourceFactory,
  FactoryUtil
}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionDiscoveryError.{
  ConnectorDiscoveryProblem,
  NoSinkOrSourceImplementationsFoundForConnector,
  TableEnvironmentRuntimeValidationError
}
import pl.touk.nussknacker.engine.flink.table.utils.SchemaExtensions.ResolvedSchemaExtensions
import pl.touk.nussknacker.engine.util.ThreadUtils

import java.net.URLClassLoader
import scala.jdk.OptionConverters.RichOptional
import scala.util.Try

class TablesDefinitionDiscovery(env: StreamTableEnvironment) extends LazyLogging {

  import scala.jdk.CollectionConverters._

  def listTables: List[ValidatedNel[FlinkDataDefinitionDiscoveryError, TableDefinition]] = for {
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

object TablesDefinitionDiscovery extends LazyLogging {

  def prepareDiscovery(
      flinkDataDefinition: FlinkDataDefinition,
      miniCluster: FlinkMiniClusterWithServices,
      classLoader: URLClassLoader
  ): ValidatedNel[FlinkDataDefinitionRegistrationError, TablesDefinitionDiscovery] = {
    ThreadUtils.withThisAsContextClassLoader(classLoader) {
      miniCluster.withDetachedStreamExecutionEnvironment { env =>
        val streamTableEnv = StreamTableEnvironment.create(
          env,
          EnvironmentSettings
            .newInstance()
            .withClassLoader(classLoader)
            .withConfiguration(Configuration.fromMap(env.getConfiguration.toMap))
            .build()
        )
        flinkDataDefinition
          .registerIn(streamTableEnv)
          .map(_ => new TablesDefinitionDiscovery(streamTableEnv))
      }
    }
  }

}
