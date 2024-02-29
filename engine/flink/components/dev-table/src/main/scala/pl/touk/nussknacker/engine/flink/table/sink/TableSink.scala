package pl.touk.nussknacker.engine.flink.table.sink

import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.{Schema, Table}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.table.HardcodedSchema.{intColumnName, stringColumnName}
import pl.touk.nussknacker.engine.flink.table.TableUtils.buildTableDescriptor
import pl.touk.nussknacker.engine.flink.table.{DataSourceConfig, HardcodedSchema}

class TableSink(config: DataSourceConfig, value: LazyParameter[java.util.Map[String, Any]]) extends FlinkSink {

  override type Value = java.util.Map[String, Any]

  override def prepareValue(
      dataStream: DataStream[Context],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[Value]] = {
    dataStream.flatMap(
      flinkNodeContext.lazyParameterHelper.lazyMapFunction(value),
      flinkNodeContext.valueWithContextInfo.forType(HardcodedSchema.typingResult)
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
    val streamOfRows: SingleOutputStreamOperator[Row] =
      dataStream.map(ctx => HardcodedSchema.MapRowConversion.fromMap(ctx.value))

    /*
      This "f0" value is name given by flink at conversion of one element stream. For details read:
      https://nightlies.apache.org/flink/flink-docs-master/docs/dev/table/data_stream_api/.
     */
    val nestedRowColumnName = "f0"
    // TODO: avoid this step by mapping datastream directly without this intermediate table with nested row
    val nestedRowSchema = Schema
      .newBuilder()
      .column(
        nestedRowColumnName,
        HardcodedSchema.rowDataType
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

    val sinkTableDescriptor = buildTableDescriptor(config, HardcodedSchema.schema)

    val statementSet = tableEnv.createStatementSet();
    statementSet.add(flatInputValueTable.insertInto(sinkTableDescriptor))
    statementSet.attachAsDataStream()

    /*
      Flink docs show something like this when integrating table api with inserts into dataStream. For details read:
      https://nightlies.apache.org/flink/flink-docs-master/docs/dev/table/data_stream_api/.
     */
    dataStream.addSink(new DiscardingSink())
  }

}
