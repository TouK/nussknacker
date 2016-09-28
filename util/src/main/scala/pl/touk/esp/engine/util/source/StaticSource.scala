package pl.touk.esp.engine.util.source

import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.watermark


object StaticSource extends SourceFunction[String] {

  @volatile var buffer = List[Timer]()

  @volatile var running = true

  override def cancel(): Unit = {
    buffer = List()
    running = false
  }

  override def run(ctx: SourceContext[String]) = {
    while (running) {
      synchronized {
        buffer.reverse.foreach {
          case Watermark(time) =>
            ctx.emitWatermark(new watermark.Watermark(time))
          case a: Data =>
            ctx.collectWithTimestamp(a.value, a.time)
        }
        buffer = List()
      }
      Thread.sleep(100)
    }
  }

  def add(timer: Timer) = {
    synchronized {
      buffer = timer :: buffer
    }
  }

  sealed trait Timer

  case class Watermark(time: Long) extends Timer

  case class Data(time: Long, value: String) extends Timer
}