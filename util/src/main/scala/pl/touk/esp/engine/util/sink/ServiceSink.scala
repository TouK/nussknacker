package pl.touk.esp.engine.util.sink

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.esp.engine.api.{InputWithExectutionContext, Service, Sink}
import pl.touk.esp.engine.util.sink.ServiceSink._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ServiceSink(service: => Service, invocationTimeout: Duration) extends Sink {

  override def toFlinkSink: SinkFunction[InputWithExectutionContext] =
    new ServiceSinkFunction(service, invocationTimeout)

}

class ServiceSinkFunction(_service: => Service, invocationTimeout: Duration)
  extends SinkFunction[InputWithExectutionContext] {

  lazy val service = _service

  override def invoke(value: InputWithExectutionContext): Unit = {
    val params = Map(InputParamName -> value.input)
    implicit val executionContext = value.executionContext
    Await.result(service.invoke(params), invocationTimeout)
  }
}

object ServiceSink {

  final val InputParamName = "input"

}