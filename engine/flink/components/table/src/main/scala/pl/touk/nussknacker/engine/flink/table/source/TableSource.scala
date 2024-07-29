package pl.touk.nussknacker.engine.flink.table.source;

import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
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
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.source.TableSource._
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions

class TableSource(
    tableDefinition: TableDefinition,
    sqlStatements: List[SqlStatement],
    enableFlinkBatchExecutionMode: Boolean,
) extends StandardFlinkSource[RECORD]
    with TestWithParametersSupport[RECORD]
    with FlinkSourceTestSupport[RECORD]
    with TestDataGenerator {

  import scala.jdk.CollectionConverters._

  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[RECORD] = {
    // TODO: move this to flink-executor - ideally should set be near level of ExecutionConfigPreparer
    if (enableFlinkBatchExecutionMode) {
      env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    }
    val tableEnv = StreamTableEnvironment.create(env);

    sqlStatements.foreach(tableEnv.executeSql)

    val selectQuery = tableEnv.from(s"`${tableDefinition.tableName}`")

    val finalQuery = flinkNodeContext.nodeDeploymentData
      .map { case SqlFilteringExpression(sqlExpression) =>
        tableEnv.executeSql(
          s"CREATE TEMPORARY VIEW $filteringInternalViewName AS SELECT * FROM `${tableDefinition.tableName}` WHERE $sqlExpression"
        )
        tableEnv.from(filteringInternalViewName)
      }
      .getOrElse(selectQuery)

    val streamOfRows: DataStream[Row] = tableEnv.toDataStream(finalQuery)

    val streamOfMaps = streamOfRows
      .map(RowConversions.rowToMap)
      .returns(classOf[RECORD])

    new DataStreamSource(streamOfMaps)
  }

  override val contextInitializer: ContextInitializer[RECORD] = new BasicContextInitializer[RECORD](Typed[RECORD])

  override def testParametersDefinition: List[Parameter] =
    tableDefinition.columns.map(c => Parameter(ParameterName(c.columnName), c.typingResult))

  override def parametersToTestData(params: Map[ParameterName, AnyRef]): RECORD = params.map {
    case (paramName, value) => paramName.value -> value.asInstanceOf[Any]
  }.asJava

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[RECORD]] = None

  override def typeInformation: TypeInformation[RECORD] = TypeInformation.of(classOf[RECORD])

  override def testRecordParser: TestRecordParser[RECORD] = (testRecords: List[TestRecord]) =>
    FlinkMiniClusterTableOperations.parseTestRecords(testRecords, tableDefinition.toFlinkSchema)

  override def generateTestData(size: Int): TestData =
    FlinkMiniClusterTableOperations.generateTestData(size, tableDefinition.toFlinkSchema)
}

object TableSource {
  type RECORD = java.util.Map[String, Any]
  private val filteringInternalViewName = "filteringView"
}
