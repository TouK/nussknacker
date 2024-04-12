package pl.touk.nussknacker.engine.management.sample.source

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkStandardSourceUtils,
  StandardFlinkSource
}
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator

class OneSource extends StandardFlinkSource[String] {

  @silent("deprecated")
  override def initialSourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[String] = {
    val flinkSourceFunction: SourceFunction[String] = new SourceFunction[String] {
      var run     = true
      var emitted = false

      override def cancel(): Unit = {
        run = false
      }

      override def run(ctx: SourceContext[String]): Unit = {
        while (run) {
          if (!emitted) ctx.collect(DevProcessConfigCreator.oneElementValue)
          emitted = true
          Thread.sleep(1000)
        }
      }
    }
    FlinkStandardSourceUtils.createSourceStream(
      env = env,
      sourceFunction = flinkSourceFunction,
      typeInformation = TypeInformation.of(classOf[String])
    )
  }

}
