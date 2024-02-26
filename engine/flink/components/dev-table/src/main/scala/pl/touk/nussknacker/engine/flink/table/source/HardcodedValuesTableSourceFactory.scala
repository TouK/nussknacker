package pl.touk.nussknacker.engine.flink.table.source

import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.Expressions.row
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory._

// TODO: Should be BoundedStreamComponent - change it after configuring batch Deployment Manager
object HardcodedValuesTableSourceFactory extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def invoke(): Source = {
    new HardcodedValuesSource()
  }

  private class HardcodedValuesSource extends FlinkSource with ReturningType {

    import scala.jdk.CollectionConverters._

    override def sourceStream(
        env: StreamExecutionEnvironment,
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[Context] = {
      val tableEnv = StreamTableEnvironment.create(env);

      val table = tableEnv.fromValues(
        row(1, "AAA"),
        row(2, "BBB")
      )

      val streamOfRows: DataStream[Row] = tableEnv.toDataStream(table)

      // TODO: infer returnType dynamically from table schema based on table.getResolvedSchema.getColumns
      val streamOfMaps = streamOfRows
        .map(r => {
          val intVal    = r.getFieldAs[Int](0)
          val stringVal = r.getFieldAs[String](1)
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

    override val returnType: typing.TypedObjectTypingResult = {
      Typed.record(Map("someInt" -> Typed[Integer], "someString" -> Typed[String]))
    }

  }

}
