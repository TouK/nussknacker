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

    val streamOfRows: SingleOutputStreamOperator[Row] =
      dataStream
        .map(valueWithContext => valueWithContext.value.asInstanceOf[java.util.Map[String, Any]])
        .returns(classOf[java.util.Map[String, Any]])
        .map(value => { mapToRowUnsafe(value, tableDefinition.columns) })

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

    dataStream.addSink(new DiscardingSink())
  }

}
