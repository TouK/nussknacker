package pl.touk.nussknacker.engine.flink.table.sink

import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.types.logical.RowType
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.table.LogicalTypesConversions.LogicalTypeConverter
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions.TypingResultExtension

import scala.jdk.CollectionConverters._

class TableSink(
    tableDefinition: TableDefinition,
    sqlStatements: List[SqlStatement],
    value: LazyParameter[AnyRef]
) extends FlinkSink {

  override type Value = AnyRef

  override def prepareValue(
      dataStream: DataStream[Context],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[Value]] = {
    dataStream.flatMap(
      flinkNodeContext.lazyParameterHelper.lazyMapFunction(value),
      flinkNodeContext.valueWithContextInfo.forType(value.returnType)
    )
  }

  override def registerSink(
      dataStream: DataStream[ValueWithContext[Value]],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSink[_] = {
    val env      = dataStream.getExecutionEnvironment
    val tableEnv = StreamTableEnvironment.create(env)

    /*
      DataStream to Table transformation:
      1. Map the dataStream[any record type] to dataStream[Row]
      2. Map dataStream[Row] to table
      3. Add sink table to environment
      4. Insert the input value table into the sink table in the correct order of column names
      5. Put the insert operation in the statementSet and do attachAsDataStream on it
      6. Continue with a DiscardingSink as DataStream
     */
    val streamOfRows: SingleOutputStreamOperator[Row] = dataStream
      .map(
        { (valueWithContext: ValueWithContext[Value]) =>
          // We validated that target type is Record so we can cast to this type
          RowConversions.toRowNested(valueWithContext.value).asInstanceOf[Row]
        },
        flinkNodeContext.typeInformationDetection.forType(value.returnType.toRowNested)
      )

    val inputValueTable = tableEnv
      .fromDataStream(streamOfRows)
      .select(tableDefinition.sinkRowDataType.getLogicalType.toRowTypeUnsafe.getFieldNames.asScala.toList.map($): _*)

    sqlStatements.foreach(tableEnv.executeSql)

    val statementSet = tableEnv.createStatementSet()
    statementSet.add(inputValueTable.insertInto(s"`${tableDefinition.tableName}`"))
    statementSet.attachAsDataStream()

    /*
      Flink docs show something like this when integrating table api with inserts into dataStream. For details read:
      https://nightlies.apache.org/flink/flink-docs-master/docs/dev/table/data_stream_api/.
     */
    dataStream.addSink(new DiscardingSink())
  }

}
