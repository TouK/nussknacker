package pl.touk.nussknacker.engine.flink.table.sink

import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import org.apache.flink.table.api.Table
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.utils.NestedRowConversions._
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions.mapToRowUnsafe

class SqlSink(
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
      flinkNodeContext.valueWithContextInfo.forType(tableDefinition.typingResult)
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
      1. Map the dataStream to dataStream[Row] to fit schema in later step
      2. Map dataStream[Row] to intermediate table with row nested inside "f0" column. This deals with converting from
         RAW type - don't see other simple solutions
      3. Map the table with nesting to a flattened table
      4. Add sink table to environment
      5. Insert the input value table into the sink table
      6. Put the insert operation in the statementSet and do attachAsDataStream on it
      7. Continue with a DiscardingSink as DataStream
     */
    val streamOfRows: SingleOutputStreamOperator[Row] = dataStream
      .map(valueWithContext => {
        mapToRowUnsafe(valueWithContext.value.asInstanceOf[java.util.Map[String, Any]], tableDefinition.columns)
      })

    /*
     This "f0" value is name given by flink at conversion of one element stream. For details read:
     https://nightlies.apache.org/flink/flink-docs-master/docs/dev/table/data_stream_api/.
     */
    // TODO: avoid this step by mapping DataStream directly without this intermediate table with nested row
    val nestedRowSchema = columnsToSingleRowFlinkSchema(tableDefinition.columns)

    val tableWithNestedRow: Table = tableEnv.fromDataStream(
      streamOfRows,
      nestedRowSchema
    )

    val inputValueTable = tableWithNestedRow
      .select(flatteningSelectExpressions(tableDefinition.columns): _*)

    sqlStatements.foreach(tableEnv.executeSql)

    val statementSet = tableEnv.createStatementSet()
    statementSet.add(inputValueTable.insertInto(tableDefinition.tableName))
    statementSet.attachAsDataStream()

    /*
      Flink docs show something like this when integrating table api with inserts into dataStream. For details read:
      https://nightlies.apache.org/flink/flink-docs-master/docs/dev/table/data_stream_api/.
     */
    dataStream.addSink(new DiscardingSink())
  }

}
