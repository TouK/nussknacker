package pl.touk.nussknacker.engine.management.javasample

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.source.{Boundedness, ReaderOutput, SourceReader, SourceReaderContext}
import org.apache.flink.core.io.InputStatus
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.SingleSplitSource

class Objects extends Serializable {

  def source: WithCategories[SourceFactory] =
    WithCategories.anyCategory(
      SourceFactory.noParamUnboundedStreamFactory[Model](
        new FlinkSource with CustomizableContextInitializerSource[Model] {

          override final def contextStream(
              env: StreamExecutionEnvironment,
              flinkNodeContext: FlinkCustomNodeContext
          ): DataStream[Context] = {
            env
              .fromSource(
                flinkSource,
                WatermarkStrategy.noWatermarks(),
                flinkNodeContext.nodeId.id,
                TypeInformation.of(classOf[Model])
              )
              .uid(flinkNodeContext.nodeId.id)
              .map(
                new FlinkContextInitializingFunction(
                  contextInitializer,
                  flinkNodeContext.nodeId,
                  flinkNodeContext.convertToEngineRuntimeContext
                ),
                flinkNodeContext.contextTypeInfo
              )
          }

          private def flinkSource: SingleSplitSource[Model] = {
            new SingleSplitSource[Model] {
              override def getBoundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED

              override def createReader(
                  readerContext: SourceReaderContext
              ): SourceReader[Model, SingleSplitSource.SingleSplit] =
                new SingleSplitSource.Reader[Model] {
                  override def pollNext(output: ReaderOutput[Model]): InputStatus = {
                    InputStatus.NOTHING_AVAILABLE
                  }
                }
            }
          }
        }
      )
    )

  def sink: WithCategories[SinkFactory] = WithCategories.anyCategory(SinkFactory.noParam(EmptySink))

}
