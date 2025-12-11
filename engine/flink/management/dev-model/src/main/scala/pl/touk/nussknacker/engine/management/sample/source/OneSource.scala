package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator

import scala.annotation.nowarn

class OneSource extends FlinkSource with CustomizableContextInitializerSource[String] {

  override final def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    sourceStream(env)
      .setUidAndNameToNodeId(flinkNodeContext.nodeId)
      .map(
        new FlinkContextInitializingFunction(
          contextInitializer,
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )
  }

  @nowarn("cat=deprecation")
  private def sourceStream(
      env: StreamExecutionEnvironment,
  ): DataStreamSource[String] = {
    env.addSource(
      new SourceFunction[String] {
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

      },
      TypeInformation.of(classOf[String])
    )
  }

}
