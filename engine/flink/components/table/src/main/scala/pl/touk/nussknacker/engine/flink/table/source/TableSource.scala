package pl.touk.nussknacker.engine.flink.table.source;

import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.{DataTypes, Schema, TableEnvironment}
import org.apache.flink.table.catalog.Column.{ComputedColumn, MetadataColumn, PhysicalColumn}
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
import pl.touk.nussknacker.engine.flink.table.source.TableSource._
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._
import pl.touk.nussknacker.engine.flink.table.utils.SchemaExtensions._

import scala.jdk.CollectionConverters._

class TableSource(
    tableDefinition: TableDefinition,
    sqlStatements: List[SqlStatement],
    testDataGenerationMode: TestDataGenerationMode
) extends StandardFlinkSource[Row]
    with TestWithParametersSupport[Row]
    with FlinkSourceTestSupport[Row]
    with TestDataGenerator {

  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Row] = {
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

    tableEnv.toDataStream(finalQuery)
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

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[Row]] = None

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
      FlinkMiniClusterTableOperations.parseTestRecords(testRecords, tableDataParserSchema)
  }

  override def generateTestData(size: Int): TestData = {
    val generateDataSchema = {
      val dataType = DataTypes.ROW(fieldsWithoutComputedColumns: _*)
      Schema.newBuilder().fromRowDataType(dataType).build()
    }
    testDataGenerationMode match {
      case TestDataGenerationMode.Random =>
        FlinkMiniClusterTableOperations.generateRandomTestData(
          amount = size,
          schema = generateDataSchema
        )
      case TestDataGenerationMode.Live =>
        FlinkMiniClusterTableOperations.generateLiveTestData(
          limit = size,
          schema = generateDataSchema,
          sqlStatements = sqlStatements,
          tableName = tableDefinition.tableName
        )
    }
  }

  // We don't want to generate data for computed columns - they will be added during parsing of test data
  private def fieldsWithoutComputedColumns: List[DataTypes.Field] =
    tableDefinition.schema.toRowDataTypeFields(c => !c.isInstanceOf[ComputedColumn])

}

object TableSource {
  private val filteringInternalViewName = "filteringView"

  private[source] def executeSqlDDL(
      sqlStatements: List[SqlStatement],
      tableEnv: TableEnvironment
  ): Unit = sqlStatements.foreach(tableEnv.executeSql)

}
