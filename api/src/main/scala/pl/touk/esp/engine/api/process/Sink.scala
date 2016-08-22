package pl.touk.esp.engine.api.process

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.esp.engine.api.MetaData

trait Sink {

  def toFlinkFunction: SinkFunction[Any]

}

trait SinkFactory {

}

object SinkFactory {

  def noParam(sink: Sink): SinkFactory =
    new NoParamSinkFactory(sink)

  class NoParamSinkFactory(sink: Sink) extends SinkFactory {
    def create(): Sink = sink
  }

}