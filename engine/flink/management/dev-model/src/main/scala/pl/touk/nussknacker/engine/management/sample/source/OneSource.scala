package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.source._
import org.apache.flink.core.io.InputStatus
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.source.SingleSplitSource
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator

class OneSource extends FlinkSource with CustomizableContextInitializerSource[String] {

  override final def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    env
      .fromSource(
        flinkSource,
        WatermarkStrategy.noWatermarks(),
        flinkNodeContext.nodeId.value,
        TypeInformation.of(classOf[String])
      )
      .uid(flinkNodeContext.nodeId.value)
      .name(flinkNodeContext.nodeName.value)
      .map(
        new FlinkContextInitializingFunction(
          contextInitializer,
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )
  }

  private def flinkSource: Source[String, _, _] = {
    new SingleSplitSource[String] {

      override def getBoundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED

      override def createReader(
          readerContext: SourceReaderContext
      ): SourceReader[String, SingleSplitSource.SingleSplit] = new SingleSplitSource.Reader[String] {

        var emitted = false

        override def pollNext(output: ReaderOutput[String]): InputStatus = {
          if (!emitted) output.collect(DevProcessConfigCreator.oneElementValue)
          emitted = true
          InputStatus.NOTHING_AVAILABLE
        }
      }
    }
  }

}
