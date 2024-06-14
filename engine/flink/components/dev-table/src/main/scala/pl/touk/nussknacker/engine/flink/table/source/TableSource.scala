package pl.touk.nussknacker.engine.flink.table.source;

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.{Configuration, CoreOptions, DeploymentOptions, PipelineOptions}
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.{EnvironmentSettings, TableEnvironment}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.delegation.ExecutorFactory
import org.apache.flink.table.factories.FactoryUtil
import org.apache.flink.types.Row
import org.apache.flink.util.Preconditions.checkNotNull
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
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

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

    val selectQuery = tableEnv.from(tableDefinition.tableName)

    val finalQuery = flinkNodeContext.nodeDeploymentData
      .map { case SqlFilteringExpression(sqlExpression) =>
        tableEnv.executeSql(
          s"CREATE TEMPORARY VIEW $filteringInternalViewName AS SELECT * FROM ${tableDefinition.tableName} WHERE $sqlExpression"
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

//  TODO: add implementation during task with test from file
  override def testRecordParser: TestRecordParser[RECORD] = ???

  override def generateTestData(size: Int): TestData = {

    // TODO: extract classpath-extracting method instead of creating modelClassLoader
    // TODO: check what we need to load - for tests we need more than
    val classPathUrls = ModelClassLoader(List("components/flink-dev/flinkTable.jar"), None).urls

    // setting context classloader because Flink in multiple places relies on it and without this temporary override it doesnt have
    // the necessary classes
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      val effectiveConfiguration = new Configuration()

      // parent-first - otherwise linkage error with 'org.apache.commons.math3.random.RandomDataGenerator'
      effectiveConfiguration.set(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")

      // without this, on the task level the classloader is basically empty
      effectiveConfiguration.set(
        PipelineOptions.CLASSPATHS,
        classPathUrls.map(_.toString).asJava
      )

      val env      = StreamExecutionEnvironment.getExecutionEnvironment(effectiveConfiguration)
      val tableEnv = StreamTableEnvironment.create(env)

      sqlStatements.foreach(tableEnv.executeSql)
      val tableWithLimit = tableEnv.from(tableDefinition.tableName).limit(size)

      val rowsIterator = tableEnv.toDataStream(tableWithLimit).executeAndCollect()
      val rowsList     = scala.collection.mutable.ArrayBuffer.empty[Row]
      while (rowsIterator.hasNext) {
        rowsList.append(rowsIterator.next())
      }

      // TODO: check if closing like this is ok, some IllegalStateExceptions get logged
      rowsIterator.close()
      env.close()

      // TODO: is this way of encoding ok? fail on unknown?
      val encoder = BestEffortJsonEncoder(failOnUnknown = false, classLoader = getClass.getClassLoader)
      val testRecords = rowsList.toList.map(row =>
        TestRecord(
          // TODO: make the field order deterministic
          encoder.encode(
            row.getFieldNames(true).asScala.map(fName => fName -> row.getField(fName)).toMap
          )
        )
      )

      TestData(testRecords)
    }
  }

}

object TableSource {
  private type RECORD = java.util.Map[String, Any]
  private val filteringInternalViewName = "filteringView"
}
