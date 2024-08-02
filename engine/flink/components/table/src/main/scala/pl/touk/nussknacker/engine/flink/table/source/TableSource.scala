package pl.touk.nussknacker.engine.flink.table.source;

import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.{Schema, TableEnvironment}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.component.SqlFilteringExpression
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{
  BasicContextInitializer,
  ContextInitializer,
  TestDataGenerator,
  TestWithParametersSupport
}
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkSourceTestSupport,
  StandardFlinkSource
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.LogicalTypesConversions._
import pl.touk.nussknacker.engine.flink.table.source.TableSource._

class TableSource(
    tableDefinition: TableDefinition,
    sqlStatements: List[SqlStatement],
    enableFlinkBatchExecutionMode: Boolean,
    testDataGenerationMode: TestDataGenerationMode
) extends StandardFlinkSource[Row]
    with TestWithParametersSupport[Row]
    with FlinkSourceTestSupport[Row]
    with TestDataGenerator {

  private val sourceType = tableDefinition.physicalRowDataType.getLogicalType.toRowTypeUnsafe.toTypingResult

  private val schema = Schema.newBuilder().fromRowDataType(tableDefinition.physicalRowDataType).build()

  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Row] = {
    // TODO: move this to flink-executor - ideally should set be near level of ExecutionConfigPreparer
    if (enableFlinkBatchExecutionMode) {
      env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    }
    val tableEnv = StreamTableEnvironment.create(env)

    executeSqlDDL(sqlStatements, tableEnv)
    val selectQuery = tableEnv.from(s"`${tableDefinition.tableName}`")

    val finalQuery = flinkNodeContext.nodeDeploymentData
      .map { case SqlFilteringExpression(sqlExpression) =>
        tableEnv.executeSql(
          s"CREATE TEMPORARY VIEW $filteringInternalViewName AS SELECT * FROM `${tableDefinition.tableName}` WHERE $sqlExpression"
        )
        tableEnv
          .from(filteringInternalViewName)
      }
      .getOrElse(selectQuery)
      // We have to keep elements in the same order as in TypingInfo generated based on TypingResults, see TypingResultAwareTypeInformationDetection
      .select(sourceType.fields.keys.toList.sorted.map($): _*)

    tableEnv.toDataStream(finalQuery)
  }

  override val contextInitializer: ContextInitializer[Row] = new BasicContextInitializer[Row](Typed[Row])

  override def testParametersDefinition: List[Parameter] =
    sourceType.fields.toList.map(c => Parameter(ParameterName(c._1), c._2))

  override def parametersToTestData(params: Map[ParameterName, AnyRef]): Row = {
    val row = Row.withNames()
    params.foreach { case (paramName, value) =>
      row.setField(paramName.value, value)
    }
    row
  }

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[Row]] = None

  override def testRecordParser: TestRecordParser[Row] = (testRecords: List[TestRecord]) =>
    FlinkMiniClusterTableOperations.parseTestRecords(testRecords, schema)

  override def generateTestData(size: Int): TestData = {
    testDataGenerationMode match {
      case TestDataGenerationMode.Random =>
        FlinkMiniClusterTableOperations.generateRandomTestData(
          amount = size,
          schema = schema
        )
      case TestDataGenerationMode.Live =>
        FlinkMiniClusterTableOperations.generateLiveTestData(
          limit = size,
          schema = schema,
          sqlStatements = sqlStatements,
          tableName = tableDefinition.tableName
        )
    }
  }

}

object TableSource {
  private val filteringInternalViewName = "filteringView"

  private[source] def executeSqlDDL(
      sqlStatements: List[SqlStatement],
      tableEnv: TableEnvironment
  ): Unit = sqlStatements.foreach(tableEnv.executeSql)

}
