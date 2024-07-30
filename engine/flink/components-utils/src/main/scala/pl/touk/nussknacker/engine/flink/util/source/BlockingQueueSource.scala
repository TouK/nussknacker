package pl.touk.nussknacker.engine.flink.util.source

import com.github.ghik.silencer.silent
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.AssignerWithPunctuatedWatermarks
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.BasicContextInitializer
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkContextInitializingFunction,
  FlinkCustomNodeContext,
  FlinkSource
}
import pl.touk.nussknacker.engine.flink.util.timestamp.BoundedOutOfOrdernessPunctuatedExtractor

import java.time.Duration
import java.util.UUID
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/**
  * This source allow to add elements after creation or decide when input stream is finished. It also emit watermark after each added element.
  */
@silent("deprecated")
class BlockingQueueSource[T](returnType: TypingResult, timestampAssigner: AssignerWithPunctuatedWatermarks[T])
    extends FlinkSource
    with Serializable {

  private val id = UUID.randomUUID().toString

  def add(elements: T*): Boolean = BlockingQueueSource.getForId[T](id).addAll(elements.map(Some(_)).asJava)

  def finish(): Boolean = BlockingQueueSource.getForId[T](id).add(None)

  private val contextInitializer = new BasicContextInitializer[T](returnType)

  private def flinkSourceFunction: SourceFunction[T] = {
    // extracted for serialization purpose
    val copyOfAssigner = timestampAssigner
    val copyOfId       = id
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

  override def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    env
      .addSource(flinkSourceFunction, flinkNodeContext.typeInformationDetection.forType[T](returnType))
      .name(s"${flinkNodeContext.metaData.name}-${flinkNodeContext.nodeId}-source")
      .map(
        new FlinkContextInitializingFunction(
          contextInitializer,
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )
  }

}

object BlockingQueueSource {

  private[this] val queueById = TrieMap.empty[String, BlockingQueue[Option[_]]]

  private def getForId[T](id: String): BlockingQueue[Option[T]] =
    queueById.getOrElseUpdate(id, new LinkedBlockingQueue).asInstanceOf[BlockingQueue[Option[T]]]

  def create[T: ClassTag](
      extractTimestampFun: T => Long,
      maxOutOfOrderness: Duration
  ): BlockingQueueSource[T] = {
    val assigner = new BoundedOutOfOrdernessPunctuatedExtractor[T](maxOutOfOrderness.toMillis) {
      override def extractTimestamp(element: T, recordTimestamp: Long): Long = extractTimestampFun(element)
    }
    new BlockingQueueSource[T](Typed.typedClass[T], assigner)
  }

}
