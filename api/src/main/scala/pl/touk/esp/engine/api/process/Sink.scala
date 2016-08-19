package pl.touk.esp.engine.api.process

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.esp.engine.api.MetaData

trait Sink {

  def toFlinkFunction: SinkFunction[Any]

}

trait SinkFactory {

  def create(processMetaData: MetaData, parameters: Map[String, String]): Sink

}

object SinkFactory {

  def noParam(sink: Sink): SinkFactory = new SinkFactory {
    override def create(processMetaData: MetaData, parameters: Map[String, String]): Sink = sink
  }

}