package pl.touk.nussknacker.engine.flink.util.source

import java.time.Duration

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.AssignerWithPunctuatedWatermarks
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.util.context.BasicFlinkContextInitializer
import pl.touk.nussknacker.engine.flink.util.timestamp.BoundedOutOfOrdernessPunctuatedExtractor

import scala.annotation.nowarn

/**
 * This source in contrary to `CollectionSource` emit watermark after each element. It is important feature during tests if you want to make them deterministic.
 */
@silent("deprecated")
@nowarn("cat=deprecation")
class EmitWatermarkAfterEachElementCollectionSource[T: TypeInformation](list: Seq[T],
                                                                        timestampAssigner: AssignerWithPunctuatedWatermarks[T])
  extends FlinkSource[T] {

  private val contextInitializer = new BasicFlinkContextInitializer[T]

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

  override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[Context] = {
    val typeInformationFromNodeContext = flinkNodeContext.typeInformationDetection.forContext(flinkNodeContext.validationContext.left.get)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env
      .addSource(flinkSourceFunction)
      .name(s"${flinkNodeContext.metaData.id}-${flinkNodeContext.nodeId}-source")
      .map(contextInitializer.initContext(flinkNodeContext.metaData.id, flinkNodeContext.nodeId))(typeInformationFromNodeContext)
  }

}

object EmitWatermarkAfterEachElementCollectionSource {

  def create[T:TypeInformation](elements: Seq[T], extractTimestampFun: T => Long, maxOutOfOrderness: Duration): EmitWatermarkAfterEachElementCollectionSource[T] = {
    val assigner = new BoundedOutOfOrdernessPunctuatedExtractor[T](maxOutOfOrderness.toMillis) {
      override def extractTimestamp(element: T, recordTimestamp: Long): Long = extractTimestampFun(element)
    }
    new EmitWatermarkAfterEachElementCollectionSource[T](elements, assigner)
  }

}