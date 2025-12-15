package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.eventtime.Watermark
import org.apache.flink.api.connector.source.{Boundedness, ReaderOutput, SourceReader, SourceReaderContext}
import org.apache.flink.core.io.InputStatus

object StaticSource extends SingleSplitSource[String] {

  @volatile var buffer = List[Command]()

  @volatile var running = true

  override def getBoundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED

  override def createReader(readerContext: SourceReaderContext): SourceReader[String, SingleSplitSource.SingleSplit] =
    new SingleSplitSource.Reader[String] {

      override def pollNext(output: ReaderOutput[String]): InputStatus = {
        if (running) {
          synchronized {
            buffer.reverse.foreach {
              case EmitWatermark(timestamp) =>
                output.emitWatermark(new Watermark(timestamp))
              case a: EmitData =>
                output.collect(a.value, a.timestamp)
            }
            buffer = List()
          }
          InputStatus.NOTHING_AVAILABLE
        } else {
          InputStatus.END_OF_INPUT
        }
      }

      override def start(): Unit = {
        running = true
      }

      override def close(): Unit = {
        buffer = Nil
        running = false
      }

    }

  def add(command: Command): Unit = {
    synchronized {
      buffer = command :: buffer
    }
  }

  sealed trait Command

  case class EmitWatermark(timestamp: Long) extends Command

  case class EmitData(timestamp: Long, value: String) extends Command

}
