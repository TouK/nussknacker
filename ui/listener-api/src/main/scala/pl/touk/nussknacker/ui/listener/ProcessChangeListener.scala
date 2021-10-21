package pl.touk.nussknacker.ui.listener

import scala.concurrent.ExecutionContext

trait ProcessChangeListener {
  def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: ListenerUser): Unit
}

object ProcessChangeListener {
  def noop: ProcessChangeListener = new ProcessChangeListener {
    override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: ListenerUser): Unit = event match {
      case _ => ()
    }
  }
}