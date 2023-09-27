package pl.touk.nussknacker.ui.listener

import scala.concurrent.ExecutionContext

trait ProcessChangeListener {
  def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit
}

object ProcessChangeListener {
  def noop: ProcessChangeListener = new ProcessChangeListener {
    override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit = event match {
      case _ => ()
    }
  }
}