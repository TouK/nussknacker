package pl.touk.nussknacker.engine.flink.table.kafka

import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink}
import org.apache.flink.streaming.api.functions.sink.DiscardingSink
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.{DataTypes, Schema, TableDescriptor}
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.table.TableApiSourceFactoryMixin
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink.valueFunction

class KafkaSinkFactory(config: KafkaTableApiConfig)
    extends SinkFactory
    with UnboundedStreamComponent
    with TableApiSourceFactoryMixin {

  @MethodToInvoke
  def invoke(): Sink = {
    new KafkaSink()
  }

  private class KafkaSink extends FlinkSink {

    def typeResult: TypingResult = Unknown

    type Value = AnyRef

    override def prepareValue(
        dataStream: DataStream[Context],
        flinkCustomNodeContext: FlinkCustomNodeContext
    ): DataStream[ValueWithContext[Value]] =
      dataStream.flatMap(
        valueFunction(flinkCustomNodeContext.lazyParameterHelper),
        flinkCustomNodeContext.valueWithContextInfo.forType(typeResult)
      )

    override def registerSink(
        dataStream: DataStream[ValueWithContext[Value]],
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStreamSink[_] = {
      val env            = dataStream.getExecutionEnvironment
      val tableEnv       = StreamTableEnvironment.create(env)
      val tableName      = config.topic
      val kafkaTopicName = "output1"

      // TODO: This is wrong - try:
      //  - getting a table with the data to be inserted into the sink
      //  - define a sink as TableDescriptor
      //  - insert the table straight into the sink table using descriptor
      // TODO: probably parameters for schema will require this being a dynamic component
      addTableToEnv(tableEnv, flinkNodeContext, tableName, kafkaTopicName)
      val table = tableEnv.from(tableName)

      // TODO: this doesn't work - this requires changes in how sinks are registered since we can't have 2 executes
      //  - try `statementSet.attachAsDataStream()`
      table.insertInto(tableName).execute()
      dataStream.addSink(new DiscardingSink())
    }

    private def addTableToEnv(
        tableEnv: StreamTableEnvironment,
        flinkNodeContext: FlinkCustomNodeContext,
        tableName: String,
        topicName: String
    ): Unit = {
      val tableDescriptor = TableDescriptor
        .forConnector("kafka")
        .option("topic", topicName)
        // TODO: handle consumer groups same with namespace naming strategy and consumer group specific naming strategy
        .option("properties.group.id", flinkNodeContext.jobData.metaData.name.value)
        .option("scan.startup.mode", "earliest-offset")
        .format("json")
        .schema(
          Schema
            .newBuilder()
            .column("id", DataTypes.INT())
            .column("name", DataTypes.STRING())
            .build()
        )

      config.kafkaProperties.foreach { case (key, value) =>
        tableDescriptor.option(s"properties.$key", value)
      }

      val tableDescriptorFilled = tableDescriptor.build()

      tableEnv.createTable(tableName, tableDescriptorFilled)
    }

  }

}
