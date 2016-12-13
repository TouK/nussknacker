package pl.touk.esp.engine.process

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.esp.engine.api.Service
import pl.touk.esp.engine.flink.api.RuntimeContextLifecycle

import scala.concurrent.ExecutionContext

class ServicesLifecycle(services: Seq[Service]) {
  def open(runtimeContext: RuntimeContext)(implicit ec: ExecutionContext) = {
    services.foreach {
      case s:RuntimeContextLifecycle => s.open(runtimeContext)
      case s => s.open()
    }
  }

  def close() = {
    services.foreach(_.close())
  }
}


