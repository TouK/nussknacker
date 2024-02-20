package pl.touk.nussknacker.engine.flink.table.kafka

import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.{DataTypes, Schema, TableDescriptor}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.table.TableApiComponentFactoryMixin

class KafkaSourceFactory(config: KafkaTableApiConfig)
    extends SourceFactory
    with UnboundedStreamComponent
    with TableApiComponentFactoryMixin {

  @MethodToInvoke
  def invoke(): Source = {
    new KafkaSource()
  }

  private class KafkaSource extends FlinkSource with ReturningType {

    import scala.jdk.CollectionConverters._

    override def sourceStream(
        env: StreamExecutionEnvironment,
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[Context] = {
      val tableEnv = StreamTableEnvironment.create(env);

      val tableName = "kafkaSource"

      addTableToEnv(tableEnv, flinkNodeContext, tableName)
      val table = tableEnv.from(tableName)

      val rowStream: DataStream[Row] = tableEnv.toDataStream(table)

      // TODO: Types:
      //  - for catalogs / dynamic components: infer returnType dynamically from table schema based on table.getResolvedSchema.getColumns
      //  - for method based components - get schema from config
      val mappedToSchemaStream = rowStream
        .map(r => {
          val eInt    = r.getFieldAs[Int](0)
          val eString = r.getFieldAs[String](1)
          val fields  = Map("id" -> eInt, "name" -> eString)
          new java.util.HashMap[String, Any](fields.asJava): RECORD
        })
        .returns(classOf[RECORD])

      val contextStream = mappedToSchemaStream.map(
        new FlinkContextInitializingFunction(
          contextInitializer,
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )

      contextStream
    }

    private def addTableToEnv(
        tableEnv: StreamTableEnvironment,
        flinkNodeContext: FlinkCustomNodeContext,
        tableName: String
    ): Unit = {
      val tableDescriptor = TableDescriptor
        .forConnector("kafka")
        .option("topic", config.topic)
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

    // This gets displayed in FE suggestions
    override def returnType: typing.TypedObjectTypingResult = {
      Typed.record(Map("id" -> Typed[Integer], "name" -> Typed[String]))
    }

  }

}
