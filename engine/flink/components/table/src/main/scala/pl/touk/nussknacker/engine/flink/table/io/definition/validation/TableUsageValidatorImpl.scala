package pl.touk.nussknacker.engine.flink.table.io.definition.validation

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.{catsSyntaxValidatedId, toFunctorOps}
import cats.syntax.either._
import org.apache.flink.connector.jdbc.postgres.database.catalog.PostgresCatalog
import org.apache.flink.table.api._
import org.apache.flink.table.catalog.{Catalog, ObjectIdentifier, ResolvedSchema}
import org.apache.flink.table.factories.{DynamicTableFactory, DynamicTableSinkFactory, DynamicTableSourceFactory}
import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.logical.LogicalTypeRoot
import pl.touk.nussknacker.engine.flink.table.io.definition._
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinition.DataDefinitionRegistrationResultExtension
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinitionValidationError._
import pl.touk.nussknacker.engine.flink.table.io.definition.discovery.{CatalogDiscovery, TableFactoryDiscovery}
import pl.touk.nussknacker.engine.flink.table.io.definition.validation.FactoryUsageValidator._
import pl.touk.nussknacker.engine.flink.table.io.definition.validation.SchemaValidator._
import pl.touk.nussknacker.engine.flink.table.io.definition.validation.SynchronizedPlanCompilationValidator._
import pl.touk.nussknacker.engine.flink.table.typing.SchemaExtensions.ResolvedSchemaExtensions

import scala.jdk.CollectionConverters._
import scala.util.Try

class TableUsageValidatorImpl(classLoader: ClassLoader) extends TableUsageValidator with Serializable {

  def validateTableUsage(
      table: TableDefinition,
      tableUseCase: TableUseCase,
      env: TableEnvironment,
      flinkDataDefinition: FlinkDataDefinition
  ): Validated[NonEmptyList[FlinkDataDefinitionError], Unit] = {
    TableFactoryDiscovery.discoverTableFactory(table, env, flinkDataDefinition, classLoader).andThen { tableFactory =>
      val catalog = CatalogDiscovery.discoverCatalog(env, flinkDataDefinition, table.tableId.getCatalogName)
      tableUseCase match {
        case TableUseCase.Source =>
          validateSchemaCatalogCompatibility(catalog, table.table.getResolvedSchema)
            .andThen(_ => validateSourceUsage(tableFactory))
            .andThen(_ => validateSourceInRuntime(table.table))
        case TableUseCase.Sink =>
          validateSchemaCatalogCompatibility(catalog, table.table.getResolvedSchema)
            .andThen(_ => validateSinkSchema(table.table.getResolvedSchema))
            .andThen(_ => validateSinkConnectorUsage(tableFactory))
            .andThen(_ => validateSinkInRuntime(table.table, table.tableId, env, flinkDataDefinition))
      }
    }
  }

}

private object SchemaValidator {

  def validateSinkSchema(
      schema: ResolvedSchema
  ): ValidatedNel[FlinkDataDefinitionValidationError, Unit] =
    if (schema.containsPersistableMetadataColumns())
      PersistableMetadataColumnUsedInSink.invalidNel
    else
      ().validNel

  def validateSchemaCatalogCompatibility(
      catalog: Catalog,
      schema: ResolvedSchema
  ): ValidatedNel[FlinkDataDefinitionValidationError, Unit] = {
    catalog match {
      case _: PostgresCatalog => {
        val dataTypes = schema.getColumns.asScala.map(_.getDataType).toList
        val unsupportedTypeUsed = dataTypes.exists { dt =>
          dt.getLogicalType.is(LogicalTypeRoot.TIMESTAMP_WITH_LOCAL_TIME_ZONE)
        }
        if (unsupportedTypeUsed) {
          PostgresCatalogUnsupportedType.invalidNel
        } else {
          ().validNel
        }
      }
      case _ => ().validNel
    }
  }

}

private object FactoryUsageValidator {

  def validateSourceUsage(factory: DynamicTableFactory): ValidatedNel[TableUseCaseNotSupportedByConnector, Unit] =
    validateConnectorUsage(factory, classOf[DynamicTableSourceFactory], TableUseCase.Source)

  def validateSinkConnectorUsage(
      factory: DynamicTableFactory
  ): ValidatedNel[TableUseCaseNotSupportedByConnector, Unit] =
    validateConnectorUsage(factory, classOf[DynamicTableSinkFactory], TableUseCase.Sink)

  private def validateConnectorUsage[A <: DynamicTableFactory](
      factory: DynamicTableFactory,
      expectedFactoryClass: Class[A],
      useCase: TableUseCase
  ): ValidatedNel[TableUseCaseNotSupportedByConnector, Unit] = {
    Either
      .cond(
        expectedFactoryClass.isInstance(factory),
        (),
        TableUseCaseNotSupportedByConnector(factory.factoryIdentifier(), useCase)
      )
      .toValidatedNel

  }

}

// Synchronization is required because of nondeterministic errors when making multiple calls at the same time.
// The precise reason is unknown.
private object SynchronizedPlanCompilationValidator {

  def validateSourceInRuntime(table: Table): Validated[NonEmptyList[FlinkDataDefinitionValidationError], Unit] = {
    val deadEndSink = buildTable("blackhole", table.getResolvedSchema.toSourceRowDataType)
    val pipeline    = table.insertInto(deadEndSink)
    tryCompile(pipeline)
  }

  def validateSinkInRuntime(
      table: Table,
      tableId: ObjectIdentifier,
      env: TableEnvironment,
      flinkDataDefinition: FlinkDataDefinition
  ): Validated[NonEmptyList[FlinkDataDefinitionValidationError], Unit] = {
    val datagenSource = buildTable("datagen", table.getResolvedSchema.toPhysicalRowDataType)
    flinkDataDefinition.registerIn(env).orFail
    val sourceFromEnv = env.from(datagenSource)
    val pipeline      = sourceFromEnv.insertInto(tableId.asSerializableString())
    tryCompile(pipeline)
  }

  private def buildTable(connector: String, rowDataType: DataType): TableDescriptor =
    TableDescriptor
      .forConnector(connector)
      .schema(Schema.newBuilder().fromRowDataType(rowDataType).build)
      .build()

  private def tryCompile(pipeline: TablePipeline): Validated[NonEmptyList[FlinkDataDefinitionValidationError], Unit] =
    Try {
      synchronized(pipeline.compilePlan())
    }.toEither
      .leftMap(e => NonEmptyList.one(TableRuntimeValidationError(e)))
      .toValidated
      .void

}
