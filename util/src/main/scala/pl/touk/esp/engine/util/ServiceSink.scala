package pl.touk.esp.engine.util

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.esp.engine.api.{InputWithExectutionContext, Service, Sink}
import ServiceSink._
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

class ServiceSink(service: => Service) extends Sink {

  override def toFlinkSink: SinkFunction[InputWithExectutionContext] = new ServiceSinkFunction(service)

}

class ServiceSinkFunction(_service: => Service) extends SinkFunction[InputWithExectutionContext] {
  private final val logger = LoggerFactory.getLogger(getClass)

  lazy val service = _service

  override def invoke(value: InputWithExectutionContext): Unit = {
    val params = Map(InputParamName -> value.input)
    implicit val executionContext = value.executionContext
    service.invoke(params).onFailure {
      case NonFatal(ex) =>
        logger.error("Service invocation failure", ex) // will failure recovery work?
    }
  }
}

object ServiceSink {

  final val InputParamName = "input"

}