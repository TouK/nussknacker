package pl.touk.nussknacker.engine.management.javasample

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink

import scala.annotation.nowarn

class Objects extends Serializable {

  def source: WithCategories[SourceFactory] =
    WithCategories.anyCategory(
      SourceFactory.noParamUnboundedStreamFactory[Model](
        new FlinkSource with CustomizableContextInitializerSource[Model] {

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
              env: StreamExecutionEnvironment
          ): DataStreamSource[Model] = {
            env.addSource(
              new SourceFunction[Model] {
                override def cancel(): Unit = {}

                override def run(ctx: SourceFunction.SourceContext[Model]): Unit = {
                  while (true) {
                    Thread.sleep(10000)
                  }
                }

              },
              TypeInformation.of(classOf[Model])
            )
          }

        }
      )
    )

  def sink: WithCategories[SinkFactory] = WithCategories.anyCategory(SinkFactory.noParam(EmptySink))

}
