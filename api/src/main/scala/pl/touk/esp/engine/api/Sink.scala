package pl.touk.esp.engine.api

import org.apache.flink.streaming.api.functions.sink.SinkFunction

import scala.concurrent.ExecutionContext

trait Sink {

  def toFlinkSink: SinkFunction[InputWithExectutionContext]

}

case class InputWithExectutionContext(input: Any,
                                      executionContext: ExecutionContext)

trait SinkFactory {

  def create(processMetaData: MetaData, parameters: Map[String, String]): Sink

}

object SinkFactory {

  def noParam(sink: Sink): SinkFactory = new SinkFactory {
    override def create(processMetaData: MetaData, parameters: Map[String, String]): Sink = sink
  }

}