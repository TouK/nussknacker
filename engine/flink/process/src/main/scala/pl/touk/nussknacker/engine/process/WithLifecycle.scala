package pl.touk.nussknacker.engine.process

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.Lifecycle
import pl.touk.nussknacker.engine.flink.api.RuntimeContextLifecycle

case class WithLifecycle[T<:Lifecycle](values: Seq[T]) {

  def open(runtimeContext: RuntimeContext) : Unit = {
    values.foreach {
      case s:RuntimeContextLifecycle =>
        s.open()
        s.open(runtimeContext)
      case s =>
        s.open()
    }
  }

  def close() : Unit = {
    values.foreach(_.close())
  }
}


