package pl.touk.nussknacker.engine.flink.util.source

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.AssignerWithPunctuatedWatermarks
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}

import scala.annotation.nowarn

/**
 * This source in contrary to `CollectionSource` emit watermark after each element. It is important feature during tests if you want to make them deterministic.
 */
@silent("deprecated")
@nowarn("deprecated")
class EmitWatermarkAfterEachElementCollectionSource[T: TypeInformation](list: Seq[T],
                                                                        timestampAssigner: AssignerWithPunctuatedWatermarks[T]) extends FlinkSource[T] {

  private val flinkSourceFunction: SourceFunction[T] = {
    // extracted for serialization purpose
    val seq = list.toIndexedSeq
    val copyOfAssigner = timestampAssigner
    new SourceFunction[T] {
      private var toConsumeIndex = 0

      @volatile private var isRunning = true

      override def run(ctx: SourceFunction.SourceContext[T]): Unit = {
        while (isRunning && toConsumeIndex < seq.size) {
          val element = seq(toConsumeIndex)
          val timestamp = copyOfAssigner.extractTimestamp(element, -1)
          ctx.collectWithTimestamp(element, timestamp)

          val watermark = copyOfAssigner.checkAndGetNextWatermark(element, timestamp)
          ctx.emitWatermark(watermark)

          toConsumeIndex += 1
        }
      }

      override def cancel(): Unit = isRunning = false

    }
  }

  override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[T] = {
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env
      .addSource(flinkSourceFunction)
      .name(s"${flinkNodeContext.metaData.id}-${flinkNodeContext.nodeId}-source")
  }

  override def typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

  // we already extract timestamp and assign watermark in the source
  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[T]]
    = Some(new LegacyTimestampWatermarkHandler[T](timestampAssigner))


}