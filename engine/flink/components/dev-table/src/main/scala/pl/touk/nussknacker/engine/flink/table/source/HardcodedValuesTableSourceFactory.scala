package pl.touk.nussknacker.engine.flink.table.source

import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.api.Expressions.row
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkContextInitializingFunction,
  FlinkCustomNodeContext,
  FlinkSource
}
import pl.touk.nussknacker.engine.flink.table.HardcodedSchema
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory._

object HardcodedValuesTableSourceFactory extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def invoke(): Source = {
    new HardcodedValuesSource()
  }

  private class HardcodedValuesSource extends FlinkSource with ReturningType {

    override def sourceStream(
        env: StreamExecutionEnvironment,
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[Context] = {
      val tableEnv = StreamTableEnvironment.create(env);

      val table = tableEnv.fromValues(
        DataTypes.ROW(DataTypes.FIELD("someInt", DataTypes.INT()), DataTypes.FIELD("someString", DataTypes.STRING())),
        row(1, "AAA"),
        row(2, "BBB")
      )

      val streamOfRows: DataStream[Row] = tableEnv.toDataStream(table)

      val streamOfMaps = streamOfRows
        .map(r => { HardcodedSchema.MapRowConversion.toMap(r): RECORD })
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
