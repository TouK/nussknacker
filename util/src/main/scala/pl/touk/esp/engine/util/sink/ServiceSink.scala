package pl.touk.esp.engine.util.sink

import org.apache.flink.api.common.functions.RichMapFunction
import pl.touk.esp.engine.api.Service
import pl.touk.esp.engine.api.process.{InputWithExectutionContext, Sink}
import pl.touk.esp.engine.util.sink.ServiceSink._

import scala.concurrent.Future

class ServiceSink(service: => Service) extends Sink {

  override def toFlinkFunction: RichMapFunction[InputWithExectutionContext, Future[Unit]] =
    new ServiceSinkFunction(service)

}

class ServiceSinkFunction(_service: => Service)
  extends RichMapFunction[InputWithExectutionContext, Future[Unit]] {

  lazy val service = _service

  override def map(value: InputWithExectutionContext): Future[Unit] = {
    implicit val ec = value.executionContext
    val params = Map(InputParamName -> value.input)
    service.invoke(params).map(_ => Unit)
  }
}

object ServiceSink {

  final val InputParamName = "input"

}