package pl.touk.nussknacker.engine.flink.table.source

import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.table.{DataSourceConfig, HardcodedSchema}
import pl.touk.nussknacker.engine.flink.table.HardcodedSchema._
import pl.touk.nussknacker.engine.flink.table.TableUtils.buildTable
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory._

class ConfigurableTableSourceFactory(config: DataSourceConfig) extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def invoke(): Source = {
    new TableSource()
  }

  private class TableSource extends FlinkSource with ReturningType {

    override def sourceStream(
        env: StreamExecutionEnvironment,
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[Context] = {
      val tableEnv = StreamTableEnvironment.create(env);

      val tableName = "some_table_name"

      val tableDescriptorFilled = buildTable(config, schema)
      tableEnv.createTable(tableName, tableDescriptorFilled)
      val table = tableEnv.from(tableName)

      val streamOfRows: DataStream[Row] = tableEnv.toDataStream(table)

      val streamOfMaps = streamOfRows
        .map(r => { HardcodedSchema.toMap(r): RECORD })
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

    override val returnType: typing.TypingResult = HardcodedSchema.typingResult

  }

}
