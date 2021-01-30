package pl.touk.nussknacker.engine.flink.util.source

import java.time.Duration
import java.util.UUID
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.AssignerWithPunctuatedWatermarks
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource, SourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.util.context.InitContextFunction
import pl.touk.nussknacker.engine.flink.util.timestamp.BoundedOutOfOrdernessPunctuatedExtractor

import scala.annotation.nowarn
import scala.collection.concurrent.TrieMap
import scala.collection.JavaConverters._

/**
 * This source allow to add elements after creation or decide when input stream is finished. It also emit watermark after each added element.
 */
@silent("deprecated")
@nowarn("deprecated")
class BlockingQueueSource[T: TypeInformation](timestampAssigner: AssignerWithPunctuatedWatermarks[T])
  extends FlinkSource[T] with SourceTestSupport[T] with Serializable {

  private val id = UUID.randomUUID().toString

  def add(elements: T*) = BlockingQueueSource.getForId[T](id).addAll(elements.map(Some(_)).asJava)

  def finish() = BlockingQueueSource.getForId[T](id).add(None)

  private def flinkSourceFunction: SourceFunction[T] = {
    // extracted for serialization purpose
    val copyOfAssigner = timestampAssigner
    val copyOfId = id
    new SourceFunction[T] {

      @volatile private var isRunning = true

      private val id = copyOfId

      override def run(ctx: SourceFunction.SourceContext[T]): Unit = {
        val queue = BlockingQueueSource.getForId[T](id)
        while (isRunning) {
          Option(queue.poll(100, TimeUnit.MILLISECONDS)).foreach {
            case Some(element) =>
              val timestamp = copyOfAssigner.extractTimestamp(element, -1)
              ctx.collectWithTimestamp(element, timestamp)

              val watermark = copyOfAssigner.checkAndGetNextWatermark(element, timestamp)
              ctx.emitWatermark(watermark)
            case None =>
              isRunning = false
          }
        }
      }

      override def cancel(): Unit = isRunning = false

    }
  }

  override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[Context] = {
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env
      .addSource(flinkSourceFunction)
      .name(s"${flinkNodeContext.metaData.id}-${flinkNodeContext.nodeId}-source")
      .map(new InitContextFunction[T](flinkNodeContext.metaData.id, flinkNodeContext.nodeId, None))(implicitly[TypeInformation[Context]])
  }

  override def typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

  // we already extract timestamp and assign watermark in the source
  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[T]]
    = Some(new LegacyTimestampWatermarkHandler[T](timestampAssigner))

}

object BlockingQueueSource {

  private[this] val queueById = TrieMap.empty[String, BlockingQueue[Option[_]]]

  private def getForId[T](id: String): BlockingQueue[Option[T]] =
    queueById.getOrElseUpdate(id, new LinkedBlockingQueue).asInstanceOf[BlockingQueue[Option[T]]]

  def create[T:TypeInformation](extractTimestampFun: T => Long, maxOutOfOrderness: Duration): BlockingQueueSource[T] = {
    val assigner = new BoundedOutOfOrdernessPunctuatedExtractor[T](maxOutOfOrderness.toMillis) {
      override def extractTimestamp(element: T, recordTimestamp: Long): Long = extractTimestampFun(element)
    }
    new BlockingQueueSource[T](assigner)
  }
}