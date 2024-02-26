package pl.touk.nussknacker.engine.flink.table.source

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
import pl.touk.nussknacker.engine.flink.table.TableSourceConfig
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory._

class ConfigurableTableSourceFactory(config: TableSourceConfig) extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def invoke(): Source = {
    new TableSource()
  }

  private class TableSource extends FlinkSource with ReturningType {

    import scala.jdk.CollectionConverters._

    override def sourceStream(
        env: StreamExecutionEnvironment,
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[Context] = {
      val tableEnv = StreamTableEnvironment.create(env);

      val tableName = "some_table_name"

      addTableToEnv(tableEnv, tableName)
      val table = tableEnv.from(tableName)

      val streamOfRows: DataStream[Row] = tableEnv.toDataStream(table)

      val streamOfMaps = streamOfRows
        .map(r => {
          val intVal    = r.getFieldAs[Int]("someInt")
          val stringVal = r.getFieldAs[String]("someString")
          val fields    = Map("someInt" -> intVal, "someString" -> stringVal)
          new java.util.HashMap[String, Any](fields.asJava): RECORD
        })
        .returns(classOf[RECORD])

      val contextStream = streamOfMaps.map(
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
        tableName: String
    ): Unit = {
      val tableDescriptor = TableDescriptor
        .forConnector(config.connector)
        .format(config.format)
        .schema(
          Schema
            .newBuilder()
            .column("someInt", DataTypes.INT())
            .column("someString", DataTypes.STRING())
            .build()
        )

      config.options.foreach { case (key, value) =>
        tableDescriptor.option(key, value)
      }

      val tableDescriptorFilled = tableDescriptor.build()

      tableEnv.createTable(tableName, tableDescriptorFilled)
    }

    override def returnType: typing.TypedObjectTypingResult = {
      Typed.record(Map("someInt" -> Typed[Integer], "someString" -> Typed[String]))
    }

  }

}
