package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.connector.source.{Boundedness, ReaderOutput, SourceReader, SourceReaderContext}
import org.apache.flink.core.io.InputStatus
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.BasicContextInitializer
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkContextInitializingFunction,
  FlinkCustomNodeContext,
  FlinkSource
}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.util.source.BlockingQueueSource.BlockingQueueFlinkSource

import java.time.Duration
import java.util.UUID
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/**
  * This source allow to add elements after creation or decide when input stream is finished. It also emit watermark after each added element.
  */
class BlockingQueueSource[T](returnType: TypingResult, extractTimestampFun: T => Long, maxOutOfOrderness: Duration)
    extends FlinkSource
    with Serializable {

  private val id = UUID.randomUUID().toString

  def add(elements: T*): Boolean = BlockingQueueSource.getForId[T](id).addAll(elements.map(Some(_)).asJava)

  def finish(): Boolean = BlockingQueueSource.getForId[T](id).add(None)

  private val contextInitializer = new BasicContextInitializer[T](returnType)

  override def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    env
      .fromSource(
        new BlockingQueueFlinkSource[T](id, extractTimestampFun, maxOutOfOrderness),
        WatermarkStrategy.noWatermarks(),
        s"${flinkNodeContext.metaData.name}-${flinkNodeContext.nodeId}-source",
        TypeInformationDetection.instance.forType[T](returnType)
      )
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

  private class BlockingQueueFlinkSource[T](id: String, extractTimestampFun: T => Long, maxOutOfOrderness: Duration)
      extends SingleSplitSource[T] {

    override def getBoundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED

    override def createReader(readerContext: SourceReaderContext): SourceReader[T, SingleSplitSource.SingleSplit] =
      new SingleSplitSource.Reader[T] {

        private val queue = BlockingQueueSource.getForId[T](id)

        override def pollNext(output: ReaderOutput[T]): InputStatus = {
          Option(queue.poll(100, TimeUnit.MILLISECONDS)) match {
            case Some(Some(element)) =>
              val timestamp = extractTimestampFun(element)
              output.collect(element, timestamp)

              val watermark =
                new org.apache.flink.api.common.eventtime.Watermark(timestamp - maxOutOfOrderness.toMillis)
              output.emitWatermark(watermark)
              InputStatus.MORE_AVAILABLE
            case Some(None) =>
              InputStatus.END_OF_INPUT
            case None =>
              InputStatus.NOTHING_AVAILABLE
          }
        }

      }

  }

  def create[T: ClassTag](
      extractTimestampFun: T => Long,
      maxOutOfOrderness: Duration
  ): BlockingQueueSource[T] = {
    new BlockingQueueSource[T](Typed.typedClass[T], extractTimestampFun, maxOutOfOrderness)
  }

}
