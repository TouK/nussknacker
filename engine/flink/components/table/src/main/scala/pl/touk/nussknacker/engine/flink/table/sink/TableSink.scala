package pl.touk.nussknacker.engine.flink.table.sink

import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement

class TableSink(
    tableDefinition: TableDefinition,
    sqlStatements: List[SqlStatement],
    value: LazyParameter[Row]
) extends FlinkSink {

  override type Value = Row

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
      1. Map dataStream[Row] to table with the same column names order as in output table
      2. Add sink table to environment
      3. Insert the input value table into the sink table
      4. Put the insert operation in the statementSet and do attachAsDataStream on it
      5. Continue with a DiscardingSink as DataStream
     */
    val streamOfRows: SingleOutputStreamOperator[Row] = dataStream
      .map(
        { (valueWithContext: ValueWithContext[Value]) =>
          valueWithContext.value
        },
        flinkNodeContext.typeInformationDetection.forType(value.returnType)
      )

    val inputValueTable = tableEnv.fromDataStream(streamOfRows).select(tableDefinition.columnNames.map($): _*)

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
