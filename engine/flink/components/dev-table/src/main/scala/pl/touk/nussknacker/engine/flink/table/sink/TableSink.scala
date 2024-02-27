package pl.touk.nussknacker.engine.flink.table.sink

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.{DataTypes, Schema, Table}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper,
  FlinkSink
}
import pl.touk.nussknacker.engine.flink.table.HardcodedSchema.{intColumnName, stringColumnName}
import pl.touk.nussknacker.engine.flink.table.{DataSourceConfig, HardcodedSchema}
import pl.touk.nussknacker.engine.flink.table.TableUtils.buildTable

class TableSink(config: DataSourceConfig, value: LazyParameter[AnyRef]) extends FlinkSink {

  // TODO: check if there is a way to have a Map[String,Any] in a LazyParameter
  override type Value   = AnyRef
  protected type RECORD = java.util.Map[String, Any]

  override def prepareValue(
      dataStream: DataStream[Context],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[Value]] = {
    dataStream.flatMap(
      valueFunction(flinkNodeContext.lazyParameterHelper),
      flinkNodeContext.valueWithContextInfo.forType(HardcodedSchema.typingResult)
    )
  }

  private def valueFunction(
      helper: FlinkLazyParameterFunctionHelper
  ): FlatMapFunction[Context, ValueWithContext[Value]] =
    helper.lazyMapFunction(value)

  override def registerSink(
      dataStream: DataStream[ValueWithContext[Value]],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSink[_] = {
    val env             = dataStream.getExecutionEnvironment
    val tableEnv        = StreamTableEnvironment.create(env)
    val outputTableName = "some_sink_table_name"

    /*
      DataStream to Table transformation:
      1. Add sink table to environment
      2. Add a table for the input value
          - here we need to do conversion AnyRef -> Map[String,Any] -> Row -> Table
          - it begins with AnyRef until we get ValueWithContext[Map[String,Any]] or straight to ValueWithContext[Row]
          - the conversion to Row is a workaround for RAW type - don't see other solutions now
      3. Insert the input value table into the sink table
      4. Contain the insert into a statementSet and do attachAsDataStream
     */
    val streamOfRows: SingleOutputStreamOperator[Row] =
      dataStream.map(ctx => {
        val mapOfAny: java.util.Map[String, Any] = ctx.value.asInstanceOf[RECORD]
        HardcodedSchema.fromMap(mapOfAny)
      })

    val nestedRowColumnName = "f0"
    // TODO: avoid this step by mapping datastream directly without this intermediate table with nested row
    val nestedRowSchema = Schema
      .newBuilder()
      .column(
        nestedRowColumnName,
        DataTypes.ROW(
          DataTypes.FIELD(stringColumnName, DataTypes.STRING()),
          DataTypes.FIELD(intColumnName, DataTypes.INT()),
        )
      )
      .build()
    val tableWithNestedRow: Table = tableEnv.fromDataStream(
      streamOfRows,
      nestedRowSchema
    )

    val flatInputValueTable = tableWithNestedRow
      .select(
        $(nestedRowColumnName).get(stringColumnName).as(stringColumnName),
        $(nestedRowColumnName).get(intColumnName).as(intColumnName)
      )

    // Registering output table
    val sinkTableDescriptor = buildTable(config, HardcodedSchema.schema)
    tableEnv.createTable(outputTableName, sinkTableDescriptor)

    // Adding insert statements to dataStream
    val statementSet = tableEnv.createStatementSet();
    statementSet.add(flatInputValueTable.insertInto(outputTableName))
    statementSet.attachAsDataStream()

    // TODO: check if this is ok. Flink docs show something like this when integrating table api into datastream
    dataStream.addSink(new DiscardingSink())
  }

}
