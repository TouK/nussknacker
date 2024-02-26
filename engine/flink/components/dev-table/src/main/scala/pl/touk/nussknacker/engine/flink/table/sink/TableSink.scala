package pl.touk.nussknacker.engine.flink.table.sink

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.{DataTypes, Schema, Table, TableDescriptor}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper,
  FlinkSink
}
import pl.touk.nussknacker.engine.flink.table.DataSourceConfig

class TableSink(config: DataSourceConfig, value: LazyParameter[AnyRef]) extends FlinkSink {

  override type Value   = AnyRef
  protected type RECORD = java.util.Map[String, Any]

  // TODO: check if this is necessary
  private def typeResult: TypingResult = Typed.record(Map("someInt" -> Typed[Integer], "someString" -> Typed[String]))

  override def prepareValue(
      ds: DataStream[Context],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[Value]] = {
    ds.flatMap(
      valueFunction(flinkNodeContext.lazyParameterHelper),
      flinkNodeContext.valueWithContextInfo.forType(typeResult)
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
      dataStream.map(a => {
        val mapOfAny = a.value.asInstanceOf[RECORD]
        val row      = Row.withNames()

        val stringVal: String = mapOfAny.get("someString").asInstanceOf[String]
        val intVal: Int       = mapOfAny.get("someInt").asInstanceOf[Int]

        row.setField("someString", stringVal)
        row.setField("someInt", intVal)
        row
      })

    // TODO: avoid this step by mapping datastream directly without this intermediate table with nested row
    val nestedRowSchema = Schema
      .newBuilder()
      .column(
        "f0",
        DataTypes.ROW(
          DataTypes.FIELD("someString", DataTypes.STRING()),
          DataTypes.FIELD("someInt", DataTypes.INT()),
        )
      )
      .build()
    val tableWithNestedRow: Table = tableEnv.fromDataStream(
      streamOfRows,
      nestedRowSchema
    )

    val flatInputValueTable = tableWithNestedRow
      .select($("f0").get("someString").as("someString"), $("f0").get("someInt").as("someInt"))
    flatInputValueTable.printSchema()

    // Registering output table
    // TODO: remove duplication with source
    val sinkTableDescriptorBuilder = TableDescriptor
      .forConnector(config.connector)
      .format(config.format)
      .schema(
        Schema
          .newBuilder()
          .column("someString", DataTypes.STRING())
          .column("someInt", DataTypes.INT())
          .build()
      )
    config.options.foreach { case (key, value) =>
      sinkTableDescriptorBuilder.option(key, value)
    }
    val sinkTableDescriptor = sinkTableDescriptorBuilder.build()

    tableEnv.createTable(outputTableName, sinkTableDescriptor)

    // Adding insert statements to dataStream
    val statementSet = tableEnv.createStatementSet();
    statementSet.add(flatInputValueTable.insertInto(outputTableName))
    statementSet.attachAsDataStream()

    // TODO: check if this is ok. Flink docs show something like this when integrating table api into datastream
    dataStream.addSink(new DiscardingSink())
  }

}
