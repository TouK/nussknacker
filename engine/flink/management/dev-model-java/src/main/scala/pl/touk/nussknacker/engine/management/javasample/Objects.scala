package pl.touk.nussknacker.engine.management.javasample

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkStandardSourceUtils,
  StandardFlinkSource
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink

class Objects extends Serializable {

  def source: WithCategories[SourceFactory] =
    WithCategories.anyCategory(SourceFactory.noParamUnboundedStreamFactory[Model](new StandardFlinkSource[Model] {

      @silent("deprecated")
      override def initialSourceStream(
          env: StreamExecutionEnvironment,
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStreamSource[Model] = {
        FlinkStandardSourceUtils.createSourceStream(
          env = env,
          sourceFunction = new SourceFunction[Model] {
            override def cancel(): Unit = {}
            override def run(ctx: SourceFunction.SourceContext[Model]): Unit = {
              while (true) {
                Thread.sleep(10000)
              }
            }
          },
          typeInformation = TypeInformation.of(classOf[Model])
        )
      }

      override def contextInitializer: ContextInitializer[Model] = new BasicContextInitializer[Model](Unknown)

      override def timestampAssigner: Option[TimestampWatermarkHandler[Model]] = None
    }))

  def sink: WithCategories[SinkFactory] = WithCategories.anyCategory(SinkFactory.noParam(EmptySink))

}
