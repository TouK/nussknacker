package pl.touk.nussknacker.engine.flink.util.transformer

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

import scala.jdk.CollectionConverters.mapAsJavaMapConverter

// TODO: Shouldn't be unbounded - this is just for easier local development
object BoundedSourceFactory extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def invoke(): Source = {
    new BoundedSource()
  }

  private class BoundedSource extends FlinkSource with ReturningType with TableApiComponent {

    override def sourceStream(
        env: StreamExecutionEnvironment,
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[Context] = {
      val tableEnv = StreamTableEnvironment.create(env);

      val table = tableEnv.fromValues(
        row(1, "ABC"),
        row(2, "DEF")
      )

      val rowStream: DataStream[Row] = tableEnv.toDataStream(table)

      val mappedToSchemaStream = rowStream
        .map(r => {
          val eInt    = r.getFieldAs[Int](0)
          val eString = r.getFieldAs[String](1)
          val fields  = Map("someInt" -> eInt, "someString" -> eString)

          val map: RECORD = new java.util.HashMap[String, Any](fields.asJava)
          map
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

    // This gets displayed in FE suggestions
    override def returnType: typing.TypedObjectTypingResult = {
      Typed.record(Map("someInt" -> Typed[Integer], "someString" -> Typed[String]))
    }

  }

}
