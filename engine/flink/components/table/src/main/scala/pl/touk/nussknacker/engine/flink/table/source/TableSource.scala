package pl.touk.nussknacker.engine.flink.table.source

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.{DataTypes, Schema}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.catalog.Column.{ComputedColumn, MetadataColumn, PhysicalColumn}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, VariableConstants}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.livedata.{DataRecord, DataRecords, LiveDataProvider}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process.{
  CustomizableContextInitializerSource,
  FlinkCustomNodeContext,
  FlinkSource,
  FlinkSourceTestSupport
}
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition._
import pl.touk.nussknacker.engine.flink.table.source.TableSource.{
  filteringInternalViewName,
  SQL_EXPRESSION_PARAMETER_NAME
}
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._
import pl.touk.nussknacker.engine.flink.table.utils.SchemaExtensions._
import pl.touk.nussknacker.engine.flink.watermarkstrategy.FlinkWatermarkStrategyRuntimeHandler
import pl.touk.nussknacker.engine.flink.watermarkstrategy.FlinkWatermarkStrategyRuntimeHandler.{
  ContextInitializingFunction,
  ContextWithEventTime
}
import pl.touk.nussknacker.engine.util.watermarkstrategy.{WatermarkStrategyOptions, WithWatermarkStrategyOptions}

import java.time.{Instant, OffsetDateTime}
import scala.jdk.CollectionConverters._

class TableSource(
    tableDefinition: TableDefinition,
    flinkDataDefinition: FlinkDataDefinition,
    testDataGenerationMode: TestDataGenerationMode,
    environmentForTestingPurposes: StreamTableEnvironment,
    override val watermarkStrategyOptions: WatermarkStrategyOptions
) extends FlinkSource
    with Serializable
    // These mixins below are for scenario testing mechanism using source-specific test data format
    with FlinkSourceTestSupport[Row]
    with TestDataGenerator
    with TestWithParametersSupport[Row]
    with CustomizableContextInitializerSource[Row]
    // end
    with LiveDataProvider
    with WithWatermarkStrategyOptions
    with LazyLogging {

  override def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    val tableEnv = StreamTableEnvironment.create(env)
    flinkDataDefinition.registerIn(tableEnv).orFail

    val selectQuery = tableEnv.from(tableDefinition.tableId.toString)

    val finalQuery = flinkNodeContext.componentUseContext.deploymentData
      .flatMap(_.get(SQL_EXPRESSION_PARAMETER_NAME))
      .collect { case sqlExpression =>
        tableEnv.executeSql(
          s"CREATE TEMPORARY VIEW $filteringInternalViewName AS SELECT * FROM ${tableDefinition.tableId} WHERE $sqlExpression"
        )
        tableEnv
          .from(filteringInternalViewName)
      }
      .getOrElse(selectQuery)

    val streamOfRow =
      tableEnv.toDataStream(finalQuery).setUidAndName(flinkNodeContext.nodeId.value, flinkNodeContext.nodeName.value)

    streamOfRow
      .flatMap(
        ContextInitializingFunction(
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext,
          watermarkStrategyOptions.eventTimeLazyParam,
          flinkNodeContext.lazyParameterHelper,
          contextInitializer
        ),
        FlinkWatermarkStrategyRuntimeHandler.contextInitializingFunctionOutputTypeInfo(
          flinkNodeContext.asOneOutputContext
        )
      )
      .assignTimestampsAndWatermarks(
        FlinkWatermarkStrategyRuntimeHandler.watermarkStrategy(watermarkStrategyOptions)
      )
      .map((ctxWithEventTime: ContextWithEventTime) => ctxWithEventTime.context, flinkNodeContext.contextTypeInfo)
  }

  override val contextInitializer: ContextInitializer[Row] =
    new BasicContextInitializer[Row](tableDefinition.sourceRowDataType.getLogicalType.toTypingResult)

  override def testParametersDefinition: List[Parameter] =
    fieldsWithoutComputedColumns
      .map(field => Parameter(ParameterName(field.getName), field.getDataType.getLogicalType.toTypingResult))

  override def parametersToTestData(params: Map[ParameterName, AnyRef]): Row = {
    val row = Row.withNames()
    params.foreach { case (paramName, value) =>
      row.setField(paramName.value, value)
    }
    row
  }

  override def watermarkStrategyForTest: Option[WatermarkStrategy[Row]] = None

  override def testRecordParser: TestRecordParser[Row] = {
    val tableDataParserSchema = {
      val columnsWithMetadataAsPersisted = tableDefinition.schema.getColumns.asScala.map {
        case p: PhysicalColumn => p
        case c: ComputedColumn => c
        case m: MetadataColumn => m.toPhysical
        case other             => throw new IllegalArgumentException(s"Unknown column type: ${other.getClass}")
      }.asJava
      Schema
        .newBuilder()
        .fromResolvedSchema(tableDefinition.schema.withColumns(columnsWithMetadataAsPersisted))
        .build()
    }
    (testRecords: List[TestRecord]) =>
      new FlinkMiniClusterTableOperations(environmentForTestingPurposes)
        .parseTestRecords(testRecords, tableDataParserSchema)
  }

  override def generateTestData(maxNumberOfRecords: Int): TestData = {
    val generateDataSchema = {
      val dataType = DataTypes.ROW(fieldsWithoutComputedColumns: _*)
      Schema.newBuilder().fromRowDataType(dataType).build()
    }
    val tableOps = new FlinkMiniClusterTableOperations(environmentForTestingPurposes)
    testDataGenerationMode match {
      case TestDataGenerationMode.Random =>
        tableOps.generateRandomTestData(
          amount = maxNumberOfRecords,
          schema = generateDataSchema
        )
      case TestDataGenerationMode.Live =>
        tableOps.generateLiveTestData(
          limit = maxNumberOfRecords,
          schema = generateDataSchema,
          tableId = tableDefinition.tableId
        )
    }
  }

  override def fetchLiveData(maxNumberOfRecords: Int): DataRecords = {
    val records = environmentForTestingPurposes
      .from(tableDefinition.tableId.toString)
      .limit(maxNumberOfRecords)
      .execute()
      .collect()
      .asScala
      .toList
      .map { row =>
        DataRecord(
          Map(VariableConstants.InputVariableName -> row),
          upstreamTimestamp = extractTimezoneAwareRowtime(row)
        )
      }
    DataRecords(records)
  }

  private def extractTimezoneAwareRowtime(row: Row) = {
    tableDefinition.singleColumnWithTimezoneAwareRowtime.map(_.getName).map(row.getField).flatMap {
      case instant: Instant =>
        Some(instant)
      // This is not tested because TIMESTAMP WITH TIME ZONE is not supported by flink sql https://issues.apache.org/jira/browse/FLINK-20869
      case offsetDateTime: OffsetDateTime =>
        Some(offsetDateTime.toInstant)
      case other =>
        logger.warn(
          s"For ${tableDefinition.singleColumnWithTimezoneAwareRowtime.map(_.getName).getOrElse("<unknown>")} column in ${tableDefinition.tableId}: " +
            s"timestamp of ${other.getClass.getName} type is not supported. Timestamp field will be omitted from returned live data"
        )
        None
    }
  }

  // We don't want to generate data for computed columns - they will be added during parsing of test data
  private def fieldsWithoutComputedColumns: List[DataTypes.Field] =
    tableDefinition.schema.toRowDataTypeFields(c => !c.isInstanceOf[ComputedColumn])

}

object TableSource {
  private val filteringInternalViewName = "filteringView"
  val SQL_EXPRESSION_PARAMETER_NAME     = "sqlExpression"
}
