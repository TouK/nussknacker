package pl.touk.esp.engine.process

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.esp.engine.api.Lifecycle
import pl.touk.esp.engine.flink.api.RuntimeContextLifecycle

import scala.concurrent.ExecutionContext

case class WithLifecycle[T<:Lifecycle](values: Seq[T]) {

  def open(runtimeContext: RuntimeContext)(implicit ec: ExecutionContext) : Unit = {
    values.foreach {
      case s:RuntimeContextLifecycle => s.open(runtimeContext)
      case s => s.open()
    }
  }

  def close() : Unit = {
    values.foreach(_.close())
  }
}


